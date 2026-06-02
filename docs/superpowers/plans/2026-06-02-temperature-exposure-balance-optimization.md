# 体温暴露压力与性能优化实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。实现 Bukkit/Paper/Folia 相关代码前必须使用项目技能 `folia-thread-safety`；写测试和实现功能前必须使用 `test-driven-development`。

**目标：** 在现有体温模型上加入冷热分离的极端暴露压力曲线，并用保守缓存降低热源扫描与遮蔽判定成本。

**架构：** 新增一个纯 Kotlin 暴露压力计算器，承载可测试的数学逻辑；`TemperatureEngine` 只负责把现有环境温度、绝缘、热源和水中状态转换为计算器输入。热源扫描缓存保留在 `PlayerEnvState`，遮蔽缓存继续由 `ShelterDetector` 管理并改为配置化。

**技术栈：** Kotlin 2.2.0、Gradle Kotlin DSL、TabooLib 6.3.0、Paper API 1.21.11、kotlin.test。

---

## 文件结构

### 创建

- `project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/temperature/TemperatureExposureCalculator.kt`  
  纯计算单元，不引用 Bukkit API。负责判断冷热暴露方向、计算严重度、更新冷热压力、计算压力倍率和保护分数。

- `project/core/src/test/kotlin/com/pixlehavencore/feature/realworld/temperature/TemperatureExposureCalculatorTest.kt`  
  `kotlin.test` 单元测试，覆盖裸奔压力增长、装备保护减速、冷热压力分离、水中增长倍率、非极端恢复和倍率插值。

### 修改

- `project/core/build.gradle.kts`  
  添加 `testImplementation(kotlin("test"))`。注意该文件当前可能已有未提交改动，实现前先查看现有 diff，只做最小合并。

- `project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/RealWorldModels.kt`  
  在 `PlayerEnvState` 新增冷热暴露压力和热源扫描缓存字段。

- `project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/temperature/TemperatureSettings.kt`  
  新增暴露压力配置与性能配置字段，在 `reload()` 中读取并 clamp。

- `project/core/src/main/resources/feature/realworld/temperature.yml`  
  新增 `exposure` 和 `performance` 配置段，保持中文注释。

- `project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/temperature/TemperatureEngine.kt`  
  接入暴露压力倍率；增加热源扫描刷新判断和缓存更新；死亡保护期恢复压力。

- `project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/temperature/ShelterDetector.kt`  
  删除固定 5 秒常量，改用 `TemperatureSettings.shelterCacheSeconds`。

---

## 任务 0：补提交已批准规格与本计划

**文件：**
- 添加：`docs/superpowers/specs/2026-06-02-temperature-exposure-balance-optimization-design.md`
- 添加：`docs/superpowers/plans/2026-06-02-temperature-exposure-balance-optimization.md`

- [ ] **步骤 1：检查工作区，确认只提交文档**

运行：

```bash
git status --short --untracked-files=all
```

预期：能看到已存在的代码改动，例如 `project/core/build.gradle.kts`、`TemperatureEngine.kt`、删除的 `EnchantmentRegistry.kt`；这些不是本任务要提交的内容。文档位于被 `.gitignore` 忽略的 `docs/` 下，普通 `git status` 可能不显示。

- [ ] **步骤 2：强制暂存规格与计划文档**

运行：

```bash
git add -f docs/superpowers/specs/2026-06-02-temperature-exposure-balance-optimization-design.md docs/superpowers/plans/2026-06-02-temperature-exposure-balance-optimization.md
```

预期：命令无输出，两个文档被 staged。

- [ ] **步骤 3：确认 staged 只包含文档**

运行：

```bash
git diff --cached --name-only
```

预期只包含：

```text
docs/superpowers/plans/2026-06-02-temperature-exposure-balance-optimization.md
docs/superpowers/specs/2026-06-02-temperature-exposure-balance-optimization-design.md
```

- [ ] **步骤 4：提交文档**

运行：

```bash
git commit -m "docs(temperature): 添加体温暴露压力优化设计"
```

预期：创建一个只包含两个文档的 commit。

---

## 任务 1：添加暴露压力计算器的失败测试

**文件：**
- 修改：`project/core/build.gradle.kts`
- 创建：`project/core/src/test/kotlin/com/pixlehavencore/feature/realworld/temperature/TemperatureExposureCalculatorTest.kt`

- [ ] **步骤 1：启用 kotlin.test 测试依赖**

在 `project/core/build.gradle.kts` 的 `dependencies` 块内加入一行。保持已有依赖不动：

```kotlin
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("net.milkbowl.vault:VaultUnlockedAPI:2.16")
    compileOnly("net.momirealms:craft-engine-core:26.5")
    compileOnly("net.momirealms:craft-engine-bukkit:26.5")
    compileOnly("com.zaxxer:HikariCP:4.0.3")
    compileOnly("top.maplex.arim:Arim:1.3.12")
    compileOnly(project(":project:bridge"))
    compileOnly(kotlin("stdlib"))
    compileOnly(fileTree(rootProject.file("libs")))
    testImplementation(kotlin("test"))
}
```

