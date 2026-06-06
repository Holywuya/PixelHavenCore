# /back 命令 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在 `feature/base/` 模块实现 `/back` 命令，允许玩家传送到上一次死亡或传送来源位置。

**架构：** BackStorage（MultipleHandler 持久化 + ConcurrentHashMap 缓存）→ BackService（记录、冷却、预热、安全传送）→ BackCommand（TabooLib 命令 DSL 入口），由 BaseListener 监听 PlayerDeathEvent + PlayerTeleportEvent 触发记录。

**技术栈：** Kotlin, TabooLib 6.3.0 (CommandHelper, MultipleHandler), Paper 1.21.11, Folia 线程安全

---

### 任务 1：BackStorage 持久化层

**文件：**
- 创建：`project/core/src/main/kotlin/com/pixlehavencore/feature/base/BackStorage.kt`

- [ ] **步骤 1：创建 BackStorage.kt**

```kotlin
package com.pixlehavencore.feature.base

import com.pixlehavencore.util.DatabaseUtils
import org.bukkit.Bukkit
import org.bukkit.Location
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import taboolib.expansion.MultipleHandler
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object BackStorage {

    private const val TABLE_NAME = "back_location"
    private const val KEY_LOCATION = "location"

    @Volatile
    private var handler: MultipleHandler? = null
    private val shuttingDown = AtomicBoolean(false)

    private val cache = ConcurrentHashMap<UUID, BackData>()

    fun init() {
        shuttingDown.set(false)
        reload()
    }

    fun reload() {
        if (shuttingDown.get()) return
        submitAsync {
            close()
            runCatching {
                handler = DatabaseUtils.newPlayerDataHandler(TABLE_NAME, syncTick = 200L)
            }.onFailure { ex ->
                warning("[Back] 初始化 PlayerDatabase 失败: ${ex.message}")
                warning("[Back] 位置数据将无法持久化，请检查数据库配置！")
                close()
            }
        }
    }

    fun close() {
        shuttingDown.set(true)
        flushCache()
        DatabaseUtils.closeMultipleHandler(handler)
        handler = null
        cache.clear()
    }

    fun get(player: UUID): BackData? {
        return cache[player]
    }

    fun set(player: UUID, data: BackData) {
        cache[player] = data
        submitAsync {
            if (shuttingDown.get()) return@submitAsync
            saveToDatabase(player, data)
        }
    }

    fun remove(player: UUID) {
        cache.remove(player)
        submitAsync {
            if (shuttingDown.get()) return@submitAsync
            val currentHandler = handler ?: return@submitAsync
            runCatching {
                currentHandler.database[player.toString(), KEY_LOCATION] = null
            }.onFailure { ex ->
                warning("[Back] 删除玩家数据失败($player): ${ex.message}")
            }
        }
    }

    fun loadFromDatabase(player: UUID): BackData? {
        val currentHandler = handler ?: return null
        return runCatching {
            val raw = currentHandler.database[player.toString(), KEY_LOCATION] ?: return null
            deserialize(raw)
        }.getOrElse { ex ->
            warning("[Back] 读取玩家数据失败($player): ${ex.message}")
            null
        }
    }

    private fun saveToDatabase(player: UUID, data: BackData) {
        val currentHandler = handler ?: return
        runCatching {
            currentHandler.database[player.toString(), KEY_LOCATION] = serialize(data.location)
        }.onFailure { ex ->
            warning("[Back] 保存玩家数据失败($player): ${ex.message}")
        }
    }

    private fun flushCache() {
        val currentHandler = handler ?: return
        for ((player, data) in cache) {
            runCatching {
                currentHandler.database[player.toString(), KEY_LOCATION] = serialize(data.location)
            }.onFailure { ex ->
                warning("[Back] flushCache 保存失败($player): ${ex.message}")
            }
        }
    }

    private fun serialize(location: Location): String {
        val worldName = location.world?.name ?: "world"
        return "$worldName:${location.x}:${location.y}:${location.z}:${location.yaw}:${location.pitch}"
    }

    private fun deserialize(raw: String): BackData? {
        val parts = raw.split(":")
        if (parts.size < 4) return null
        val worldName = parts[0]
        val x = parts[1].toDoubleOrNull() ?: return null
        val y = parts[2].toDoubleOrNull() ?: return null
        val z = parts[3].toDoubleOrNull() ?: return null
        val yaw = parts.getOrElse(4) { "0.0" }.toFloatOrNull() ?: 0f
        val pitch = parts.getOrElse(5) { "0.0" }.toFloatOrNull() ?: 0f
        val world = Bukkit.getWorld(worldName) ?: return null
        return BackData(
            location = Location(world, x, y, z, yaw, pitch),
            reason = "persisted",
            timestamp = System.currentTimeMillis()
        )
    }
}
```

