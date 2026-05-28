# 温度系统真实感改造实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将温度系统改造为体感温度中间层模型，引入独立水温系统、遮蔽细化和更真实的温差参数

**架构：** 引入体感温度（feelsLike）作为环境修正与体温变化之间的中间计算层。水温独立计算（生物群系基础 + 季节滞后 + 深度修正），浸水时体温直接趋向水温。遮蔽细化为 NONE/CANOPY/BUILDING 三级。昼夜温差改为可配置 scale，季节温差加大。

**技术栈：** Kotlin, Bukkit API, TabooLib 配置系统

---

## 文件结构

### 修改文件
- `project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/RealWorldModels.kt` — 新增 ShelterType 枚举，替换 PlayerEnvState.isSheltered
- `project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/temperature/TemperatureSettings.kt` — 新增水温、体感、遮蔽配置字段
- `project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/temperature/TemperatureEngine.kt` — 重构 compute() 为体感温度模型，新增水温计算
- `project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/season/SeasonEngine.kt` — 修改 getTimeTemperatureModifier() 使用 dayNightScale
- `project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/SurvivalHud.kt` — 更新遮蔽显示为三态图标
- `project/core/src/main/resources/feature/realworld/temperature.yml` — 新增水温、体感、遮蔽配置项

---

## 任务 1：新增 ShelterType 枚举并替换 isSheltered

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/RealWorldModels.kt:99-103`
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/RealWorldModels.kt:126-127`

- [ ] **步骤 1：在 DayPhase 枚举后新增 ShelterType 枚举**

在 `RealWorldModels.kt` 的 `DayPhase` 枚举后添加：

```kotlin
enum class ShelterType {
    NONE,     // 露天：无修正
    CANOPY,   // 树荫/半遮蔽：+2°C
    BUILDING, // 实体建筑：+8°C
}
```

- [ ] **步骤 2：替换 PlayerEnvState 中的 isSheltered 字段**

找到 `PlayerEnvState` 数据类中的：
```kotlin
var isSheltered: Boolean = false,
```

替换为：
```kotlin
var shelterType: ShelterType = ShelterType.NONE,
```

- [ ] **步骤 3：验证编译**

运行：`./gradlew :project:core:compileKotlin`
预期：编译失败（其他文件引用了 isSheltered），这是预期的

---

## 任务 2：新增 TemperatureSettings 配置字段

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/temperature/TemperatureSettings.kt`

- [ ] **步骤 1：在 TemperatureSettings 对象中新增配置字段声明**

在现有字段后添加（建议在 `shelterHorizontalRadius` 后）：

```kotlin
var wetnessCoolingFactor: Double = 8.0
    private set
var dayNightScale: Double = 10.0
    private set
var waterEnabled: Boolean = true
    private set
var waterConductivityMultiplier: Double = 4.0
    private set
var waterDepthCoolPer10Blocks: Double = 1.0
    private set
var waterMaxDepthCool: Double = 5.0
    private set
var waterSeasonLagRatio: Double = 0.3
    private set
var shelterCanopyBonus: Double = 2.0
    private set
var shelterBuildingBonus: Double = 8.0
    private set
var shelterLeavesCountAsCanopy: Boolean = true
    private set
```

- [ ] **步骤 2：在 reload() 方法中添加配置读取逻辑**

在 `reload()` 方法的现有读取逻辑后添加：

```kotlin
wetnessCoolingFactor = config.getDouble("feels-like.wetness-cooling", 8.0).coerceAtLeast(0.0)
dayNightScale = config.getDouble("time.day-night-scale", 10.0).coerceAtLeast(0.0)
waterEnabled = config.getBoolean("water.enabled", true)
waterConductivityMultiplier = config.getDouble("water.conductivity-multiplier", 4.0).coerceAtLeast(1.0)
waterDepthCoolPer10Blocks = config.getDouble("water.depth-cool-per-10-blocks", 1.0).coerceAtLeast(0.0)
waterMaxDepthCool = config.getDouble("water.max-depth-cool", 5.0).coerceAtLeast(0.0)
waterSeasonLagRatio = config.getDouble("water.season-lag-ratio", 0.3).coerceIn(0.0, 1.0)
shelterCanopyBonus = config.getDouble("shelter.canopy-bonus", 2.0)
shelterBuildingBonus = config.getDouble("shelter.building-bonus", 8.0)
shelterLeavesCountAsCanopy = config.getBoolean("shelter.leaves-count-as-canopy", true)
```

- [ ] **步骤 3：验证编译**

运行：`./gradlew :project:core:compileKotlin`
预期：编译失败（仍然有 isSheltered 引用），继续下一步

---

## 任务 3：更新 temperature.yml 配置文件

**文件：**
- 修改：`project/core/src/main/resources/feature/realworld/temperature.yml`

- [ ] **步骤 1：在 temperature.yml 末尾添加新配置节**

在文件末尾添加：

```yaml
# 体感温度系统
feels-like:
  wetness-cooling: 8.0              # 满湿时最大蒸发冷却(°C)