如果该文件在当前工作区已有其他未提交改动，保留它们，只添加 `testImplementation(kotlin("test"))`。

- [ ] **步骤 2：创建失败测试文件**

创建 `project/core/src/test/kotlin/com/pixlehavencore/feature/realworld/temperature/TemperatureExposureCalculatorTest.kt`：

```kotlin
package com.pixlehavencore.feature.realworld.temperature

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TemperatureExposureCalculatorTest {

    private val settings = TemperatureExposureSettings(
        coldThreshold = 10.0,
        heatThreshold = 8.0,
        baseGainPerSecond = 0.018,
        minGainPerSecond = 0.0025,
        recoveryPerSecond = 0.01,
        minExtremeMultiplier = 0.35,
        maxExtremeMultiplier = 1.0,
        waterGainMultiplier = 1.5,
        blockProtectionMax = 0.4,
        blockProtectionFullModifier = 20.0,
    )

    @Test
    fun `detects cold and heat exposure direction`() {
        assertEquals(
            ExposureDirection.COLD,
            TemperatureExposureCalculator.detectDirection(
                effectiveEnvTemp = 0.0,
                comfortMin = 15.0,
                comfortMax = 36.0,
                settings = settings,
            ),
        )
        assertEquals(
            ExposureDirection.HEAT,
            TemperatureExposureCalculator.detectDirection(
                effectiveEnvTemp = 50.0,
                comfortMin = 15.0,
                comfortMax = 36.0,
                settings = settings,
            ),
        )
        assertEquals(
            ExposureDirection.NONE,
            TemperatureExposureCalculator.detectDirection(
                effectiveEnvTemp = 25.0,
                comfortMin = 15.0,
                comfortMax = 36.0,
                settings = settings,
            ),
        )
    }

    @Test
    fun `naked cold exposure grows pressure faster than protected exposure`() {
        val naked = TemperatureExposureCalculator.updatePressures(
            coldPressure = 0.0,
            heatPressure = 0.0,
            direction = ExposureDirection.COLD,
            severity = 2.0,
            protectionScore = 0.0,
            isInWater = false,
            tickSeconds = 10.0,
            settings = settings,
        )
        val protected = TemperatureExposureCalculator.updatePressures(
            coldPressure = 0.0,
            heatPressure = 0.0,
            direction = ExposureDirection.COLD,
            severity = 2.0,
            protectionScore = 0.7,
            isInWater = false,
            tickSeconds = 10.0,
            settings = settings,
        )

        assertTrue(naked.cold > protected.cold)
        assertEquals(0.36, naked.cold, 0.0001)
        assertEquals(0.108, protected.cold, 0.0001)
    }

    @Test
    fun `cold exposure recovers heat pressure`() {
        val result = TemperatureExposureCalculator.updatePressures(
            coldPressure = 0.0,
            heatPressure = 0.5,
            direction = ExposureDirection.COLD,
            severity = 1.0,
            protectionScore = 0.0,
            isInWater = false,
            tickSeconds = 10.0,
            settings = settings,
        )

        assertTrue(result.cold > 0.0)
        assertEquals(0.4, result.heat, 0.0001)
    }

    @Test
    fun `non extreme environment recovers both pressures`() {
        val result = TemperatureExposureCalculator.updatePressures(
            coldPressure = 0.5,
            heatPressure = 0.25,
            direction = ExposureDirection.NONE,
            severity = 0.0,
            protectionScore = 0.0,
            isInWater = false,
            tickSeconds = 10.0,
            settings = settings,
        )

        assertEquals(0.4, result.cold, 0.0001)
        assertEquals(0.15, result.heat, 0.0001)
    }

    @Test
    fun `water exposure applies gain multiplier`() {
        val air = TemperatureExposureCalculator.updatePressures(
            coldPressure = 0.0,
            heatPressure = 0.0,
            direction = ExposureDirection.COLD,
            severity = 1.0,
            protectionScore = 0.0,
            isInWater = false,
            tickSeconds = 10.0,
            settings = settings,
        )
        val water = TemperatureExposureCalculator.updatePressures(
            coldPressure = 0.0,
            heatPressure = 0.0,
            direction = ExposureDirection.COLD,
            severity = 1.0,
            protectionScore = 0.0,
            isInWater = true,
            tickSeconds = 10.0,
            settings = settings,
        )

        assertEquals(air.cold * 1.5, water.cold, 0.0001)
    }

    @Test
    fun `multiplier interpolates between configured bounds`() {
        assertEquals(0.35, TemperatureExposureCalculator.multiplier(0.0, settings), 0.0001)
        assertEquals(0.675, TemperatureExposureCalculator.multiplier(0.5, settings), 0.0001)
        assertEquals(1.0, TemperatureExposureCalculator.multiplier(1.0, settings), 0.0001)
    }

    @Test
    fun `block protection only helps matching exposure direction`() {
        assertEquals(
            0.4,
            TemperatureExposureCalculator.blockProtection(
                direction = ExposureDirection.COLD,
                blockModifier = 25.0,
                settings = settings,
            ),
            0.0001,
        )
        assertEquals(
            0.0,
            TemperatureExposureCalculator.blockProtection(
                direction = ExposureDirection.COLD,
                blockModifier = -25.0,
                settings = settings,
            ),
            0.0001,
        )
        assertEquals(
            0.4,
            TemperatureExposureCalculator.blockProtection(
                direction = ExposureDirection.HEAT,
                blockModifier = -25.0,
                settings = settings,
            ),
            0.0001,
        )
    }
}
```

