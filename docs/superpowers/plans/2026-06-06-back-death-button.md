# /back 死亡回归+聊天按钮 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 简化 /back：仅记录死亡、去掉冷却、死亡时发聊天可点击按钮、传送后删除记录。

**架构：** BackSettings 新增按钮消息配置 → BackService 新增 handleDeath/sendDeathButton、移除冷却/传送记录、传送后删数据 → BaseListener 修改死亡监听 + 移除传送监听 → base-command.yml 配置更新。

**技术栈：** Kotlin, Adventure Component (ClickEvent/HoverEvent), TextBridge, Paper 1.21.11

---

### 任务 1：BackSettings + base-command.yml 配置更新

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/base/BackSettings.kt`
- 修改：`project/core/src/main/resources/feature/base-command.yml`

- [ ] **步骤 1：修改 BackSettings.kt**

用 Read 读取当前内容，替换为：

```kotlin
package com.pixlehavencore.feature.base

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object BackSettings {

    @Config("feature/base-command.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
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

    var msgWarmupStarting: String = "&a将在 {time} 秒后传送... 请勿移动"
        private set

    var msgWarmupCancelled: String = "&c传送已取消！"
        private set

    var msgTeleported: String = "&a已传送到死亡位置。"
        private set

    var msgAlreadyWarmingUp: String = "&c传送预热中，请稍候。"
        private set

    var msgDeathButton: String = "&c你已死亡！ &a[点击此处返回死亡位置]"
        private set

    var msgDeathHover: String = "&a点击回到死亡点"
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("back.enabled", true)
        warmupSeconds = config.getInt("back.warmupSeconds", 3)
        cancelOnMove = config.getBoolean("back.cancelOnMove", true)
        cancelOnDamage = config.getBoolean("back.cancelOnDamage", true)
        unsafeTeleport = config.getBoolean("back.unsafeTeleport", false)
        msgModuleDisabled = config.getString("messages.moduleDisabled") ?: "&c基础模块已禁用。"
        msgNoLocation = config.getString("back.msgNoLocation") ?: "&c没有可返回的位置。"
        msgWarmupStarting = config.getString("back.msgWarmupStarting") ?: "&a将在 {time} 秒后传送... 请勿移动"
        msgWarmupCancelled = config.getString("back.msgWarmupCancelled") ?: "&c传送已取消！"
        msgTeleported = config.getString("back.msgTeleported") ?: "&a已传送到死亡位置。"
        msgAlreadyWarmingUp = config.getString("back.msgAlreadyWarmingUp") ?: "&c传送预热中，请稍候。"
        msgDeathButton = config.getString("back.msgDeathButton") ?: "&c你已死亡！ &a[点击此处返回死亡位置]"
        msgDeathHover = config.getString("back.msgDeathHover") ?: "&a点击回到死亡点"
    }
}
```

具体变更：移除 `cooldownSeconds`、`msgCooldown`，新增 `msgDeathButton`、`msgDeathHover`，`msgTeleported` 默认值改为"死亡位置"。

- [ ] **步骤 2：修改 base-command.yml 的 back 节**

用 Read 读取当前内容，将 back 节替换为：

```yaml
# /back 命令配置
back:
  # 是否启用 /back 命令
  enabled: true
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
  msgWarmupStarting: "&a将在 {time} 秒后传送... 请勿移动"
  msgWarmupCancelled: "&c传送已取消！"
  msgTeleported: "&a已传送到死亡位置。"
  msgAlreadyWarmingUp: "&c传送预热中，请稍候。"
  msgDeathButton: "&c你已死亡！ &a[点击此处返回死亡位置]"
  msgDeathHover: "&a点击回到死亡点"
```

具体变更：移除 `cooldownSeconds`、`msgCooldown`，新增 `msgDeathButton`、`msgDeathHover`。

- [ ] **步骤 3：验证编译**

```bash
./gradlew :project:core:compileKotlin
```
预期：编译失败（BackService 仍引用已删除字段，任务 2 修复）

- [ ] **步骤 4：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/base/BackSettings.kt project/core/src/main/resources/feature/base-command.yml
git commit -m "feat(back): 更新配置——移除冷却、新增死亡按钮消息"
```

---

### 任务 2：BackService 重构

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/base/BackService.kt`

- [ ] **步骤 1：重写 BackService.kt**

```kotlin
package com.pixlehavencore.feature.base

import com.pixlehavencore.bridge.TextBridge
import com.pixlehavencore.util.TextUtils
import com.pixlehavencore.util.cancelTaskSafely
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import taboolib.common.platform.function.submitAsync
import taboolib.platform.util.submit as submitOnEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class BackData(
    val location: Location,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)

class WarmupState(
    val targetLocation: Location,
    val startLocation: Location,
    var remaining: Int,
    var taskRef: Any,
    @Volatile var cancelled: Boolean = false
)

object BackService {

    private val warmups = ConcurrentHashMap<UUID, WarmupState>()

    fun init() {
        BackSettings.init()
        BackStorage.init()
        stop()
    }

    fun reload() {
        stop()
        BackSettings.reload()
        BackStorage.reload()
    }

    fun stop() {
        for ((_, state) in warmups) {
            state.taskRef.cancelTaskSafely()
        }
        warmups.clear()
    }

    fun isEnabled(): Boolean = BackSettings.enabled

    fun handleDeath(player: Player) {
        if (!BackSettings.enabled) return
        record(player.uniqueId, player.location, "death")
        sendDeathButton(player)
    }

    private fun record(player: UUID, location: Location, reason: String) {
        val data = BackData(location = location.clone(), reason = reason)
        BackStorage.set(player, data)
    }