# 昼夜温差
time:
  day-night-scale: 10.0             # 基础昼夜温差幅度（±10°C）

# 水温系统
water:
  enabled: true
  conductivity-multiplier: 4.0      # 水中导热倍率（水中导热速度是空气的 4 倍）
  depth-cool-per-10-blocks: 1.0     # 每深入水下 10 格，水温降低的度数
  max-depth-cool: 5.0               # 深度降温上限（最多降 5°C）
  season-lag-ratio: 0.3             # 季节滞后比例（水温跟随上一季的 30% 修正）
```

- [ ] **步骤 2：修改 shelter 配置节**

找到现有的 shelter 配置节，替换为：

```yaml
# 遮蔽细化
shelter:
  canopy-bonus: 2.0                 # 树荫/半遮蔽修正(°C)
  building-bonus: 8.0               # 建筑修正(°C)
  horizontal-radius: 1              # 建筑判定水平半径
  glass-counts-as-shelter: false
  leaves-count-as-canopy: true      # 树叶算树荫不算建筑
```

- [ ] **步骤 3：验证配置文件格式**

运行：`./gradlew :project:core:processResources`
预期：BUILD SUCCESSFUL

---

## 任务 4：修改 Season 枚举温度修正值

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/RealWorldModels.kt:5-57`

- [ ] **步骤 1：修改 Season 枚举的 temperatureModifier 值**

在 `RealWorldModels.kt` 中找到 Season 枚举，修改 temperatureModifier 值：

```kotlin
SPRING(
    "春",
    3.0,  // 原 5.0，改为 3.0
    1.0,
    mapOf(...)
),
SUMMER(
    "夏",
    10.0,  // 原 15.0，改为 10.0
    1.5,
    mapOf(...)
),
AUTUMN(
    "秋",
    0.0,  // 保持不变
    0.9,
    mapOf(...)
),
WINTER(
    "冬",
    -20.0,  // 原 -15.0，改为 -20.0
    0.6,
    mapOf(...)
);
```

- [ ] **步骤 2：验证编译**

运行：`./gradlew :project:core:compileKotlin`
预期：编译失败（isSheltered 引用），继续下一步

---

## 任务 5：重构 TemperatureEngine 遮蔽判定逻辑

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/temperature/TemperatureEngine.kt:138-157`

- [ ] **步骤 1：修改 updateShelterState() 方法**

找到 `updateShelterState()` 方法（约 138-157 行），替换整个方法体：

```kotlin
private fun updateShelterState(player: Player, state: PlayerEnvState, tickIntervalSeconds: Int) {
    state.shelterCacheTimer -= tickIntervalSeconds.coerceAtLeast(0).toDouble()

    val eyeBlock = player.eyeLocation.block
    val movedToDifferentBlock =
        state.shelterCacheBlockX != eyeBlock.x ||
            state.shelterCacheBlockY != eyeBlock.y ||
            state.shelterCacheBlockZ != eyeBlock.z

    if (!movedToDifferentBlock && state.shelterCacheTimer > 0.0) {
        return
    }

    state.shelterType = classifyShelter(player)
    state.isWeatherSheltered = isWeatherSheltered(player.eyeLocation)
    state.shelterCacheBlockX = eyeBlock.x
    state.shelterCacheBlockY = eyeBlock.y
    state.shelterCacheBlockZ = eyeBlock.z
    state.shelterCacheTimer = SHELTER_CACHE_SECONDS
}