- [ ] **步骤 3：运行测试确认失败**

运行：

```bash
./gradlew :project:core:test --tests "com.pixlehavencore.feature.realworld.temperature.TemperatureExposureCalculatorTest"
```

预期：FAIL。失败原因应是 `TemperatureExposureSettings`、`ExposureDirection`、`TemperatureExposureCalculator` 未定义。

- [ ] **步骤 4：Commit 测试红灯**

如果团队允许提交红灯测试，运行：

```bash
git add project/core/build.gradle.kts project/core/src/test/kotlin/com/pixlehavencore/feature/realworld/temperature/TemperatureExposureCalculatorTest.kt
git commit -m "test(temperature): 添加暴露压力计算测试"
```

如果不允许红灯 commit，则跳过本步骤，在任务 2 绿灯后一并提交测试和实现。

---

## 任务 2：实现纯暴露压力计算器并让测试通过

**文件：**
- 创建：`project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/temperature/TemperatureExposureCalculator.kt`
- 测试：`project/core/src/test/kotlin/com/pixlehavencore/feature/realworld/temperature/TemperatureExposureCalculatorTest.kt`

- [ ] **步骤 1：创建最小实现**

创建 `TemperatureExposureCalculator.kt`：

```kotlin
package com.pixlehavencore.feature.realworld.temperature

data class TemperatureExposureSettings(
    val coldThreshold: Double,
    val heatThreshold: Double,
    val baseGainPerSecond: Double,
    val minGainPerSecond: Double,
    val recoveryPerSecond: Double,
    val minExtremeMultiplier: Double,
    val maxExtremeMultiplier: Double,
    val waterGainMultiplier: Double,
    val blockProtectionMax: Double,
    val blockProtectionFullModifier: Double,
)

data class TemperatureExposurePressures(
    val cold: Double,
    val heat: Double,
)

enum class ExposureDirection {
    NONE,
    COLD,
    HEAT,
}

object TemperatureExposureCalculator {

    fun detectDirection(
        effectiveEnvTemp: Double,
        comfortMin: Double,
        comfortMax: Double,
        settings: TemperatureExposureSettings,
    ): ExposureDirection {
        val coldBoundary = comfortMin - settings.coldThreshold
        val heatBoundary = comfortMax + settings.heatThreshold
        return when {
            effectiveEnvTemp < coldBoundary -> ExposureDirection.COLD
            effectiveEnvTemp > heatBoundary -> ExposureDirection.HEAT
            else -> ExposureDirection.NONE
        }
    }

    fun severity(
        effectiveEnvTemp: Double,
        comfortMin: Double,
        comfortMax: Double,
        direction: ExposureDirection,
        settings: TemperatureExposureSettings,
    ): Double {
        return when (direction) {
            ExposureDirection.COLD -> {
                val boundary = comfortMin - settings.coldThreshold
                val overshoot = (boundary - effectiveEnvTemp).coerceAtLeast(0.0)
                (1.0 + overshoot / settings.coldThreshold.coerceAtLeast(0.1)).coerceIn(1.0, 3.0)
            }
            ExposureDirection.HEAT -> {
                val boundary = comfortMax + settings.heatThreshold
                val overshoot = (effectiveEnvTemp - boundary).coerceAtLeast(0.0)
                (1.0 + overshoot / settings.heatThreshold.coerceAtLeast(0.1)).coerceIn(1.0, 3.0)
            }
            ExposureDirection.NONE -> 0.0
        }
    }

    fun updatePressures(
        coldPressure: Double,
        heatPressure: Double,
        direction: ExposureDirection,
        severity: Double,
        protectionScore: Double,
        isInWater: Boolean,
        tickSeconds: Double,
        settings: TemperatureExposureSettings,
    ): TemperatureExposurePressures {
        val dt = tickSeconds.coerceAtLeast(0.0)
        val recoveredCold = recover(coldPressure, dt, settings)
        val recoveredHeat = recover(heatPressure, dt, settings)

        return when (direction) {
            ExposureDirection.COLD -> TemperatureExposurePressures(
                cold = gain(coldPressure, severity, protectionScore, isInWater, dt, settings),
                heat = recoveredHeat,
            )
            ExposureDirection.HEAT -> TemperatureExposurePressures(
                cold = recoveredCold,
                heat = gain(heatPressure, severity, protectionScore, isInWater, dt, settings),
            )
            ExposureDirection.NONE -> TemperatureExposurePressures(
                cold = recoveredCold,
                heat = recoveredHeat,
            )
        }
    }

    fun multiplier(pressure: Double, settings: TemperatureExposureSettings): Double {
        val normalizedPressure = pressure.coerceIn(0.0, 1.0)
        val min = settings.minExtremeMultiplier
        val max = settings.maxExtremeMultiplier.coerceAtLeast(min)
        return min + (max - min) * normalizedPressure
    }

    fun blockProtection(
        direction: ExposureDirection,
        blockModifier: Double,
        settings: TemperatureExposureSettings,
    ): Double {
        val fullModifier = settings.blockProtectionFullModifier.coerceAtLeast(0.1)
        val rawProtection = when {
            direction == ExposureDirection.COLD && blockModifier > 0.0 -> blockModifier / fullModifier
            direction == ExposureDirection.HEAT && blockModifier < 0.0 -> -blockModifier / fullModifier
            else -> 0.0
        }
        return rawProtection.coerceIn(0.0, settings.blockProtectionMax)
    }

    fun activePressure(pressures: TemperatureExposurePressures, direction: ExposureDirection): Double {
        return when (direction) {
            ExposureDirection.COLD -> pressures.cold
            ExposureDirection.HEAT -> pressures.heat
            ExposureDirection.NONE -> 0.0
        }
    }

    private fun gain(
        current: Double,
        severity: Double,
        protectionScore: Double,
        isInWater: Boolean,
        tickSeconds: Double,
        settings: TemperatureExposureSettings,
    ): Double {
        val protectedGain = settings.baseGainPerSecond *
            severity.coerceAtLeast(0.0) *
            (1.0 - protectionScore.coerceIn(0.0, 1.0))
        val waterMultiplier = if (isInWater) settings.waterGainMultiplier else 1.0
        val gainPerSecond = (protectedGain * waterMultiplier).coerceAtLeast(settings.minGainPerSecond)
        return (current + gainPerSecond * tickSeconds).coerceIn(0.0, 1.0)
    }

    private fun recover(current: Double, tickSeconds: Double, settings: TemperatureExposureSettings): Double {
        return (current - settings.recoveryPerSecond * tickSeconds).coerceIn(0.0, 1.0)
    }
}
```

