# 首次登录随机传送 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在 `feature/base/` 模块实现首次登录随机传送：玩家第一次进服时自动传送到以出生点为中心的圆环区域随机安全位置。

**架构：** FirstJoinSettings 读取配置 → FirstJoinService 判定首次（查 title_player_data 表 + hasPlayedBefore 回退）+ 随机坐标计算 + 安全位置传送 → BaseListener 的 PlayerJoinEvent 入口触发。

**技术栈：** Kotlin, TabooLib 6.3.0 (MultipleHandler), Paper 1.21.11, Folia 线程安全

---

### 任务 1：FirstJoinSettings + base-command.yml

**文件：**
- 创建：`project/core/src/main/kotlin/com/pixlehavencore/feature/base/FirstJoinSettings.kt`
- 修改：`project/core/src/main/resources/feature/base-command.yml`

- [ ] **步骤 1：创建 FirstJoinSettings.kt**

```kotlin
package com.pixlehavencore.feature.base

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object FirstJoinSettings {

    @Config("feature/base-command.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = false
        private set

    var centerX: Double = 0.0
        private set

    var centerZ: Double = 0.0
        private set

    var minRadius: Double = 50.0
        private set

    var maxRadius: Double = 500.0
        private set

    var safeLocationRetries: Int = 10
        private set

    var msgTeleported: String = "&a你被随机传送到 {x}, {y}, {z}"
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("first-join.enabled", false)
        centerX = config.getDouble("first-join.centerX", 0.0)
        centerZ = config.getDouble("first-join.centerZ", 0.0)
        minRadius = config.getDouble("first-join.minRadius", 50.0)
        maxRadius = config.getDouble("first-join.maxRadius", 500.0)
        safeLocationRetries = config.getInt("first-join.safeLocationRetries", 10)
        msgTeleported = config.getString("first-join.msgTeleported") ?: "&a你被随机传送到 {x}, {y}, {z}"
    }
}
```

- [ ] **步骤 2：修改 base-command.yml**

在文件末尾追加：

```yaml

# 首次登录随机传送配置
first-join:
  # 总开关，默认关闭
  enabled: false
  # 传送中心坐标（相对于世界出生点 0,0 的偏移）
  centerX: 0
  centerZ: 0
  # 最小半径（距中心至少多远，防止传送到出生点附近）
  minRadius: 50
  # 最大半径（距中心至多多远）
  maxRadius: 500
  # 安全位置查找最大尝试次数
  safeLocationRetries: 10
  # 传送成功提示（{x}{y}{z} 为占位符）
  msgTeleported: "&a你被随机传送到 {x}, {y}, {z}"
```

用 Read 读取当前 base-command.yml，确认当前内容不包含 `first-join` 节，然后追加。

- [ ] **步骤 3：验证编译**

```bash
./gradlew :project:core:compileKotlin
```
预期：BUILD SUCCESSFUL（FirstJoinSettings 编译通过）

- [ ] **步骤 4：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/base/FirstJoinSettings.kt project/core/src/main/resources/feature/base-command.yml
git commit -m "feat(first-join): 添加 FirstJoinSettings 配置类和 YAML 配置"
```

---

### 任务 2：FirstJoinService 核心逻辑

**文件：**
- 创建：`project/core/src/main/kotlin/com/pixlehavencore/feature/base/FirstJoinService.kt`

- [ ] **步骤 1：创建 FirstJoinService.kt**

```kotlin
package com.pixlehavencore.feature.base