- [ ] **步骤 2：验证编译**

```bash
./gradlew :project:core:compileKotlin
```
预期：仅 BackData 未定义的编译错误（将在任务 2 解决）

- [ ] **步骤 3：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/base/BackStorage.kt
git commit -m "feat(back): 添加 BackStorage 持久化层"
```

---

### 任务 2：BackService 核心逻辑（含 BackData 数据类）

**文件：**
- 创建：`project/core/src/main/kotlin/com/pixlehavencore/feature/base/BackService.kt`

`BackData` 定义在 BackService.kt 文件顶部（与 FlightPlayerData 在 FlightService.kt 的模式一致）。

- [ ] **步骤 1：创建 BackService.kt**

```kotlin
package com.pixlehavencore.feature.base

import com.pixlehavencore.bridge.TextBridge
import com.pixlehavencore.util.TextUtils
import com.pixlehavencore.util.cancelTaskSafely
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.platform.util.submit as submitOnEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class BackData(
    val location: Location,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class WarmupState(
    val targetLocation: Location,
    val startLocation: Location,
    var remaining: Int,
    val taskRef: Any,
    var cancelled: Boolean = false
)

object BackService {

    private val cooldowns = ConcurrentHashMap<UUID, Long>()
    private val warmups = ConcurrentHashMap<UUID, WarmupState>()

    fun init() {
        BackSettings.init()
        stop()
    }

    fun reload() {
        stop()
        BackSettings.reload()
    }

    fun stop() {
        for ((_, state) in warmups) {
            state.taskRef.cancelTaskSafely()
        }
        warmups.clear()
    }

    fun isEnabled(): Boolean = BackSettings.enabled

    fun getBackData(player: UUID): BackData? {
        var data = BackStorage.get(player)
        if (data != null) return data
        data = BackStorage.loadFromDatabase(player)
        if (data != null) {
            BackStorage.set(player, data)
        }
        return data
    }

    fun record(player: UUID, location: Location, reason: String) {
        if (!BackSettings.enabled) return
        val data = BackData(location = location.clone(), reason = reason)
        BackStorage.set(player, data)
    }

    fun teleportBack(player: Player): Boolean {
        if (!BackSettings.enabled) {
            player.sendMessage(TextUtils.parse(BackSettings.msgModuleDisabled))
            return false
        }

        val uuid = player.uniqueId

        // 检查是否已有预热进行中
        if (warmups.containsKey(uuid)) {
            player.sendMessage(TextUtils.parse(BackSettings.msgAlreadyWarmingUp))
            return false
        }

        // 冷却检查
        val lastUse = cooldowns[uuid]
        if (lastUse != null && BackSettings.cooldownSeconds > 0) {
            val elapsed = (System.currentTimeMillis() - lastUse) / 1000
            if (elapsed < BackSettings.cooldownSeconds) {
                val remaining = BackSettings.cooldownSeconds - elapsed
                player.sendMessage(
                    TextUtils.parse(BackSettings.msgCooldown.replace("{time}", remaining.toString()))
                )
                return false
            }
        }

        // 获取记录位置
        val data = getBackData(uuid)
        if (data == null) {
            player.sendMessage(TextUtils.parse(BackSettings.msgNoLocation))
            return false
        }

        // 异步解析目标世界
        val targetWorldName = data.location.world?.name ?: run {
            player.sendMessage(TextUtils.parse("&c目标世界不可用。"))
            return false
        }

        submitAsync {
            val targetWorld = Bukkit.getWorld(targetWorldName)
            if (targetWorld == null) {
                BackStorage.remove(uuid)
                player.sendMessage(TextUtils.parse("&c目标世界不可用。"))
                return@submitAsync
            }
            val targetLoc = Location(targetWorld, data.location.x, data.location.y, data.location.z, data.location.yaw, data.location.pitch)

            if (BackSettings.warmupSeconds <= 0) {
                doTeleport(player, targetLoc, uuid)
            } else {
                startWarmup(player, targetLoc, uuid)
            }
        }

        return true
    }

    private fun startWarmup(player: Player, targetLoc: Location, uuid: UUID) {
        val startLoc = player.location.clone()
        player.sendMessage(
            TextUtils.parse(BackSettings.msgWarmupStarting.replace("{time}", BackSettings.warmupSeconds.toString()))
        )

        val warmupState = WarmupState(
            targetLocation = targetLoc,
            startLocation = startLoc,
            remaining = BackSettings.warmupSeconds,
            taskRef = Any()
        )

        val task = player.submitOnEntity(delay = 0L, period = 20L) {
            // 玩家离线
            if (!player.isOnline) {
                warmups.remove(uuid)
                warmupState.taskRef.cancelTaskSafely()
                return@submitOnEntity
            }

            // 受伤取消
            if (BackSettings.cancelOnDamage && warmupState.cancelled) {
                warmups.remove(uuid)
                warmupState.taskRef.cancelTaskSafely()
                player.sendMessage(TextUtils.parse(BackSettings.msgWarmupCancelled))
                return@submitOnEntity
            }

            // 移动取消
            if (BackSettings.cancelOnMove && warmupState.remaining < BackSettings.warmupSeconds) {
                // 第一秒不检测（给玩家缓冲）
                val currentLoc = player.location
                if (currentLoc.blockX != warmupState.startLocation.blockX ||
                    currentLoc.blockY != warmupState.startLocation.blockY ||
                    currentLoc.blockZ != warmupState.startLocation.blockZ
                ) {
                    warmups.remove(uuid)
                    warmupState.taskRef.cancelTaskSafely()
                    player.sendMessage(TextUtils.parse(BackSettings.msgWarmupCancelled))
                    return@submitOnEntity
                }
            }

            if (warmupState.remaining <= 0) {
                warmups.remove(uuid)
                warmupState.taskRef.cancelTaskSafely()
                doTeleport(player, warmupState.targetLocation, uuid)
                return@submitOnEntity
            }

            TextBridge.sendActionBar(
                player,
                TextUtils.parse(BackSettings.msgWarmupStarting.replace("{time}", warmupState.remaining.toString()))
            )
            warmupState.remaining--
        }

        warmupState.taskRef = task
        warmups[uuid] = warmupState
    }

    fun cancelWarmup(uuid: UUID) {
        warmups[uuid]?.let { it.cancelled = true }
    }

    private fun doTeleport(player: Player, targetLoc: Location, uuid: UUID) {
        val safeLoc = if (BackSettings.unsafeTeleport) {
            targetLoc
        } else {
            findSafeLocation(targetLoc)
        }

        if (safeLoc == null) {
            player.sendMessage(TextUtils.parse("&c未找到安全传送位置。"))
            return
        }

        player.submitOnEntity {
            player.teleport(safeLoc)
            cooldowns[uuid] = System.currentTimeMillis()
            player.sendMessage(TextUtils.parse(BackSettings.msgTeleported))
        }
    }

    private fun findSafeLocation(location: Location): Location? {
        val world = location.world ?: return null
        val block = world.getBlockAt(location.blockX, location.blockY, location.blockZ)

        // 如果当前位置和上方一格都不阻塞，则是安全位置
        if (block.isPassable && world.getBlockAt(location.blockX, location.blockY + 1, location.blockZ).isPassable) {
            return location.clone()
        }

        // 向上搜索安全位置
        for (dy in 1..8) {
            val y = location.blockY + dy
            if (y > world.maxHeight) break
            val b = world.getBlockAt(location.blockX, y, location.blockZ)
            val above = world.getBlockAt(location.blockX, y + 1, location.blockZ)
            if (b.isPassable && above.isPassable) {
                val safeLoc = location.clone()
                safeLoc.y = y + 0.0
                return safeLoc
            }
        }

        // 向下搜索
        for (dy in 1..8) {
            val y = location.blockY - dy
            if (y < world.minHeight) break
            val b = world.getBlockAt(location.blockX, y, location.blockZ)
            val above = world.getBlockAt(location.blockX, y + 1, location.blockZ)
            if (b.isPassable && above.isPassable) {
                val safeLoc = location.clone()
                safeLoc.y = y + 0.0
                return safeLoc
            }
        }

        return null
    }
}
```

- [ ] **步骤 2：验证编译**

```bash
./gradlew :project:core:compileKotlin
```
预期：仅 BackSettings 未定义错误（将在任务 3 解决）

- [ ] **步骤 3：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/base/BackService.kt
git commit -m "feat(back): 添加 BackService 核心逻辑（记录、冷却、预热、安全传送）"
```

---

### 任务 3：BackSettings 配置类 + base-command.yml

**文件：**
- 创建：`project/core/src/main/kotlin/com/pixlehavencore/feature/base/BackSettings.kt`
- 修改：`project/core/src/main/resources/feature/base-command.yml`

- [ ] **步骤 1：创建 BackSettings.kt**

```kotlin
package com.pixlehavencore.feature.base

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object BackSettings {

    @Config("feature/base-command.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    var cooldownSeconds: Int = 30
        private set

    var warmupSeconds: Int = 3
        private set

    var cancelOnMove: Boolean = true
        private set

    var cancelOnDamage: Boolean = true
        private set

    var unsafeTeleport: Boolean = false
        private set

    var msgModuleDisabled: String = "&c基础模块已禁用。"
        private set

    var msgNoLocation: String = "&c没有可返回的位置。"
        private set

    var msgCooldown: String = "&c请等待 {time} 秒后再使用。"
        private set

    var msgWarmupStarting: String = "&a将在 {time} 秒后传送... 请勿移动"
        private set

    var msgWarmupCancelled: String = "&c传送已取消！"
        private set

    var msgTeleported: String = "&a已传送到上一个位置。"
        private set

    var msgAlreadyWarmingUp: String = "&c传送预热中，请稍候。"
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        cooldownSeconds = config.getInt("back.cooldownSeconds", 30)
        warmupSeconds = config.getInt("back.warmupSeconds", 3)
        cancelOnMove = config.getBoolean("back.cancelOnMove", true)
        cancelOnDamage = config.getBoolean("back.cancelOnDamage", true)
        unsafeTeleport = config.getBoolean("back.unsafeTeleport", false)
        msgModuleDisabled = config.getString("messages.moduleDisabled") ?: "&c基础模块已禁用。"
        msgNoLocation = config.getString("back.msgNoLocation") ?: "&c没有可返回的位置。"
        msgCooldown = config.getString("back.msgCooldown") ?: "&c请等待 {time} 秒后再使用。"
        msgWarmupStarting = config.getString("back.msgWarmupStarting") ?: "&a将在 {time} 秒后传送... 请勿移动"
        msgWarmupCancelled = config.getString("back.msgWarmupCancelled") ?: "&c传送已取消！"
        msgTeleported = config.getString("back.msgTeleported") ?: "&a已传送到上一个位置。"
        msgAlreadyWarmingUp = config.getString("back.msgAlreadyWarmingUp") ?: "&c传送预热中，请稍候。"
    }
}
```

- [ ] **步骤 2：修改 base-command.yml，在 `messages:` 节下方增加 back 节**

找到 `messages:` 节的 `suicide:` 行，并在 `portalProtection:` 之前插入：

```yaml
  # 模块禁用时提示消息
  moduleDisabled: "&c基础模块已禁用。"

# /back 命令配置
back:
  # 是否启用 /back 命令
  enabled: true
  # 冷却时间（秒），0=无冷却
  cooldownSeconds: 30
  # 传送预热等待时间（秒），0=无预热
  warmupSeconds: 3
  # 预热期间移动是否取消传送
  cancelOnMove: true
  # 预热期间受伤是否取消传送
  cancelOnDamage: true
  # true=跳过安全位置检测，直接传送到原始坐标
  unsafeTeleport: false
  # 消息配置
  msgNoLocation: "&c没有可返回的位置。"
  msgCooldown: "&c请等待 {time} 秒后再使用。"
  msgWarmupStarting: "&a将在 {time} 秒后传送... 请勿移动"
  msgWarmupCancelled: "&c传送已取消！"
  msgTeleported: "&a已传送到上一个位置。"
  msgAlreadyWarmingUp: "&c传送预热中，请稍候。"
```

完整文件如下：

```yaml
version: 9

# =============================================================================
# 基础命令模块配置
# =============================================================================
# 功能说明：
#   - 提供基础游戏命令和保护功能
#   - 包含自杀命令、/back、苦力怕防爆、传送门保护等
#   - 支持自定义消息和权限控制

# 总开关（true=启用，false=禁用）
enabled: true

# 消息配置
messages:
  # 自杀命令执行后的提示消息
  suicide: "&c你已自杀。"
  # 模块禁用时提示消息
  moduleDisabled: "&c基础模块已禁用。"

# /back 命令配置
back:
  # 是否启用 /back 命令
  enabled: true
  # 冷却时间（秒），0=无冷却
  cooldownSeconds: 30
  # 传送预热等待时间（秒），0=无预热
  warmupSeconds: 3
  # 预热期间移动是否取消传送
  cancelOnMove: true
  # 预热期间受伤是否取消传送
  cancelOnDamage: true
  # true=跳过安全位置检测，直接传送到原始坐标
  unsafeTeleport: false
  # 消息配置
  msgNoLocation: "&c没有可返回的位置。"
  msgCooldown: "&c请等待 {time} 秒后再使用。"
  msgWarmupStarting: "&a将在 {time} 秒后传送... 请勿移动"
  msgWarmupCancelled: "&c传送已取消！"
  msgTeleported: "&a已传送到上一个位置。"
  msgAlreadyWarmingUp: "&c传送预热中，请稍候。"

# 苦力怕防爆配置
creeperProtect:
  # 是否启用苦力怕防爆（true=启用，false=禁用）
  enabled: false
  # 防爆模式
  # true=完全取消爆炸（无伤害无破坏）
  # false=仅保护方块不受破坏（爆炸伤害保留）
  cancelDamage: false

# 传送门保护配置
portalProtection:
  # 是否禁止指定生物进入传送门（true=启用，false=禁用）
  enabled: true
  # 禁止进入传送门的生物类型列表
  blockedEntities:
    - "FROG"
  # 若指定生物出现在地狱或末地，是否直接清除（true=清除，false=不清除）
  clearInNetherEnd: true
  # 在地狱/末地自动清除的生物类型列表
  clearEntitiesInNetherEnd:
    - "FROG"
```

- [ ] **步骤 3：验证编译**

```bash
./gradlew :project:core:compileKotlin
```
预期：BackSettings 已定义，BackStorage 和 BackService 编译通过。

- [ ] **步骤 4：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/base/BackSettings.kt project/core/src/main/resources/feature/base-command.yml
git commit -m "feat(back): 添加 BackSettings 配置类和 YAML 配置"
```

---

### 任务 4：BaseListener 新增事件监听

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/base/BaseListener.kt`

- [ ] **步骤 1：在 BaseListener.kt 中添加三个事件监听方法**

在 `object BaseListener` 类体内末尾添加：

```kotlin
    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        if (!BaseCommandSettings.enabled) return
        val player = event.player
        BackService.record(player.uniqueId, event.from, "teleport")
    }

    @SubscribeEvent(priority = EventPriority.MONITOR)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        if (!BaseCommandSettings.enabled) return
        BackService.record(event.player.uniqueId, event.player.location, "death")
    }

    @SubscribeEvent
    fun onPlayerDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        BackService.cancelWarmup(player.uniqueId)
    }
```

在文件头部添加缺失的 import：

```kotlin
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerTeleportEvent
import taboolib.common.platform.event.EventPriority
```

- [ ] **步骤 2：验证编译**

```bash
./gradlew :project:core:compileKotlin
```
预期：编译通过。

- [ ] **步骤 3：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/base/BaseListener.kt
git commit -m "feat(back): BaseListener 新增传送/死亡/受伤事件监听"
```

---

### 任务 5：BackCommand 命令定义

**文件：**
- 创建：`project/core/src/main/kotlin/com/pixlehavencore/feature/base/BackCommand.kt`

- [ ] **步骤 1：创建 BackCommand.kt**

```kotlin
package com.pixlehavencore.feature.base

import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePlayer
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand

@CommandHeader(name = "back", permissionDefault = PermissionDefault.TRUE)
object BackCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!BaseCommandSettings.enabled) {
                sender.msg("&c基础模块已禁用。")
                return@execute
            }
            val player = sender.requirePlayer() ?: return@execute
            val bukkitPlayer = player.cast<org.bukkit.entity.Player>() ?: run {
                sender.msg("&c只有玩家可以使用此命令。")
                return@execute
            }
            BackService.teleportBack(bukkitPlayer)
        }
    }
}
```

- [ ] **步骤 2：验证编译**

```bash
./gradlew :project:core:compileKotlin
```
预期：所有新文件编译通过。

- [ ] **步骤 3：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/base/BackCommand.kt
git commit -m "feat(back): 添加 /back 命令定义"
```

