# 统一玩家状态库 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 新建 `feature/playerstate/` 模块，用一张 `player_meta` 表统一存储首登时间、登录次数、死亡/传送位置等玩家基础状态，通过 PlayerStateService 对外暴露 API。

**架构：** PlayerStateStorage（MultipleHandler + ConcurrentHashMap 缓存，参照 BackStorage 模式）→ PlayerStateService（对外只读/写 API）→ PlayerStateListener（Join/Quit/Death/Teleport 事件自动写入），现阶段与旧表共存不迁移。

**技术栈：** Kotlin, TabooLib 6.3.0 (MultipleHandler), Paper 1.21.11, Folia 线程安全

---

### 任务 1：PlayerStateSettings + player-state.yml

**文件：**
- 创建：`project/core/src/main/kotlin/com/pixlehavencore/feature/playerstate/PlayerStateSettings.kt`
- 创建：`project/core/src/main/resources/feature/player-state.yml`

- [ ] **步骤 1：创建 PlayerStateSettings.kt**

```kotlin
package com.pixlehavencore.feature.playerstate

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object PlayerStateSettings {

    @Config("feature/player-state.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
    }
}
```

- [ ] **步骤 2：创建 player-state.yml**

```yaml
version: 1

# =============================================================================
# 统一玩家状态库配置
# =============================================================================
# 功能说明：
#   - 统一存储玩家首次登录、登录次数、离线时间等基础信息
#   - 存储死亡位置和传送来源位置
#   - 供 BackService、FirstJoinService 等模块通过 PlayerStateService 查询

# 总开关（true=启用，false=禁用）
enabled: true
```

- [ ] **步骤 3：验证编译**

```bash
./gradlew :project:core:compileKotlin
```
预期：BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/playerstate/PlayerStateSettings.kt project/core/src/main/resources/feature/player-state.yml
git commit -m "feat(playerstate): 添加 PlayerStateSettings 和 YAML 配置"
```

---

### 任务 2：PlayerStateStorage 持久化层

**文件：**
- 创建：`project/core/src/main/kotlin/com/pixlehavencore/feature/playerstate/PlayerStateStorage.kt`

- [ ] **步骤 1：创建 PlayerStateStorage.kt**

```kotlin
package com.pixlehavencore.feature.playerstate

import com.pixlehavencore.util.DatabaseUtils
import org.bukkit.Bukkit
import org.bukkit.Location
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import taboolib.expansion.MultipleHandler
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

data class PlayerStateData(
    val uuid: UUID,
    var playerName: String = "",
    var firstJoinTime: Long = 0L,
    var lastJoinTime: Long = 0L,
    var lastQuitTime: Long = 0L,
    var joinCount: Int = 0,
    var lastDeathLocation: String = "",
    var lastTeleportLocation: String = ""
)

object PlayerStateStorage {

    private const val TABLE_NAME = "player_meta"
    private const val KEY_PLAYER_NAME = "player_name"
    private const val KEY_FIRST_JOIN = "first_join_time"
    private const val KEY_LAST_JOIN = "last_join_time"
    private const val KEY_LAST_QUIT = "last_quit_time"
    private const val KEY_JOIN_COUNT = "join_count"
    private const val KEY_DEATH_LOC = "last_death_location"
    private const val KEY_TELEPORT_LOC = "last_teleport_location"

    @Volatile
    private var handler: MultipleHandler? = null
    private val shuttingDown = AtomicBoolean(false)

    private val cache = ConcurrentHashMap<UUID, PlayerStateData>()

    @Volatile
    var ready: Boolean = false
        private set

    fun init() {
        shuttingDown.set(false)
        reload()
    }

    fun reload() {
        if (shuttingDown.get()) return
        ready = false
        submitAsync {
            close()
            runCatching {
                handler = DatabaseUtils.newPlayerDataHandler(TABLE_NAME, syncTick = 200L)
            }.onSuccess {
                shuttingDown.set(false)
                ready = true
            }.onFailure { ex ->
                warning("[PlayerState] 初始化 PlayerDatabase 失败: ${ex.message}")
                warning("[PlayerState] 状态数据将无法持久化，请检查数据库配置！")
                close()
                ready = true
            }
        }
    }