import com.pixlehavencore.util.DatabaseUtils
import com.pixlehavencore.util.TextUtils
import com.pixlehavencore.util.cancelTaskSafely
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import taboolib.expansion.MultipleHandler
import taboolib.platform.util.submit as submitOnEntity
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object FirstJoinService {

    private const val TABLE_NAME = "title_player_data"
    private const val KEY_ACTIVE = "active_title"
    private const val KEY_OWNED = "owned_titles"

    @Volatile
    private var titleHandler: MultipleHandler? = null

    fun init() {
        FirstJoinSettings.init()
        if (!FirstJoinSettings.enabled) return
        submitAsync {
            runCatching {
                titleHandler = DatabaseUtils.newPlayerDataHandler(TABLE_NAME, syncTick = 200L)
            }.onFailure { ex ->
                warning("[FirstJoin] 连接 title_player_data 失败: ${ex.message}")
                warning("[FirstJoin] 将回退到 hasPlayedBefore() 判定")
            }
        }
    }

    fun reload() {
        stop()
        FirstJoinSettings.reload()
        if (FirstJoinSettings.enabled) init()
    }

    fun stop() {
        DatabaseUtils.closeMultipleHandler(titleHandler)
        titleHandler = null
    }

    fun handleJoin(player: Player) {
        if (!FirstJoinSettings.enabled) return
        if (!BaseCommandSettings.enabled) return

        val uuid = player.uniqueId
        val world = Bukkit.getWorlds().firstOrNull() ?: return

        submitAsync {
            if (!isFirstJoin(uuid)) return@submitAsync

            val spawn = world.spawnLocation
            val centerX = spawn.x + FirstJoinSettings.centerX
            val centerZ = spawn.z + FirstJoinSettings.centerZ

            val targetLoc = findRandomSafeLocation(
                world,
                centerX,
                centerZ,
                FirstJoinSettings.minRadius,
                FirstJoinSettings.maxRadius,
                FirstJoinSettings.safeLocationRetries
            )

            if (targetLoc == null) {
                warning("[FirstJoin] 未找到安全随机位置(${player.name})")
                return@submitAsync
            }

            player.submitOnEntity {
                player.teleport(targetLoc)
                val msg = FirstJoinSettings.msgTeleported
                    .replace("{x}", targetLoc.blockX.toString())
                    .replace("{y}", targetLoc.blockY.toString())
                    .replace("{z}", targetLoc.blockZ.toString())
                player.sendMessage(TextUtils.parse(msg))
            }
        }
    }

    private fun isFirstJoin(uuid: UUID): Boolean {
        val currentHandler = titleHandler
        if (currentHandler != null) {
            return runCatching {
                val user = uuid.toString()
                val activeTitle = (currentHandler.database[user, KEY_ACTIVE] as? String)?.takeIf { it.isNotBlank() }
                val ownedJson = (currentHandler.database[user, KEY_OWNED] as? String)?.takeIf { it.isNotBlank() }
                activeTitle == null && ownedJson == null
            }.getOrDefault(false)
        }
        return !Bukkit.getOfflinePlayer(uuid).hasPlayedBefore()
    }

    private fun findRandomSafeLocation(
        world: World,
        centerX: Double,
        centerZ: Double,
        minRadius: Double,
        maxRadius: Double,
        retries: Int
    ): Location? {
        val actualMin = minRadius.coerceAtMost(maxRadius)
        val actualMax = maxRadius.coerceAtLeast(actualMin)

        repeat(5) {
            val angle = Random.nextDouble(0.0, 2 * Math.PI)
            val distance = actualMin + Random.nextDouble(0.0, actualMax - actualMin)
            val x = centerX + distance * cos(angle)
            val z = centerZ + distance * sin(angle)

            for (dy in 0 until retries) {
                val y = world.getHighestBlockYAt(x.toInt(), z.toInt())
                val loc = Location(world, x, y + 1.0, z)
                val block = loc.block
                val above = world.getBlockAt(loc.blockX, loc.blockY + 1, loc.blockZ)
                if (block.isPassable && above.isPassable) {
                    return loc
                }
            }
        }

        return null
    }

    fun isEnabled(): Boolean = FirstJoinSettings.enabled
}
```

- [ ] **步骤 2：验证编译**

```bash
./gradlew :project:core:compileKotlin
```
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/base/FirstJoinService.kt
git commit -m "feat(first-join): 添加 FirstJoinService 首次检测与随机传送逻辑"
```

---

### 任务 3：BaseListener 新增 PlayerJoinEvent

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/base/BaseListener.kt`

- [ ] **步骤 1：在 BaseListener 中添加 PlayerJoinEvent 监听**

用 Read 读取 BaseListener.kt，在文件头部添加 import：

```kotlin
import org.bukkit.event.player.PlayerJoinEvent
```

在类体内末尾（`onPlayerDamage` 方法 `}` 之后，`object` 结束 `}` 之前）添加：

```kotlin
    @SubscribeEvent
    fun onPlayerJoin(event: PlayerJoinEvent) {
        FirstJoinService.handleJoin(event.player)
    }
```

- [ ] **步骤 2：验证编译**

```bash
./gradlew :project:core:compileKotlin
```
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/base/BaseListener.kt
git commit -m "feat(first-join): BaseListener 新增首次登录随机传送监听"
```

---

### 任务 4：PixleHavenCore 生命周期注册

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/PixleHavenCore.kt`

- [ ] **步骤 1：注册 FirstJoinService 生命周期**

用 Read 读取 PixleHavenCore.kt。
在文件头部 import 区域添加：

```kotlin
import com.pixlehavencore.feature.base.FirstJoinService
```

在 `onEnable()` 中，`BackService.init()` 之后添加：

```kotlin
        FirstJoinService.init()
```

在 `onDisable()` 中，`BackStorage.close()` 之前添加（与 BackService.stop() 相邻）：

```kotlin
        FirstJoinService.stop()
```

- [ ] **步骤 2：验证编译**

```bash
./gradlew :project:core:compileKotlin
```
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/PixleHavenCore.kt
git commit -m "feat(first-join): 在插件生命周期中注册 FirstJoinService"
```

---

### 任务 5：构建验证

**文件：** 无

- [ ] **步骤 1：完整构建**

```bash
./gradlew build
```
预期：BUILD SUCCESSFUL

- [ ] **步骤 2：逻辑审查**

审查以下路径：
- `enabled: false` → 不触发任何行为
- 首次登录，title table 可访问无数据 → 随机传送 + 消息
- 首次登录，title table 不可访问 → 回退 `hasPlayedBefore()` → 随机传送
- 非首次 → 跳过
- 安全位置多轮未找到 → warning 日志，不传送
- `minRadius` > `maxRadius` → 自动交换

无需额外 commit（构建验证不产生代码变更）。


## 文件变更清单

| 文件 | 操作 |
|---|---|
| `.../feature/base/FirstJoinSettings.kt` | 创建 |
| `.../feature/base/FirstJoinService.kt` | 创建 |
| `.../feature/base/BaseListener.kt` | 修改 |
| `.../resources/feature/base-command.yml` | 修改 |
| `.../PixleHavenCore.kt` | 修改 |