---

### 任务 6：PixleHavenCore 生命周期注册

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/PixleHavenCore.kt`

- [ ] **步骤 1：在 onEnable() 中初始化 BackStorage 和 BackService**

在 `PixleHavenCore.kt` 的 `onEnable()` 方法中，紧接 `BaseCommandSettings.init()` 之后添加：

```kotlin
        BackStorage.init()
        BackService.init()
```

在文件头部添加 import：

```kotlin
import com.pixlehavencore.feature.base.BackService
import com.pixlehavencore.feature.base.BackStorage
```

- [ ] **步骤 2：在 onDisable() 中关闭 BackService 和 BackStorage**

在 `onDisable()` 方法末尾（`VanishService.stop()` 之后，`ItemUtils.clearHeadCache()` 之前）添加：

```kotlin
        BackService.stop()
        BackStorage.close()
```

- [ ] **步骤 3：在 logModulesStatus() 中更新 Base Module 状态行**

将现有行：
```kotlin
"Base Module" to BaseCommandSettings.enabled,
```
替换为自动包含 back 的判定。无需修改 —— BaseCommandSettings.enabled 已控制整个 base 模块的开关，back 功能绑定在此总开关下。

- [ ] **步骤 4：验证编译**

```bash
./gradlew :project:core:compileKotlin
```
预期：整个项目编译通过。

- [ ] **步骤 5：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/PixleHavenCore.kt
git commit -m "feat(back): 在插件生命周期中注册 BackStorage/BackService"
```