private fun classifyShelter(player: Player): ShelterType {
    val hasOverhead = hasAnyOverheadCover(player.eyeLocation)
    if (!hasOverhead) return ShelterType.NONE

    val hasCompleteRoof = hasWeatherTopCoverage(player.eyeLocation)
    return if (hasCompleteRoof) ShelterType.BUILDING else ShelterType.CANOPY
}
```

- [ ] **步骤 2：修改 isWeatherSheltered() 方法**

找到 `isWeatherSheltered(location: Location)` 方法（约 131-136 行），修改为：

```kotlin
fun isWeatherSheltered(location: Location): Boolean {
    val hasOverhead = hasAnyOverheadCover(location)
    if (!hasOverhead) return false

    val hasCompleteRoof = hasWeatherTopCoverage(location)
    return hasCompleteRoof
}
```

- [ ] **步骤 3：验证编译**

运行：`./gradlew :project:core:compileKotlin`
预期：编译失败（compute() 中仍引用 isSheltered），继续下一步

---

## 任务 6：新增水温计算方法

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/temperature/TemperatureEngine.kt`

- [ ] **步骤 1：新增 getBiomeWaterTemp() 方法**

在 TemperatureEngine 对象中添加方法（建议在 getBiomeBaseTemperature() 后）：

```kotlin
fun getBiomeWaterTemp(biomeName: String): Double {
    val normalizedName = biomeName.lowercase()
    return when {
        normalizedName.contains("jungle") || normalizedName.contains("bamboo") -> 24.0
        normalizedName.contains("swamp") || normalizedName.contains("mangrove") -> 22.0
        normalizedName.contains("desert") || normalizedName.contains("badlands") -> 20.0
        normalizedName.contains("plains") || normalizedName.contains("forest") || 
            normalizedName.contains("beach") -> 16.0
        normalizedName.contains("ocean") || normalizedName.contains("river") -> 14.0
        normalizedName.contains("taiga") || normalizedName.contains("dark_forest") -> 10.0
        normalizedName.contains("snow") || normalizedName.contains("ice") || 
            normalizedName.contains("frozen") -> 2.0
        else -> 14.0
    }
}
```

- [ ] **步骤 2：新增 calculateWaterTemp() 方法**

在 TemperatureEngine 对象中添加方法：

```kotlin
fun calculateWaterTemp(player: Player, global: GlobalEnvState): Double {
    if (!TemperatureSettings.waterEnabled) {
        return 14.0
    }

    val biomeName = player.location.block.biome.toString().lowercase()
    val biomeWaterTemp = getBiomeWaterTemp(biomeName)

    val previousSeason = getPreviousSeason(global.season)
    val seasonLag = previousSeason.temperatureModifier * TemperatureSettings.waterSeasonLagRatio

    val waterSurfaceY = findWaterSurfaceY(player.location)
    val waterDepth = (waterSurfaceY - player.location.blockY).coerceAtLeast(0)
    val depthModifier = -(waterDepth / 10.0).coerceAtMost(TemperatureSettings.waterMaxDepthCool) * 
        TemperatureSettings.waterDepthCoolPer10Blocks

    return biomeWaterTemp + seasonLag + depthModifier
}

private fun getPreviousSeason(currentSeason: Season): Season {
    val previousIndex = (currentSeason.ordinal - 1 + Season.entries.size) % Season.entries.size
    return Season.entries[previousIndex]
}

private fun findWaterSurfaceY(location: Location): Int {
    val world = location.world ?: return location.blockY
    val maxY = world.maxHeight - 1
    for (y in location.blockY + 1..maxY) {
        val block = world.getBlockAt(location.blockX, y, location.blockZ)
        if (block.type != Material.WATER) {
            return y
        }
    }
    return maxY
}
```

- [ ] **步骤 3：验证编译**

运行：`./gradlew :project:core:compileKotlin`
预期：编译失败（compute() 中仍引用 isSheltered），继续下一步

---