- [ ] **步骤 2：运行计算器测试验证通过**

运行：

```bash
./gradlew :project:core:test --tests "com.pixlehavencore.feature.realworld.temperature.TemperatureExposureCalculatorTest"
```

预期：PASS。

- [ ] **步骤 3：运行核心模块测试**

运行：

```bash
./gradlew :project:core:test
```

预期：BUILD SUCCESSFUL。

- [ ] **步骤 4：Commit 计算器实现**

如果任务 1 未提交红灯测试，本次一起提交测试依赖、测试和实现：

```bash
git add project/core/build.gradle.kts project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/temperature/TemperatureExposureCalculator.kt project/core/src/test/kotlin/com/pixlehavencore/feature/realworld/temperature/TemperatureExposureCalculatorTest.kt
git commit -m "feat(temperature): 添加极端暴露压力计算器"
```

---

## 任务 3：添加状态字段与配置读取

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/RealWorldModels.kt`
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/temperature/TemperatureSettings.kt`
- 修改：`project/core/src/main/resources/feature/realworld/temperature.yml`

- [ ] **步骤 1：在 PlayerEnvState 添加字段**

在 `RealWorldModels.kt` 的 `PlayerEnvState` 中，放在 `lastWaterTemp` 附近：

```kotlin
    var lastWaterTemp: Double = 20.0,
    var coldExposurePressure: Double = 0.0,
    var heatExposurePressure: Double = 0.0,
    var heatSourceCacheBlockX: Int = Int.MIN_VALUE,
    var heatSourceCacheBlockY: Int = Int.MIN_VALUE,
    var heatSourceCacheBlockZ: Int = Int.MIN_VALUE,
    var heatSourceCacheWorldName: String = "",
    var heatSourceCacheBiomeTemperature: Double = 20.0,
    var isClientRaining: Boolean = false,
```

- [ ] **步骤 2：在 TemperatureSettings 添加属性**

在 `TemperatureSettings.kt` 中 `waterExitBlendThreshold` 后添加：

```kotlin
    var exposureColdThreshold: Double = 10.0
        private set
    var exposureHeatThreshold: Double = 8.0
        private set
    var exposureBaseGainPerSecond: Double = 0.018
        private set
    var exposureMinGainPerSecond: Double = 0.0025
        private set
    var exposureRecoveryPerSecond: Double = 0.01
        private set
    var exposureMinExtremeMultiplier: Double = 0.35
        private set
    var exposureMaxExtremeMultiplier: Double = 1.0
        private set
    var exposureWaterGainMultiplier: Double = 1.5
        private set
    var exposureBlockProtectionMax: Double = 0.4
        private set
    var exposureBlockProtectionFullModifier: Double = 20.0
        private set
    var heatScanStationaryMultiplier: Int = 3
        private set
    var heatScanBiomeTempThreshold: Double = 2.0
        private set
    var shelterCacheSeconds: Int = 3
        private set
```