    fun close() {
        ready = false
        shuttingDown.set(true)
        flushCache()
        DatabaseUtils.closeMultipleHandler(handler)
        handler = null
        cache.clear()
    }

    fun getOrCreate(uuid: UUID): PlayerStateData {
        return cache.computeIfAbsent(uuid) { PlayerStateData(uuid = uuid) }
    }

    fun get(uuid: UUID): PlayerStateData? = cache[uuid]

    fun loadFromDatabase(uuid: UUID, playerName: String): PlayerStateData? {
        val currentHandler = handler ?: return null
        return runCatching {
            val user = uuid.toString()
            val existingName = (currentHandler.database[user, KEY_PLAYER_NAME] as? String)?.takeIf { it.isNotBlank() }
            val firstJoin = (currentHandler.database[user, KEY_FIRST_JOIN] as? String)?.toLongOrNull() ?: 0L
            val lastJoin = (currentHandler.database[user, KEY_LAST_JOIN] as? String)?.toLongOrNull() ?: 0L
            val lastQuit = (currentHandler.database[user, KEY_LAST_QUIT] as? String)?.toLongOrNull() ?: 0L
            val joinCount = (currentHandler.database[user, KEY_JOIN_COUNT] as? String)?.toIntOrNull() ?: 0
            val deathLoc = (currentHandler.database[user, KEY_DEATH_LOC] as? String)?.takeIf { it.isNotBlank() } ?: ""
            val teleportLoc = (currentHandler.database[user, KEY_TELEPORT_LOC] as? String)?.takeIf { it.isNotBlank() } ?: ""

            val data = PlayerStateData(
                uuid = uuid,
                playerName = existingName ?: playerName,
                firstJoinTime = firstJoin,
                lastJoinTime = lastJoin,
                lastQuitTime = lastQuit,
                joinCount = joinCount,
                lastDeathLocation = deathLoc,
                lastTeleportLocation = teleportLoc
            )
            cache[uuid] = data
            data
        }.getOrElse { ex ->
            warning("[PlayerState] 读取玩家数据失败($playerName): ${ex.message}")
            null
        }
    }

    fun saveImmediate(uuid: UUID) {
        val data = cache[uuid] ?: return
        if (shuttingDown.get()) return
        submitAsync {
            if (shuttingDown.get()) return@submitAsync
            persist(data)
        }
    }

    private fun persist(data: PlayerStateData) {
        val currentHandler = handler ?: return
        runCatching {
            val user = data.uuid.toString()
            currentHandler.database[user, KEY_PLAYER_NAME] = data.playerName
            currentHandler.database[user, KEY_FIRST_JOIN] = data.firstJoinTime.toString()
            currentHandler.database[user, KEY_LAST_JOIN] = data.lastJoinTime.toString()
            currentHandler.database[user, KEY_LAST_QUIT] = data.lastQuitTime.toString()
            currentHandler.database[user, KEY_JOIN_COUNT] = data.joinCount.toString()
            currentHandler.database[user, KEY_DEATH_LOC] = data.lastDeathLocation
            currentHandler.database[user, KEY_TELEPORT_LOC] = data.lastTeleportLocation
        }.onFailure { ex ->
            warning("[PlayerState] 保存玩家数据失败(${data.uuid}): ${ex.message}")
        }
    }

    private fun flushCache() {
        val currentHandler = handler ?: return
        for ((_, data) in cache) {
            runCatching { persist(data) }.onFailure { ex ->
                warning("[PlayerState] flushCache 保存失败(${data.uuid}): ${ex.message}")
            }
        }
    }

    fun deserializeLocation(raw: String): Location? {
        val parts = raw.split(":")
        if (parts.size < 4) return null
        val worldName = parts[0]
        val x = parts[1].toDoubleOrNull() ?: return null
        val y = parts[2].toDoubleOrNull() ?: return null
        val z = parts[3].toDoubleOrNull() ?: return null
        val yaw = parts.getOrElse(4) { "0.0" }.toFloatOrNull() ?: 0f
        val pitch = parts.getOrElse(5) { "0.0" }.toFloatOrNull() ?: 0f
        val world = Bukkit.getWorld(worldName) ?: return null
        return Location(world, x, y, z, yaw, pitch)
    }