## 任务 7：新增体感温度计算方法

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/temperature/TemperatureEngine.kt`

- [ ] **步骤 1：新增 getBiomeDayNightFactor() 方法**

在 TemperatureEngine 对象中添加方法：

```kotlin
fun getBiomeDayNightFactor(biomeName: String): Double {
    val normalizedName = biomeName.lowercase()
    return when {
        normalizedName.contains("desert") || normalizedName.contains("badlands") -> 1.5
        normalizedName.contains("jungle") || normalizedName.contains("bamboo") -> 0.5
        normalizedName.contains("swamp") || normalizedName.contains("mangrove") -> 0.5
        normalizedName.contains("ocean") || normalizedName.contains("river") -> 0.7
        else -> 1.0
    }
}
```

- [ ] **步骤 2：新增 calculateAirFeelsLike() 方法**

在 TemperatureEngine 对象中添加方法：

```kotlin
fun calculateAirFeelsLike(
    player: Player,
    state: PlayerEnvState,
    global: GlobalEnvState,
    biomeBaseTemperature: Double,
    seasonModifier: Double,
    timeModifier: Double,
    weatherModifier: Double,
    altitudeModifier: Double,
    armorModifier: Double,
): Double {
    val shelterModifier = when (state.shelterType) {
        ShelterType.NONE -> 0.0
        ShelterType.CANOPY -> TemperatureSettings.shelterCanopyBonus
        ShelterType.BUILDING -> TemperatureSettings.shelterBuildingBonus
    }

    val blockModifier = state.temperatureBlockModifier

    val wetnessModifier = if (player.isInWater) {
        0.0
    } else {
        -state.wetness * TemperatureSettings.wetnessCoolingFactor
    }

    return biomeBaseTemperature +
        seasonModifier + timeModifier + weatherModifier +
        altitudeModifier + shelterModifier + armorModifier +
        blockModifier + wetnessModifier
}
```

- [ ] **步骤 3：验证编译**

运行：`./gradlew :project:core:compileKotlin`
预期：编译失败（compute() 中仍引用 isSheltered），继续下一步

---

## 任务 8：重构 TemperatureEngine.compute() 方法

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/temperature/TemperatureEngine.kt:30-92`

- [ ] **步骤 1：替换 compute() 方法体**

找到 `compute()` 方法（约 30-92 行），替换整个方法体：

```kotlin
fun compute(
    player: Player,
    state: PlayerEnvState,
    global: GlobalEnvState,
    tickIntervalSeconds: Int,
) {
    val location = player.location
    val biomeName = location.block.biome.toString().lowercase()
    val worldTime = location.world?.time ?: 6000L

    val biomeBaseTemperature = getBiomeBaseTemperature(biomeName)
    state.biomeTemperature = biomeBaseTemperature

    val seasonModifier = SeasonEngine.getTemperatureModifier(global)
    val timeModifier = SeasonEngine.getTimeTemperatureModifier(worldTime, biomeName)
    val weatherModifier = if (WeatherSettings.localEnabled) {
        WeatherQuery.getTemperatureModifierAt(player.location, global)
    } else {
        global.weather.temperatureModifier
    }
    val altitudeModifier = computeAltitudeModifier(location.blockY)

    updateShelterState(player, state, tickIntervalSeconds)
    val armorModifier = getArmorTemperatureBonus(player)

    computeWetness(player, state, global, tickIntervalSeconds)

    state.heatSourceScanTimer -= tickIntervalSeconds.coerceAtLeast(0)
    if (state.heatSourceScanTimer <= 0.0) {
        val ambientBaseline = biomeBaseTemperature +
            seasonModifier + timeModifier + weatherModifier +
            altitudeModifier + armorModifier
        val scanResult = scanTemperatureBlocks(player, ambientBaseline)
        state.nearHeatSource = scanResult.first
        state.temperatureBlockModifier = scanResult.second
        val interval = TemperatureSettings.heatSourceScanIntervalSeconds.toDouble()
        while (state.heatSourceScanTimer <= 0.0) {
            state.heatSourceScanTimer += interval
        }
    }

    val feelsLike = if (player.isInWater && TemperatureSettings.waterEnabled) {
        calculateWaterTemp(player, global)
    } else {
        calculateAirFeelsLike(
            player = player,
            state = state,
            global = global,
            biomeBaseTemperature = biomeBaseTemperature,
            seasonModifier = seasonModifier,
            timeModifier = timeModifier,
            weatherModifier = weatherModifier,
            altitudeModifier = altitudeModifier,
            armorModifier = armorModifier,
        )
    }

    val maxChangePerTick = TemperatureSettings.maxChangePerTick.coerceAtLeast(0.0)
    val changeRate = if (player.isInWater && TemperatureSettings.waterEnabled) {
        TemperatureSettings.waterConductivityMultiplier
    } else {
        1.0
    }

    val delta = (feelsLike - state.temperature) * changeRate
    val change = delta.coerceIn(-maxChangePerTick, maxChangePerTick)

    state.temperature += change
    state.temperaturePhase = classifyTemperature(state.temperature)
}
```

- [ ] **步骤 2：验证编译**

运行：`./gradlew :project:core:compileKotlin`
预期：编译失败（SeasonEngine.getTimeTemperatureModifier 签名不匹配），继续下一步