- [ ] **步骤 3：在 reload() 读取并 clamp 配置**

在 `TemperatureSettings.reload()` 末尾、`waterExitBlendThreshold` 读取后添加：

```kotlin
        exposureColdThreshold = config.getDouble("exposure.cold-threshold", 10.0).coerceAtLeast(0.0)
        exposureHeatThreshold = config.getDouble("exposure.heat-threshold", 8.0).coerceAtLeast(0.0)
        exposureBaseGainPerSecond = config.getDouble("exposure.base-gain-per-second", 0.018).coerceAtLeast(0.0)
        exposureMinGainPerSecond = config.getDouble("exposure.min-gain-per-second", 0.0025).coerceAtLeast(0.0)
        exposureRecoveryPerSecond = config.getDouble("exposure.recovery-per-second", 0.01).coerceAtLeast(0.0)
        exposureMinExtremeMultiplier = config.getDouble("exposure.min-extreme-multiplier", 0.35).coerceIn(0.0, 1.0)
        exposureMaxExtremeMultiplier = config.getDouble("exposure.max-extreme-multiplier", 1.0)
            .coerceIn(exposureMinExtremeMultiplier, 10.0)
        exposureWaterGainMultiplier = config.getDouble("exposure.water-gain-multiplier", 1.5).coerceAtLeast(1.0)
        exposureBlockProtectionMax = config.getDouble("exposure.block-protection-max", 0.4).coerceIn(0.0, 1.0)
        exposureBlockProtectionFullModifier = config.getDouble("exposure.block-protection-full-modifier", 20.0).coerceAtLeast(0.1)
        heatScanStationaryMultiplier = config.getInt("performance.heat-scan-stationary-multiplier", 3).coerceAtLeast(1)
        heatScanBiomeTempThreshold = config.getDouble("performance.heat-scan-biome-temp-threshold", 2.0).coerceAtLeast(0.0)
        shelterCacheSeconds = config.getInt("performance.shelter-cache-seconds", 3).coerceAtLeast(1)
```

- [ ] **步骤 4：在 temperature.yml 增加配置段**

在 `water` 配置段之后、`temperature-blocks` 之前插入：

```yaml
# =============================================================================
# 极端暴露压力配置
# =============================================================================
# 控制极端环境下体温恶化节奏：
#   - 裸奔：30~60 秒进入明显危险
#   - 有基础装备/遮蔽/热源：延长到 2~4 分钟

exposure:
  # 低于舒适下限多少度后开始累计低温压力
  cold-threshold: 10.0
  # 高于舒适上限多少度后开始累计高温压力
  heat-threshold: 8.0
  # 压力基础增长速度
  base-gain-per-second: 0.018
  # 即使保护充分，极端环境中仍保留的最小增长速度
  min-gain-per-second: 0.0025
  # 非极端或相反方向压力恢复速度
  recovery-per-second: 0.01
  # 压力为 0 时，极端环境体温变化倍率
  min-extreme-multiplier: 0.35
  # 压力为 1 时，极端环境体温变化倍率
  max-extreme-multiplier: 1.0
  # 水中压力增长倍率
  water-gain-multiplier: 1.5
  # 热源/冷源最多提供多少压力保护
  block-protection-max: 0.4
  # 多强的方块温度修正视为满额热源/冷源保护
  block-protection-full-modifier: 20.0

# =============================================================================
# 性能优化配置
# =============================================================================

performance:
  # 玩家原地不动时，热源扫描间隔倍率
  heat-scan-stationary-multiplier: 3
  # 生物群系基础温度变化超过此值时，强制刷新热源扫描
  heat-scan-biome-temp-threshold: 2.0
  # 遮蔽缓存秒数
  shelter-cache-seconds: 3
```

- [ ] **步骤 5：运行编译验证**

运行：

```bash
./gradlew :project:core:compileKotlin
```

预期：BUILD SUCCESSFUL。

- [ ] **步骤 6：Commit 状态与配置**

运行：

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/RealWorldModels.kt project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/temperature/TemperatureSettings.kt project/core/src/main/resources/feature/realworld/temperature.yml
git commit -m "feat(temperature): 添加暴露压力配置"
```

---

## 任务 4：接入暴露压力到 TemperatureEngine

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/temperature/TemperatureEngine.kt`
- 测试：`project/core/src/test/kotlin/com/pixlehavencore/feature/realworld/temperature/TemperatureExposureCalculatorTest.kt`

- [ ] **步骤 1：增加配置快照函数**

在 `TemperatureEngine` 内部添加：

```kotlin
    private fun exposureSettings(): TemperatureExposureSettings {
        return TemperatureExposureSettings(
            coldThreshold = TemperatureSettings.exposureColdThreshold,
            heatThreshold = TemperatureSettings.exposureHeatThreshold,
            baseGainPerSecond = TemperatureSettings.exposureBaseGainPerSecond,
            minGainPerSecond = TemperatureSettings.exposureMinGainPerSecond,
            recoveryPerSecond = TemperatureSettings.exposureRecoveryPerSecond,
            minExtremeMultiplier = TemperatureSettings.exposureMinExtremeMultiplier,
            maxExtremeMultiplier = TemperatureSettings.exposureMaxExtremeMultiplier,
            waterGainMultiplier = TemperatureSettings.exposureWaterGainMultiplier,
            blockProtectionMax = TemperatureSettings.exposureBlockProtectionMax,
            blockProtectionFullModifier = TemperatureSettings.exposureBlockProtectionFullModifier,
        )
    }
```