    fun serializeLocation(location: Location): String? {
        val worldName = location.world?.name ?: return null
        return "$worldName:${location.x}:${location.y}:${location.z}:${location.yaw}:${location.pitch}"
    }
}
```

- [ ] **步骤 2：验证编译**

```bash
./gradlew :project:core:compileKotlin
```
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/playerstate/PlayerStateStorage.kt
git commit -m "feat(playerstate): 添加 PlayerStateStorage 持久化层"
```

---

### 任务 3：PlayerStateService 对外 API

**文件：**
- 创建：`project/core/src/main/kotlin/com/pixlehavencore/feature/playerstate/PlayerStateService.kt`

- [ ] **步骤 1：创建 PlayerStateService.kt**

```kotlin
package com.pixlehavencore.feature.playerstate

import org.bukkit.Location
import java.util.UUID

object PlayerStateService {

    fun init() {
        PlayerStateSettings.init()
        PlayerStateStorage.init()
    }

    fun reload() {
        PlayerStateSettings.reload()
        PlayerStateStorage.reload()
    }

    fun stop() {
        PlayerStateStorage.close()
    }

    fun isEnabled(): Boolean = PlayerStateSettings.enabled

    // ========== 登录态 ==========

    fun getOrCreate(uuid: UUID): PlayerStateData = PlayerStateStorage.getOrCreate(uuid)

    fun get(uuid: UUID): PlayerStateData? = PlayerStateStorage.get(uuid)

    fun loadFromDatabase(uuid: UUID, playerName: String): PlayerStateData? =
        PlayerStateStorage.loadFromDatabase(uuid, playerName)

    fun isFirstJoin(uuid: UUID): Boolean {
        val data = PlayerStateStorage.get(uuid) ?: return true
        return data.firstJoinTime == 0L && data.joinCount == 0
    }

    fun getFirstJoinTime(uuid: UUID): Long? {
        val data = get(uuid) ?: return null
        return data.firstJoinTime.takeIf { it > 0 }
    }

    fun getLastJoinTime(uuid: UUID): Long? {
        val data = get(uuid) ?: return null
        return data.lastJoinTime.takeIf { it > 0 }
    }

    fun getJoinCount(uuid: UUID): Int {
        return get(uuid)?.joinCount ?: 0
    }

    fun getLastQuitTime(uuid: UUID): Long? {
        val data = get(uuid) ?: return null
        return data.lastQuitTime.takeIf { it > 0 }
    }

    fun getPlayerName(uuid: UUID): String? {
        return get(uuid)?.playerName?.takeIf { it.isNotBlank() }
    }

    // ========== 位置 ==========

    fun getLastDeathLocation(uuid: UUID): Location? {
        val raw = get(uuid)?.lastDeathLocation?.takeIf { it.isNotBlank() } ?: return null
        return PlayerStateStorage.deserializeLocation(raw)
    }

    fun setLastDeathLocation(uuid: UUID, loc: Location) {
        val data = getOrCreate(uuid)
        data.lastDeathLocation = PlayerStateStorage.serializeLocation(loc) ?: return
        PlayerStateStorage.saveImmediate(uuid)
    }

    fun getLastTeleportLocation(uuid: UUID): Location? {
        val raw = get(uuid)?.lastTeleportLocation?.takeIf { it.isNotBlank() } ?: return null
        return PlayerStateStorage.deserializeLocation(raw)
    }

    fun setLastTeleportLocation(uuid: UUID, loc: Location) {
        val data = getOrCreate(uuid)
        data.lastTeleportLocation = PlayerStateStorage.serializeLocation(loc) ?: return
        PlayerStateStorage.saveImmediate(uuid)
    }

    // ========== 管理 ==========

    fun reset(uuid: UUID) {
        val data = getOrCreate(uuid)
        data.firstJoinTime = 0L
        data.lastJoinTime = 0L
        data.lastQuitTime = 0L
        data.joinCount = 0
        data.lastDeathLocation = ""
        data.lastTeleportLocation = ""
        PlayerStateStorage.saveImmediate(uuid)
    }
}
```

- [ ] **步骤 2：验证编译**