---

## 任务 9：修改 SeasonEngine.getTimeTemperatureModifier()

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/season/SeasonEngine.kt:59-63`

- [ ] **步骤 1：修改 getTimeTemperatureModifier() 方法签名和实现**

找到 `getTimeTemperatureModifier(worldTime: Long)` 方法（约 59-63 行），替换为：

```kotlin
fun getTimeTemperatureModifier(worldTime: Long, biomeName: String = ""): Double {
    val normalizedTime = ((worldTime % 24000L) + 24000L) % 24000L
    val radians = 2.0 * Math.PI * (normalizedTime - 6000.0) / 24000.0
    val biomeFactor = if (biomeName.isNotEmpty()) {
        TemperatureEngine.getBiomeDayNightFactor(biomeName)
    } else {
        1.0
    }
    return TemperatureSettings.dayNightScale * biomeFactor * cos(radians)
}
```

- [ ] **步骤 2：添加 TemperatureEngine 和 TemperatureSettings 导入**

在 SeasonEngine.kt 文件顶部添加导入：

```kotlin
import com.pixlehavencore.feature.realworld.temperature.TemperatureEngine
import com.pixlehavencore.feature.realworld.temperature.TemperatureSettings
```

- [ ] **步骤 3：验证编译**

运行：`./gradlew :project:core:compileKotlin`
预期：编译失败（SurvivalHud.kt 中仍引用 isSheltered），继续下一步

---

## 任务 10：更新 SurvivalHud 遮蔽显示

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/SurvivalHud.kt:81-85`

- [ ] **步骤 1：修改 buildStatusActionBar() 中的遮蔽文本生成逻辑**

找到 `buildStatusActionBar()` 方法中的遮蔽文本生成部分（约 81-85 行），替换为：

```kotlin
val shelterText = when (state.shelterType) {
    ShelterType.NONE -> RealWorldSettings.hudUnshelteredIndicator
    ShelterType.CANOPY -> "🌳"
    ShelterType.BUILDING -> RealWorldSettings.hudShelteredIndicator
}
```

- [ ] **步骤 2：验证编译**

运行：`./gradlew :project:core:compileKotlin`
预期：编译失败（TemperatureEngine 中 isSheltered 引用），继续下一步

---

## 任务 11：修复 TemperatureEngine 中的 isSheltered 引用

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/temperature/TemperatureEngine.kt:52-53`

- [ ] **步骤 1：删除 compute() 中的 shelteredModifier 局部变量**

在 compute() 方法中，找到并删除这一行（约第 53 行）：

```kotlin
val shelteredModifier = if (state.isSheltered) 5.0 else 0.0
```

- [ ] **步骤 2：从 ambientBaseline 计算中移除 shelteredModifier**

找到 ambientBaseline 计算（约第 56-58 行），从加法链中移除 `shelteredModifier`：

```kotlin
val ambientBaseline = biomeBaseTemperature +
    seasonModifier + timeModifier + weatherModifier +
    altitudeModifier + armorModifier
```

注意：shelterModifier 现在在 calculateAirFeelsLike() 中计算，不再需要在 compute() 中处理。

- [ ] **步骤 3：验证编译**

运行：`./gradlew :project:core:compileKotlin`
预期：BUILD SUCCESSFUL

---

## 任务 12：完整构建验证

**文件：** 无新增修改

- [ ] **步骤 1：运行完整构建**

运行：`./gradlew build`
预期：BUILD SUCCESSFUL

- [ ] **步骤 2：检查生成的 JAR 文件**

运行：`ls -lh plugin/build/libs/`
预期：看到新生成的 JAR 文件

- [ ] **步骤 3：Commit 所有变更**

```bash
git add -A
git commit -m "feat(realworld): 温度系统真实感改造