- [ ] **步骤 2：增加压力恢复函数**

在 `TemperatureEngine` 内部添加：

```kotlin
    private fun recoverExposurePressure(state: PlayerEnvState, tickIntervalSeconds: Int) {
        val pressures = TemperatureExposureCalculator.updatePressures(
            coldPressure = state.coldExposurePressure,
            heatPressure = state.heatExposurePressure,
            direction = ExposureDirection.NONE,
            severity = 0.0,
            protectionScore = 0.0,
            isInWater = false,
            tickSeconds = tickIntervalSeconds.coerceAtLeast(0).toDouble(),
            settings = exposureSettings(),
        )
        state.coldExposurePressure = pressures.cold
        state.heatExposurePressure = pressures.heat
    }
```

- [ ] **步骤 3：死亡保护期恢复压力**

在 `compute()` 的死亡保护分支中，`computeWetness(...)` 后、`return` 前加入：

```kotlin
            recoverExposurePressure(state, tickIntervalSeconds)
```

完整分支应保持类似：

```kotlin
        if (state.deathProtectionTimer > 0.0) {
            state.deathProtectionTimer -= tickIntervalSeconds.coerceAtLeast(0).toDouble()
            ShelterDetector.updateState(player, state, tickIntervalSeconds)
            computeWetness(player, state, global, tickIntervalSeconds)
            recoverExposurePressure(state, tickIntervalSeconds)
            return
        }
```

- [ ] **步骤 4：添加保护分数函数**

在 `TemperatureEngine` 内部添加：

```kotlin
    private fun getExposureProtectionScore(
        totalInsulation: Double,
        blockRadiationModifier: Double,
        direction: ExposureDirection,
    ): Double {
        val armorAndShelterProtection = if (TemperatureSettings.armorInsulationMax > 0.0) {
            totalInsulation / TemperatureSettings.armorInsulationMax
        } else {
            0.0
        }
        val blockProtection = TemperatureExposureCalculator.blockProtection(
            direction = direction,
            blockModifier = blockRadiationModifier,
            settings = exposureSettings(),
        )
        return (armorAndShelterProtection + blockProtection).coerceIn(0.0, 1.0)
    }
```

- [ ] **步骤 5：添加更新并获取倍率函数**

在 `TemperatureEngine` 内部添加：

```kotlin
    private fun updateAndGetExposureMultiplier(
        state: PlayerEnvState,
        effectiveEnvTemp: Double,
        totalInsulation: Double,
        blockRadiationModifier: Double,
        isInWater: Boolean,
        tickIntervalSeconds: Int,
    ): Double {
        val settings = exposureSettings()
        val direction = TemperatureExposureCalculator.detectDirection(
            effectiveEnvTemp = effectiveEnvTemp,
            comfortMin = TemperatureSettings.comfortMin,
            comfortMax = TemperatureSettings.comfortMax,
            settings = settings,
        )
        val severity = TemperatureExposureCalculator.severity(
            effectiveEnvTemp = effectiveEnvTemp,
            comfortMin = TemperatureSettings.comfortMin,
            comfortMax = TemperatureSettings.comfortMax,
            direction = direction,
            settings = settings,
        )
        val protectionScore = getExposureProtectionScore(
            totalInsulation = totalInsulation,
            blockRadiationModifier = blockRadiationModifier,
            direction = direction,
        )
        val pressures = TemperatureExposureCalculator.updatePressures(
            coldPressure = state.coldExposurePressure,
            heatPressure = state.heatExposurePressure,
            direction = direction,
            severity = severity,
            protectionScore = protectionScore,
            isInWater = isInWater,
            tickSeconds = tickIntervalSeconds.coerceAtLeast(0).toDouble(),
            settings = settings,
        )
        state.coldExposurePressure = pressures.cold
        state.heatExposurePressure = pressures.heat

        if (direction == ExposureDirection.NONE) {
            return 1.0
        }
        val activePressure = TemperatureExposureCalculator.activePressure(pressures, direction)
        return TemperatureExposureCalculator.multiplier(activePressure, settings)
    }
```

- [ ] **步骤 6：在最终 change 前接入倍率**

找到当前代码：

```kotlin
        val rawChange = envDelta * (1.0 - Math.exp(-absorptionRate * Math.abs(envDelta)))
        val change = rawChange.coerceIn(-maxChange, maxChange)
```

替换为：