```bash
./gradlew :project:core:compileKotlin
```
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/playerstate/PlayerStateService.kt
git commit -m "feat(playerstate): 添加 PlayerStateService 对外 API"
```

---

### 任务 4：PlayerStateListener 事件处理

**文件：**
- 创建：`project/core/src/main/kotlin/com/pixlehavencore/feature/playerstate/PlayerStateListener.kt`

- [ ] **步骤 1：创建 PlayerStateListener.kt**

```kotlin
package com.pixlehavencore.feature.playerstate

import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent

object PlayerStateListener {

    @SubscribeEvent
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!PlayerStateSettings.enabled) return
        val player = event.player
        val uuid = player.uniqueId

        var data = PlayerStateStorage.get(uuid)
        if (data == null) {
            data = PlayerStateStorage.loadFromDatabase(uuid, player.name)
        }
        if (data == null) {
            data = PlayerStateStorage.getOrCreate(uuid)
        }

        val now = System.currentTimeMillis()
        if (data.firstJoinTime == 0L) {
            data.firstJoinTime = now
        }
        data.playerName = player.name
        data.lastJoinTime = now
        data.joinCount += 1
        PlayerStateStorage.saveImmediate(uuid)
    }

    @SubscribeEvent
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (!PlayerStateSettings.enabled) return
        val uuid = event.player.uniqueId
        val data = PlayerStateStorage.getOrCreate(uuid)
        data.lastQuitTime = System.currentTimeMillis()
        PlayerStateStorage.saveImmediate(uuid)
    }

    @SubscribeEvent(priority = EventPriority.MONITOR)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        if (!PlayerStateSettings.enabled) return
        val player = event.player
        PlayerStateService.setLastDeathLocation(player.uniqueId, player.location)
    }

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        if (!PlayerStateSettings.enabled) return
        PlayerStateService.setLastTeleportLocation(event.player.uniqueId, event.from)
    }
}
```

- [ ] **步骤 2：验证编译**

```bash
./gradlew :project:core:compileKotlin
```
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/playerstate/PlayerStateListener.kt
git commit -m "feat(playerstate): 添加 PlayerStateListener 自动事件处理"
```

---

### 任务 5：PixleHavenCore 生命周期注册

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/PixleHavenCore.kt`

- [ ] **步骤 1：注册 PlayerStateService 生命周期**

用 Read 读取 `PixleHavenCore.kt` 当前内容。

在文件头部 import 区域添加（按字母顺序）：

```kotlin
import com.pixlehavencore.feature.playerstate.PlayerStateService
```

在 `onEnable()` 中，`FirstJoinService.init()` 之后添加：

```kotlin
        PlayerStateService.init()
```

在 `onDisable()` 中，`FirstJoinService.stop()` 之后添加：

```kotlin
        PlayerStateService.stop()
```

- [ ] **步骤 2：验证编译**

```bash
./gradlew :project:core:compileKotlin
```
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/PixleHavenCore.kt
git commit -m "feat(playerstate): 在插件生命周期中注册 PlayerStateService"
```

---

### 任务 6：构建验证

**文件：** 无

- [ ] **步骤 1：完整构建**

```bash
./gradlew build
```
预期：BUILD SUCCESSFUL

- [ ] **步骤 2：逻辑审查**

检查以下路径：
- 玩家首次加入 → `firstJoinTime` 写入当前时间，`joinCount` = 1
- 玩家再次加入 → `lastJoinTime` 更新，`joinCount` 递增，`firstJoinTime` 不变
- 玩家退出 → `lastQuitTime` 写入
- 玩家死亡 → `lastDeathLocation` 写入
- 玩家传送 → `lastTeleportLocation` 写入
- `enabled: false` → 所有事件监听跳过
- `isFirstJoin()` 在缓存无首登时为 true

---

## 文件变更清单

| 文件 | 操作 |
|---|---|
| `.../feature/playerstate/PlayerStateSettings.kt` | 创建 |
| `.../feature/playerstate/PlayerStateStorage.kt` | 创建 |
| `.../feature/playerstate/PlayerStateService.kt` | 创建 |
| `.../feature/playerstate/PlayerStateListener.kt` | 创建 |
| `.../resources/feature/player-state.yml` | 创建 |
| `.../PixleHavenCore.kt` | 修改 |