---

### 任务 7：构建验证

**文件：** 无新建/修改

- [ ] **步骤 1：完整构建**

```bash
./gradlew build
```
预期：BUILD SUCCESSFUL

- [ ] **步骤 2：功能检查**

审查以下逻辑路径：
- `/back` 无记录 → `msgNoLocation`
- `/back` 冷却中 → `msgCooldown` 含剩余秒数
- `/back` 有记录无预热 → 安全检测 → 传送 → `msgTeleported`
- `/back` 有记录有预热 → 倒计时 actionbar → 传送
- 预热中移动 → `msgWarmupCancelled`
- 预热中受伤 → `msgWarmupCancelled`
- 死亡后 `/back` → 回到死亡点
- 传送后 `/back` → 回到传送来源
- `enabled: false` → `/back` 提示模块禁用
- `warmupSeconds: 0` → 跳过预热，直接传送
- `cooldownSeconds: 0` → 跳过冷却
- `unsafeTeleport: true` → 跳过安全检测

- [ ] **步骤 3：如果构建失败，根据错误修复后重新提交；通过则完成**

无需额外 commit（构建验证不产生代码变更）。

---

## 文件变更清单

| 文件 | 操作 |
|---|---|
| `.../feature/base/BackStorage.kt` | 创建 |
| `.../feature/base/BackService.kt` | 创建 |
| `.../feature/base/BackSettings.kt` | 创建 |
| `.../feature/base/BackCommand.kt` | 创建 |
| `.../feature/base/BaseListener.kt` | 修改 |
| `.../resources/feature/base-command.yml` | 修改 |
| `.../PixleHavenCore.kt` | 修改 |