```kotlin
        val rawChange = envDelta * (1.0 - Math.exp(-absorptionRate * Math.abs(envDelta)))
        val exposureMultiplier = updateAndGetExposureMultiplier(
            state = state,
            effectiveEnvTemp = effectiveEnvTemp,
            totalInsulation = totalInsulation,
            blockRadiationModifier = blockRadiationModifier,
            isInWater = isInWater,
            tickIntervalSeconds = tickIntervalSeconds,
        )
        val change = (rawChange * exposureMultiplier).coerceIn(-maxChange, maxChange)
```

- [ ] **步骤 7：运行测试与编译**

运行：

```bash
./gradlew :project:core:test --tests "com.pixlehavencore.feature.realworld.temperature.TemperatureExposureCalculatorTest"
./gradlew :project:core:compileKotlin
```

预期：两条命令均 BUILD SUCCESSFUL。

- [ ] **步骤 8：Commit 引擎接入**

运行：

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/temperature/TemperatureEngine.kt
git commit -m "feat(temperature): 接入极端暴露压力曲线"
```

---

## 任务 5：实现热源扫描缓存优化

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/temperature/TemperatureEngine.kt`

- [ ] **步骤 1：添加扫描判断数据类**

在 `TemperatureEngine` 内部底部添加：

```kotlin
    private data class HeatSourceScanDecision(
        val shouldRefresh: Boolean,
        val isStationary: Boolean,
    )
```

- [ ] **步骤 2：添加扫描刷新判断函数**

在 `TemperatureEngine` 内部添加：

```kotlin
    private fun shouldRefreshHeatSourceScan(
        player: Player,
        state: PlayerEnvState,
        biomeBaseTemperature: Double,
    ): HeatSourceScanDecision {
        val location = player.location
        val worldName = location.world?.name.orEmpty()
        val movedToDifferentBlock =
            state.heatSourceCacheBlockX != location.blockX ||
                state.heatSourceCacheBlockY != location.blockY ||
                state.heatSourceCacheBlockZ != location.blockZ
        val changedWorld = state.heatSourceCacheWorldName != worldName
        val changedBiomeTemperature = Math.abs(
            state.heatSourceCacheBiomeTemperature - biomeBaseTemperature,
        ) > TemperatureSettings.heatScanBiomeTempThreshold
        val shouldRefresh = state.heatSourceScanTimer <= 0.0 ||
            movedToDifferentBlock ||
            changedWorld ||
            changedBiomeTemperature

        return HeatSourceScanDecision(
            shouldRefresh = shouldRefresh,
            isStationary = !movedToDifferentBlock && !changedWorld,
        )
    }
```

- [ ] **步骤 3：添加缓存更新函数**

在 `TemperatureEngine` 内部添加：

```kotlin
    private fun updateHeatSourceScanCache(
        player: Player,
        state: PlayerEnvState,
        biomeBaseTemperature: Double,
    ) {
        val location = player.location
        state.heatSourceCacheBlockX = location.blockX
        state.heatSourceCacheBlockY = location.blockY
        state.heatSourceCacheBlockZ = location.blockZ
        state.heatSourceCacheWorldName = location.world?.name.orEmpty()
        state.heatSourceCacheBiomeTemperature = biomeBaseTemperature
    }
```

- [ ] **步骤 4：替换现有热源扫描分支**

找到当前代码：

```kotlin
        state.heatSourceScanTimer -= tickIntervalSeconds.coerceAtLeast(0)
        if (state.heatSourceScanTimer <= 0.0) {
            val scanResult = BlockRadiationScanner.scan(player, biomeBaseTemperature)
            state.nearHeatSource = scanResult.first
            state.temperatureBlockModifier = scanResult.second
            val interval = TemperatureSettings.heatSourceScanIntervalSeconds.toDouble()
            state.heatSourceScanTimer = interval
        }
```

替换为：

```kotlin
        state.heatSourceScanTimer -= tickIntervalSeconds.coerceAtLeast(0)
        val scanDecision = shouldRefreshHeatSourceScan(player, state, biomeBaseTemperature)
        if (scanDecision.shouldRefresh) {
            val scanResult = BlockRadiationScanner.scan(player, biomeBaseTemperature)
            state.nearHeatSource = scanResult.first
            state.temperatureBlockModifier = scanResult.second
            updateHeatSourceScanCache(player, state, biomeBaseTemperature)

            val baseInterval = TemperatureSettings.heatSourceScanIntervalSeconds.toDouble()
            val intervalMultiplier = if (scanDecision.isStationary) {
                TemperatureSettings.heatScanStationaryMultiplier.toDouble()
            } else {
                1.0
            }
            state.heatSourceScanTimer = baseInterval * intervalMultiplier
        }
```

- [ ] **步骤 5：运行编译验证**

运行：

```bash
./gradlew :project:core:compileKotlin
```

预期：BUILD SUCCESSFUL。

- [ ] **步骤 6：Commit 热源缓存优化**

运行：

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/temperature/TemperatureEngine.kt
git commit -m "perf(temperature): 缓存原地玩家热源扫描"
```

---

## 任务 6：配置化遮蔽缓存时间

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/temperature/ShelterDetector.kt`

- [ ] **步骤 1：删除固定缓存常量**

删除：