- 引入体感温度中间层，综合所有环境因素
- 新增独立水温系统（生物群系基础 + 季节滞后 + 深度修正）
- 水中导热速度是空气的 4 倍
- 潮湿度改为蒸发冷却机制（-8°C 满湿）
- 遮蔽细化为 NONE/CANOPY/BUILDING 三级
- 昼夜温差改为可配置 scale（默认 ±10°C）
- 季节温差加大（春+3、夏+10、秋0、冬-20）
- 生物群系昼夜因子（沙漠 1.5、丛林 0.5）"
```

---

## 任务 13：手动测试验证

**文件：** 无新增修改

- [ ] **步骤 1：启动测试服务器**

将生成的 JAR 复制到测试服务器的 plugins 目录，启动服务器。

- [ ] **步骤 2：测试水温系统**

测试场景：
1. 夏天（`/rw season summer`）跳入热带水域（jungle 生物群系），观察体温是否趋向 24°C
2. 冬天（`/rw season winter`）跳入冻湖（snow 生物群系），观察体温是否快速下降
3. 深潜（水下 50 格），观察水温是否比浅水低 5°C
4. 浸水时观察潮湿度是否不影响体温（直接趋向水温）

预期：
- 热带夏天水温 ≈ 24°C
- 冻湖冬天水温 ≈ 2°C
- 深潜 50 格水温降低 5°C
- 浸水时体温变化速率是空气的 4 倍

- [ ] **步骤 3：测试昼夜温差**

测试场景：
1. 沙漠中午（`/time set 6000`），观察体感温度是否达 50°C+
2. 沙漠午夜（`/time set 18000`），观察体感温度是否降至 30°C 左右
3. 丛林中午，观察昼夜温差是否较小（约 ±5°C）

预期：
- 沙漠昼夜温差 ±15°C（dayNightScale 10.0 * 1.5）
- 丛林昼夜温差 ±5°C（dayNightScale 10.0 * 0.5）

- [ ] **步骤 4：测试季节温差**

测试场景：
1. 夏天平原中午，观察体感温度是否接近 35°C
2. 冬天雪原午夜，观察体感温度是否达 -40°C（致命）

预期：
- 夏天平原中午：15 + 10 + 10 = 35°C
- 冬天雪原午夜：-10 - 20 - 10 = -40°C

- [ ] **步骤 5：测试遮蔽系统**

测试场景：
1. 露天站立，观察 HUD 显示 ☁ 图标，体温无修正
2. 站在树荫下（树叶覆盖），观察 HUD 显示 🌳 图标，体温 +2°C
3. 进入石屋（不透明方块），观察 HUD 显示 🏠 图标，体温 +8°C
4. 树荫下淋雨，观察是否仍被淋湿（CANOPY 不防雨）
5. 石屋内淋雨，观察是否不被淋湿（BUILDING 防雨）

预期：
- NONE：无修正，不防雨
- CANOPY：+2°C，不防雨
- BUILDING：+8°C，防雨

- [ ] **步骤 6：测试潮湿度蒸发冷却**

测试场景：
1. 淋雨 30 秒（wetness ≈ 0.3），观察体感温度是否降低约 2.4°C
2. 从水中出来（wetness = 1.0），观察体感温度是否降低 8°C
3. 观察潮湿度是否逐渐干燥，体感温度逐渐恢复

预期：
- wetness 0.3 → 体感 -2.4°C
- wetness 1.0 → 体感 -8°C
- 干燥后体感恢复正常

---

## 依赖关系

```
任务 1 (ShelterType 枚举)
  ↓
任务 2 (TemperatureSettings 配置)
  ↓
任务 3 (temperature.yml 配置)
  ↓
任务 4 (Season 枚举修改)
  ↓
任务 5 (遮蔽判定逻辑)
  ↓
任务 6 (水温计算)
  ↓
任务 7 (体感温度计算)
  ↓
任务 8 (compute() 重构) ← 依赖任务 5, 6, 7
  ↓
任务 9 (SeasonEngine 修改) ← 依赖任务 8
  ↓
任务 10 (HUD 更新) ← 依赖任务 1
  ↓
任务 11 (清理引用) ← 依赖任务 8, 10
  ↓
任务 12 (完整构建) ← 依赖所有前置任务
  ↓
任务 13 (手动测试) ← 依赖任务 12
```

---

## 风险与回滚

**主要风险：**
1. 体感温度计算公式可能导致极端温度（如沙漠 60°C），需要调整参数
2. 水温计算中的 findWaterSurfaceY() 在大型水域可能性能较差
3. 遮蔽细化判定可能与现有建筑不兼容

**回滚方案：**
```bash
git revert HEAD
```

所有变更在单个 commit 中，可完整回滚。

---

## 完成标准

- [ ] 所有 13 个任务完成
- [ ] 构建成功（BUILD SUCCESSFUL）
- [ ] 手动测试通过（水温、昼夜、季节、遮蔽、潮湿度）
- [ ] 代码审查通过