    private fun sendDeathButton(player: Player) {
        val button = TextUtils.parse(BackSettings.msgDeathButton)
            .clickEvent(ClickEvent.runCommand("/back"))
            .hoverEvent(HoverEvent.showText(TextUtils.parse(BackSettings.msgDeathHover)))
        TextBridge.sendMessage(player, button)
    }

    fun teleportBack(player: Player): Boolean {
        if (!BackSettings.enabled) {
            player.sendMessage(TextUtils.parse(BackSettings.msgModuleDisabled))
            return false
        }

        val uuid = player.uniqueId

        if (warmups.containsKey(uuid)) {
            player.sendMessage(TextUtils.parse(BackSettings.msgAlreadyWarmingUp))
            return false
        }

        val data = getBackData(uuid)
        if (data == null) {
            player.sendMessage(TextUtils.parse(BackSettings.msgNoLocation))
            return false
        }

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
                player.submitOnEntity {
                    doTeleport(player, targetLoc, uuid)
                }
            } else {
                startWarmup(player, targetLoc, uuid)
            }
        }

        return true
    }

    fun cancelWarmup(uuid: UUID) {
        warmups[uuid]?.let { it.cancelled = true }
    }

    private fun getBackData(player: UUID): BackData? {
        var data = BackStorage.get(player)
        if (data != null) return data
        data = BackStorage.loadFromDatabase(player)
        if (data != null) {
            BackStorage.set(player, data)
        }
        return data
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
            if (!player.isOnline) {
                warmups.remove(uuid)
                warmupState.taskRef.cancelTaskSafely()
                return@submitOnEntity
            }

            if (warmupState.cancelled) {
                warmups.remove(uuid)
                warmupState.taskRef.cancelTaskSafely()
                player.sendMessage(TextUtils.parse(BackSettings.msgWarmupCancelled))
                return@submitOnEntity
            }

            if (BackSettings.cancelOnMove && warmupState.remaining < BackSettings.warmupSeconds) {
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

        player.teleport(safeLoc)
        BackStorage.remove(uuid)
        player.sendMessage(TextUtils.parse(BackSettings.msgTeleported))
    }

    private fun findSafeLocation(location: Location): Location? {
        val world = location.world ?: return null
        val block = world.getBlockAt(location.blockX, location.blockY, location.blockZ)

        if (block.isPassable && world.getBlockAt(location.blockX, location.blockY + 1, location.blockZ).isPassable) {
            return location.clone()
        }

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

具体变更：
- 移除 `cooldowns` ConcurrentHashMap 和冷却检查
- 移除 `getBackData` 的 public 可见性（改为 private）
- 新增 `handleDeath(player)` — 记录 + 发按钮
- 新增 `sendDeathButton` — 用 ClickEvent.runCommand("/back") + HoverEvent + TextBridge.sendMessage
- `record` 改为 private
- `doTeleport` 中：移除冷却写入，改用 `BackStorage.remove(uuid)` 删除数据
- 移除 `submitAsync` 的 import（仍在使用，保留）
- 新增 `net.kyori.adventure.text.event.ClickEvent` 和 `HoverEvent` import

- [ ] **步骤 2：验证编译**

```bash
./gradlew :project:core:compileKotlin
```
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/base/BackService.kt
git commit -m "feat(back): 重构——仅记录死亡、去冷却、死亡发按钮、传后删数据"
```

---

### 任务 3：BaseListener 事件监听更新

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/base/BaseListener.kt`

- [ ] **步骤 1：修改 BaseListener.kt**

用 Read 读取当前内容。做以下变更：

**删除 `onPlayerTeleport` 方法（第 74-78 行）：**
```kotlin
    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        if (!BaseCommandSettings.enabled) return
        BackService.record(event.player.uniqueId, event.from, "teleport")
    }
```

**修改 `onPlayerDeath` 方法（第 80-84 行）：**
```kotlin
    @SubscribeEvent(priority = EventPriority.MONITOR)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        BackService.handleDeath(event.player)
    }
```

去掉 `if (!BaseCommandSettings.enabled) return` 和旧的 record 调用，改为调用 `BackService.handleDeath(event.player)`。

**删除不再需要的 import（移除后确认无其他引用）：**
```kotlin
import org.bukkit.event.player.PlayerTeleportEvent
```

- [ ] **步骤 2：验证编译**

```bash
./gradlew :project:core:compileKotlin
```
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/base/BaseListener.kt
git commit -m "feat(back): 移除传送监听、死亡监听改用 handleDeath"
```

---

### 任务 4：构建验证

**文件：** 无

- [ ] **步骤 1：完整构建**

```bash
./gradlew build
```
预期：BUILD SUCCESSFUL

- [ ] **步骤 2：逻辑审查**

审查路径：
- 玩家死亡 → 聊天显示可点击按钮 → 点击执行 `/back`
- 死亡后传送（无预热）→ 传送 → 删除记录 → 再次 `/back` 提示"无位置"
- 死亡后传送（有预热）→ 倒计时 → 传送 → 删除记录
- 点击按钮后预热中移动/受伤 → 取消 → 仍可再次 `/back`（数据未删）
- 旧 `cooldownSeconds` 配置项 → 不再读取（YAML 保留旧 key 无害）

---

## 文件变更清单

| 文件 | 操作 |
|---|---|
| `.../feature/base/BackSettings.kt` | 修改 |
| `.../feature/base/BackService.kt` | 修改 |
| `.../feature/base/BaseListener.kt` | 修改 |
| `.../resources/feature/base-command.yml` | 修改 |