```kotlin
    private const val SHELTER_CACHE_SECONDS = 5.0
```

- [ ] **步骤 2：使用配置值设置缓存时间**

找到：

```kotlin
        state.shelterCacheTimer = SHELTER_CACHE_SECONDS
```

替换为：

```kotlin
        state.shelterCacheTimer = TemperatureSettings.shelterCacheSeconds.toDouble()
```

- [ ] **步骤 3：更新 KDoc 注释**

将文件顶部注释中的：

```kotlin
 * 内置 5 秒缓存，避免每个 tick 重复扫描。
```

替换为：

```kotlin
 * 使用可配置缓存时间，避免每个 tick 重复扫描。
```

- [ ] **步骤 4：运行编译验证**

运行：

```bash
./gradlew :project:core:compileKotlin
```

预期：BUILD SUCCESSFUL。

- [ ] **步骤 5：Commit 遮蔽缓存配置化**

运行：

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/temperature/ShelterDetector.kt
git commit -m "perf(temperature): 配置化遮蔽缓存时间"
```

---

## 任务 7：完整验证与代码审查

**文件：**
- 验证：所有本计划修改文件

- [ ] **步骤 1：运行完整构建**

运行：

```bash
./gradlew build
```

预期：BUILD SUCCESSFUL。允许 Gradle deprecation warning，但不允许编译或测试失败。

- [ ] **步骤 2：检查 diff 范围**

运行：

```bash
git status --short
git diff --stat
```

预期：工作区中只剩用户原本未提交的无关改动，或没有未提交改动。若出现本计划文件未提交，返回对应任务补 commit。

- [ ] **步骤 3：人工场景验证清单**

在测试服务器按以下场景观察体温、冷热压力和热源扫描行为。若没有现成调试输出，可以临时在本地分支加日志验证，验证后删除日志再提交。

```text
冬季雪原午夜裸奔：coldExposurePressure 逐步上升，不瞬间满值。
冬季雪原铁套：coldExposurePressure 增长慢于裸奔。
冬季雪原建筑 + 热源：低温压力增长大幅降低，非极端后恢复。
夏季沙漠正午裸奔：heatExposurePressure 逐步上升。
夏季沙漠高绝缘装备：heatExposurePressure 增长变慢但不免疫。
冰水浸泡：水中压力增长快于空气，但平滑。
死亡保护期：冷热压力不增长并逐步恢复。
玩家原地靠近热源：热源扫描间隔延长。
玩家移动到新方块：热源扫描及时刷新。
进入/离开建筑：遮蔽状态在缓存期内更新。
```

- [ ] **步骤 4：调用代码审查技能或代理**

调用 `requesting-code-review` 技能，或使用 `code-reviewer` 代理审查本计划实现。审查重点：

```text
1. 暴露压力是否只影响极端环境下的变化速度。
2. 冷热压力是否互不污染。
3. 热源扫描缓存是否会导致移动后结果过期。
4. 是否新增异步 Bukkit 世界访问。
5. compute() 是否仍可读，小函数边界是否清晰。
```

- [ ] **步骤 5：修复审查发现的问题并重新构建**

如果审查发现问题，修复后运行：

```bash
./gradlew build
```

预期：BUILD SUCCESSFUL。

- [ ] **步骤 6：最终提交修复**

如果步骤 5 产生修复改动，运行：

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/realworld project/core/src/main/resources/feature/realworld/temperature.yml project/core/src/test/kotlin/com/pixlehavencore/feature/realworld/temperature/TemperatureExposureCalculatorTest.kt project/core/build.gradle.kts
git commit -m "fix(temperature): 修正暴露压力优化审查问题"
```

如果没有修复改动，本步骤无需提交。

---

## 自检结果

### 规格覆盖度

- 冷热分离暴露压力：任务 1、2、3、4 覆盖。
- 裸奔 30~60 秒、基础准备 2~4 分钟：任务 1、2 的默认参数与测试覆盖，任务 7 人工场景验证。
- 暴露压力只影响最终变化速度：任务 4 覆盖。
- 水中压力增长但不瞬间满值：任务 1、2、4 覆盖。
- 热源扫描缓存：任务 3、5 覆盖。
- 遮蔽缓存配置化：任务 3、6 覆盖。
- 死亡保护期恢复压力：任务 4 覆盖。
- Folia/Canvas 线程安全：任务 5 不新增异步访问，任务 7 审查验证。
- 构建验证与人工验收：任务 7 覆盖。

### 占位符扫描

计划未使用“待定”“后续实现”“添加适当处理”等占位表达。每个代码变更步骤均给出文件、代码片段、命令和预期结果。

### 类型一致性

计划中统一使用：

- `TemperatureExposureSettings`
- `TemperatureExposurePressures`
- `ExposureDirection`
- `TemperatureExposureCalculator`
- `coldExposurePressure`
- `heatExposurePressure`
- `heatScanStationaryMultiplier`
- `shelterCacheSeconds`

这些名称在任务 1 测试、任务 2 实现、任务 3 配置和任务 4 接入中保持一致。
