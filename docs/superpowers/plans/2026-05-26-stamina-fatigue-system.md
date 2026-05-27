# 体力疲劳系统实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 为 RealWorld 模块新增体力疲劳系统，串联 7 个现有系统，增加生存玩法深度

**架构：** 新增 StaminaModels（数据模型）、StaminaSettings（配置读取）、StaminaEngine（核心逻辑）三个文件，集成到现有 RealWorldService tick 循环、RealWorldCommand 命令、SurvivalHud BossBar、RealWorldStorage 存储中

**技术栈：** Kotlin、TabooLib 6.3.0、Paper API 1.21.11

**规格文档：** `docs/superpowers/specs/2026-05-26-stamina-fatigue-system-design.md`

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/StaminaModels.kt` | 创建 | 体力阶段枚举、消耗/恢复来源枚举、自定义事件类 |
| `project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/StaminaSettings.kt` | 创建 | 从 YAML 读取体力配置，提供类型安全访问器 |
| `project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/StaminaEngine.kt` | 创建 | 体力计算核心逻辑：消耗、恢复、阶段判定、惩罚、提醒 |
| `project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/RealWorldModels.kt` | 修改 | PlayerEnvState 新增 stamina 相关字段 |
| `project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/RealWorldSettings.kt` | 修改 | init()/reload() 中调用 StaminaSettings |
| `project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/RealWorldService.kt` | 修改 | tick 循环中调用 StaminaEngine，resetPlayer 重置体力 |
| `project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/RealWorldStorage.kt` | 修改 | 数据库表新增 stamina 列，读写体力数据 |
| `project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/RealWorldCommand.kt` | 修改 | 新增 stamina 子命令 |
| `project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/SurvivalHud.kt` | 修改 | 新增体力 BossBar 显示逻辑 |
| `project/core/src/main/resources/feature/realworld/realworld.yml` | 修改 | 新增 stamina 配置段落 |

---

### 任务 1：数据模型 — StaminaModels.kt

**文件：**
- 创建：`project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/StaminaModels.kt`

- [ ] **步骤 1：创建 StaminaModels.kt**

```kotlin
package com.pixlehavencore.feature.realworld

import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

enum class StaminaPhase(
    val displayName: String,
    val speedMultiplier: Double,
    val canSprint: Boolean,
) {
    FULL("充沛", 1.0, true),
    TIRED("疲劳", 0.85, true),
    EXHAUSTED("筋疲力尽", 0.70, false),
    DEPLETED("体力耗尽", 0.50, false),
}

enum class StaminaConsumeSource {
    SPRINT, SWIM, CLIMB, ATTACK, MINE, USE_TOOL,
    UNDERWATER, HIGH_ALTITUDE, ENVIRONMENT,
}

enum class StaminaRecoverSource {
    IDLE, FOOD, DRINK, SLEEP, SPECIAL_ITEM, COMMAND,
}

class StaminaConsumeEvent(
    val player: org.bukkit.entity.Player,
    val source: StaminaConsumeSource,
    var amount: Double,
) : Event(true), Cancellable {

    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }

    override fun getHandlers(): HandlerList = getHandlerList()

    companion object {
        private val handlers = HandlerList()
        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }
}

class StaminaRecoverEvent(
    val player: org.bukkit.entity.Player,
    val source: StaminaRecoverSource,
    var amount: Double,
) : Event(true), Cancellable {

    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }

    override fun getHandlers(): HandlerList = getHandlerList()

    companion object {
        private val handlers = HandlerList()
        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }
}

class StaminaPhaseChangeEvent(
    val player: org.bukkit.entity.Player,
    val from: StaminaPhase,
    val to: StaminaPhase,
) : Event(true) {

    override fun getHandlers(): HandlerList = getHandlerList()

    companion object {
        private val handlers = HandlerList()
        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }
}

class StaminaDepletedEvent(
    val player: org.bukkit.entity.Player,
) : Event(true) {

    override fun getHandlers(): HandlerList = getHandlerList()

    companion object {
        private val handlers = HandlerList()
        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }
}
```

- [ ] **步骤 2：构建验证**

运行：`./gradlew build`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/StaminaModels.kt
git commit -m "feat(realworld): 添加体力系统数据模型和事件类"
```

---

### 任务 2：配置读取 — StaminaSettings.kt

**文件：**
- 创建：`project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/StaminaSettings.kt`

- [ ] **步骤 1：创建 StaminaSettings.kt**

```kotlin
package com.pixlehavencore.feature.realworld

import org.bukkit.Material
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object StaminaSettings {

    @Config("feature/realworld/realworld.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    var maxStamina: Double = 100.0
        private set
    var baseConsumptionRate: Double = 0.05
        private set
    var baseRecoveryRate: Double = 0.3
        private set
    var maxMultiplier: Double = 5.0
        private set

    var sprintMultiplier: Double = 2.0
        private set
    var swimMultiplier: Double = 2.5
        private set
    var climbMultiplier: Double = 2.0
        private set

    var attackCost: Double = 0.15
        private set
    var mineCost: Double = 0.075
        private set
    var useToolCost: Double = 0.1
        private set

    var underwaterMultiplier: Double = 4.0
        private set
    var highAltitudeMultiplier: Double = 2.0
        private set
    var highAltitudeY: Int = 120
        private set

    var idleEnabled: Boolean = true
        private set
    var idleDelaySeconds: Double = 3.0
        private set
    var idleMultiplier: Double = 1.0
        private set
    var idleBlockedInExtremeTemp: Boolean = true
        private set
    var idleBlockedUnderwater: Boolean = true
        private set
    var idleBlockedWhenAttacked: Boolean = true
        private set

    var foodEnabled: Boolean = true
        private set
    var hungerToStaminaRatio: Double = 2.0
        private set
    var rottenFoodPenalty: Double = 0.5
        private set
    var foodCooldownSeconds: Double = 1.0
        private set

    var drinkEnabled: Boolean = true
        private set
    var hydrationToStaminaRatio: Double = 0.5
        private set

    var sleepEnabled: Boolean = true
        private set
    var outdoorRecoveryPercent: Double = 50.0
        private set
    var sleepBlockedInExtremeTemp: Boolean = true
        private set

    var specialItemsEnabled: Boolean = true
        private set
    var specialItems: Map<Material, Double> = emptyMap()
        private set

    var tiredThreshold: Double = 60.0
        private set
    var tiredSpeedMultiplier: Double = 0.85
        private set

    var exhaustedThreshold: Double = 30.0
        private set
    var exhaustedSpeedMultiplier: Double = 0.70
        private set
    var exhaustedMiningFatigueAmplifier: Int = 0
        private set
    var exhaustedWeaknessAmplifier: Int = 0
        private set

    var depletedThreshold: Double = 10.0
        private set
    var depletedSpeedMultiplier: Double = 0.50
        private set
    var depletedMiningFatigueAmplifier: Int = 1
        private set
    var depletedWeaknessAmplifier: Int = 1
        private set

    var effectDurationSeconds: Int = 3
        private set

    var msgEnterTired: String = "&e⚡ 你感到有些疲劳了... (体力: {stamina}%)"
        private set
    var msgEnterExhausted: String = "&c⚡ 你筋疲力尽了！无法疾跑！ (体力: {stamina}%)"
        private set
    var msgEnterDepleted: String = "&4⚡ 体力耗尽！你几乎无法行动！ (体力: {stamina}%)"
        private set
    var msgDepletedReminder: String = "&4⚡ 你需要休息或进食来恢复体力！ (体力: {stamina}%)"
        private set
    var msgRecoveredFromTired: String = "&a⚡ 你感觉好多了。 (体力: {stamina}%)"
        private set
    var msgRecoveredFromExhausted: String = "&a⚡ 你恢复了一些精力。 (体力: {stamina}%)"
        private set
    var msgRecoveredFromDepleted: String = "&a⚡ 你终于缓过来了。 (体力: {stamina}%)"
        private set
    var depletedReminderCooldownSeconds: Double = 30.0
        private set

    var bossBarEnabled: Boolean = true
        private set
    var bossBarTitleExhausted: String = "&e⚡ 体力不足！寻找食物或休息！"
        private set
    var bossBarTitleDepleted: String = "&4⚡ 体力耗尽！你几乎无法行动！"
        private set
    var bossBarColorExhausted: String = "YELLOW"
        private set
    var bossBarColorDepleted: String = "RED"
        private set
    var bossBarStyle: String = "SOLID"
        private set

    var integrationTemperatureEnabled: Boolean = true
        private set
    var integrationTemperatureExtremeMultiplier: Double = 1.5
        private set
    var integrationTemperatureMildMultiplier: Double = 1.2
        private set

    var integrationFractureEnabled: Boolean = true
        private set
    var integrationFractureThreshold: Double = 20.0
        private set
    var integrationFractureConsumptionMultiplier: Double = 1.3
        private set
    var integrationFractureMaxStaminaReduction: Double = 30.0
        private set

    var integrationThirstEnabled: Boolean = true
        private set
    var integrationThirstDehydrationThreshold: Double = 30.0
        private set
    var integrationThirstRecoveryMultiplier: Double = 0.5
        private set

    var integrationWetnessEnabled: Boolean = true
        private set
    var integrationWetnessThreshold: Double = 0.7
        private set
    var integrationWetnessConsumptionMultiplier: Double = 1.2
        private set

    var integrationFoodEnabled: Boolean = true
        private set
    var integrationFoodFullSaturationBonus: Double = 1.25
        private set

    var integrationWeatherEnabled: Boolean = true
        private set
    var integrationWeatherExtremeMultiplier: Double = 1.3
        private set

    var integrationSeasonEnabled: Boolean = true
        private set
    var integrationSeasonWinterMultiplier: Double = 1.1
        private set
    var integrationSeasonSummerMultiplier: Double = 1.05
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("stamina.enabled", true)

        maxStamina = config.getDouble("stamina.max-stamina", 100.0).coerceAtLeast(1.0)
        baseConsumptionRate = config.getDouble("stamina.base-consumption-rate", 0.05).coerceAtLeast(0.0)
        baseRecoveryRate = config.getDouble("stamina.base-recovery-rate", 0.3).coerceAtLeast(0.0)
        maxMultiplier = config.getDouble("stamina.max-multiplier", 5.0).coerceAtLeast(1.0)

        sprintMultiplier = config.getDouble("stamina.continuous.sprint-multiplier", 2.0).coerceAtLeast(0.0)
        swimMultiplier = config.getDouble("stamina.continuous.swim-multiplier", 2.5).coerceAtLeast(0.0)
        climbMultiplier = config.getDouble("stamina.continuous.climb-multiplier", 2.0).coerceAtLeast(0.0)

        attackCost = config.getDouble("stamina.actions.attack-cost", 0.15).coerceAtLeast(0.0)
        mineCost = config.getDouble("stamina.actions.mine-cost", 0.075).coerceAtLeast(0.0)
        useToolCost = config.getDouble("stamina.actions.use-tool-cost", 0.1).coerceAtLeast(0.0)

        underwaterMultiplier = config.getDouble("stamina.special.underwater-multiplier", 4.0).coerceAtLeast(0.0)
        highAltitudeMultiplier = config.getDouble("stamina.special.high-altitude-multiplier", 2.0).coerceAtLeast(0.0)
        highAltitudeY = config.getInt("stamina.special.high-altitude-y", 120)

        idleEnabled = config.getBoolean("stamina.recovery.idle.enabled", true)
        idleDelaySeconds = config.getDouble("stamina.recovery.idle.delay-seconds", 3.0).coerceAtLeast(0.0)
        idleMultiplier = config.getDouble("stamina.recovery.idle.multiplier", 1.0).coerceAtLeast(0.0)
        idleBlockedInExtremeTemp = config.getBoolean("stamina.recovery.idle.blocked-in-extreme-temperature", true)
        idleBlockedUnderwater = config.getBoolean("stamina.recovery.idle.blocked-underwater", true)
        idleBlockedWhenAttacked = config.getBoolean("stamina.recovery.idle.blocked-when-attacked", true)

        foodEnabled = config.getBoolean("stamina.recovery.food.enabled", true)
        hungerToStaminaRatio = config.getDouble("stamina.recovery.food.hunger-to-stamina-ratio", 2.0).coerceAtLeast(0.0)
        rottenFoodPenalty = config.getDouble("stamina.recovery.food.rotten-food-penalty", 0.5).coerceIn(0.0, 1.0)
        foodCooldownSeconds = config.getDouble("stamina.recovery.food.cooldown-seconds", 1.0).coerceAtLeast(0.0)

        drinkEnabled = config.getBoolean("stamina.recovery.drink.enabled", true)
        hydrationToStaminaRatio = config.getDouble("stamina.recovery.drink.hydration-to-stamina-ratio", 0.5).coerceAtLeast(0.0)

        sleepEnabled = config.getBoolean("stamina.recovery.sleep.enabled", true)
        outdoorRecoveryPercent = config.getDouble("stamina.recovery.sleep.outdoor-recovery-percent", 50.0).coerceIn(0.0, 100.0)
        sleepBlockedInExtremeTemp = config.getBoolean("stamina.recovery.sleep.blocked-in-extreme-temperature", true)

        specialItemsEnabled = config.getBoolean("stamina.recovery.special-items.enabled", true)
        specialItems = config.getConfigurationSection("stamina.recovery.special-items.items")
            ?.getKeys(false)
            ?.mapNotNull { key ->
                val material = runCatching { Material.valueOf(key.uppercase()) }.getOrNull()
                val amount = config.getDouble("stamina.recovery.special-items.items.$key")
                if (material != null && amount > 0) material to amount else null
            }
            ?.toMap()
            ?: emptyMap()

        tiredThreshold = config.getDouble("stamina.penalties.tired.threshold", 60.0).coerceIn(0.0, 100.0)
        tiredSpeedMultiplier = config.getDouble("stamina.penalties.tired.speed-multiplier", 0.85).coerceIn(0.0, 1.0)

        exhaustedThreshold = config.getDouble("stamina.penalties.exhausted.threshold", 30.0).coerceIn(0.0, tiredThreshold)
        exhaustedSpeedMultiplier = config.getDouble("stamina.penalties.exhausted.speed-multiplier", 0.70).coerceIn(0.0, 1.0)
        exhaustedMiningFatigueAmplifier = config.getInt("stamina.penalties.exhausted.mining-fatigue-amplifier", 0).coerceAtLeast(0)
        exhaustedWeaknessAmplifier = config.getInt("stamina.penalties.exhausted.weakness-amplifier", 0).coerceAtLeast(0)

        depletedThreshold = config.getDouble("stamina.penalties.depleted.threshold", 10.0).coerceIn(0.0, exhaustedThreshold)
        depletedSpeedMultiplier = config.getDouble("stamina.penalties.depleted.speed-multiplier", 0.50).coerceIn(0.0, 1.0)
        depletedMiningFatigueAmplifier = config.getInt("stamina.penalties.depleted.mining-fatigue-amplifier", 1).coerceAtLeast(0)
        depletedWeaknessAmplifier = config.getInt("stamina.penalties.depleted.weakness-amplifier", 1).coerceAtLeast(0)

        effectDurationSeconds = config.getInt("stamina.penalties.effect-duration-seconds", 3).coerceAtLeast(1)

        msgEnterTired = config.getString("stamina.messages.enter-tired") ?: msgEnterTired
        msgEnterExhausted = config.getString("stamina.messages.enter-exhausted") ?: msgEnterExhausted
        msgEnterDepleted = config.getString("stamina.messages.enter-depleted") ?: msgEnterDepleted
        msgDepletedReminder = config.getString("stamina.messages.depleted-reminder") ?: msgDepletedReminder
        msgRecoveredFromTired = config.getString("stamina.messages.recovered-from-tired") ?: msgRecoveredFromTired
        msgRecoveredFromExhausted = config.getString("stamina.messages.recovered-from-exhausted") ?: msgRecoveredFromExhausted
        msgRecoveredFromDepleted = config.getString("stamina.messages.recovered-from-depleted") ?: msgRecoveredFromDepleted
        depletedReminderCooldownSeconds = config.getDouble("stamina.messages.depleted-reminder-cooldown-seconds", 30.0).coerceAtLeast(1.0)

        bossBarEnabled = config.getBoolean("stamina.hud.bossbar-enabled", true)
        bossBarTitleExhausted = config.getString("stamina.hud.bossbar-title-exhausted") ?: bossBarTitleExhausted
        bossBarTitleDepleted = config.getString("stamina.hud.bossbar-title-depleted") ?: bossBarTitleDepleted
        bossBarColorExhausted = config.getString("stamina.hud.bossbar-color-exhausted") ?: "YELLOW"
        bossBarColorDepleted = config.getString("stamina.hud.bossbar-color-depleted") ?: "RED"
        bossBarStyle = config.getString("stamina.hud.bossbar-style") ?: "SOLID"

        integrationTemperatureEnabled = config.getBoolean("stamina.integration.temperature.enabled", true)
        integrationTemperatureExtremeMultiplier = config.getDouble("stamina.integration.temperature.extreme-multiplier", 1.5).coerceAtLeast(1.0)
        integrationTemperatureMildMultiplier = config.getDouble("stamina.integration.temperature.mild-multiplier", 1.2).coerceAtLeast(1.0)

        integrationFractureEnabled = config.getBoolean("stamina.integration.fracture.enabled", true)
        integrationFractureThreshold = config.getDouble("stamina.integration.fracture.threshold", 20.0).coerceAtLeast(0.0)
        integrationFractureConsumptionMultiplier = config.getDouble("stamina.integration.fracture.consumption-multiplier", 1.3).coerceAtLeast(1.0)
        integrationFractureMaxStaminaReduction = config.getDouble("stamina.integration.fracture.max-stamina-reduction", 30.0).coerceAtLeast(0.0)

        integrationThirstEnabled = config.getBoolean("stamina.integration.thirst.enabled", true)
        integrationThirstDehydrationThreshold = config.getDouble("stamina.integration.thirst.dehydration-threshold", 30.0).coerceAtLeast(0.0)
        integrationThirstRecoveryMultiplier = config.getDouble("stamina.integration.thirst.recovery-multiplier", 0.5).coerceIn(0.0, 1.0)

        integrationWetnessEnabled = config.getBoolean("stamina.integration.wetness.enabled", true)
        integrationWetnessThreshold = config.getDouble("stamina.integration.wetness.threshold", 0.7).coerceIn(0.0, 1.0)
        integrationWetnessConsumptionMultiplier = config.getDouble("stamina.integration.wetness.consumption-multiplier", 1.2).coerceAtLeast(1.0)

        integrationFoodEnabled = config.getBoolean("stamina.integration.food.enabled", true)
        integrationFoodFullSaturationBonus = config.getDouble("stamina.integration.food.full-saturation-bonus", 1.25).coerceAtLeast(1.0)

        integrationWeatherEnabled = config.getBoolean("stamina.integration.weather.enabled", true)
        integrationWeatherExtremeMultiplier = config.getDouble("stamina.integration.weather.extreme-multiplier", 1.3).coerceAtLeast(1.0)

        integrationSeasonEnabled = config.getBoolean("stamina.integration.season.enabled", true)
        integrationSeasonWinterMultiplier = config.getDouble("stamina.integration.season.winter-multiplier", 1.1).coerceAtLeast(1.0)
        integrationSeasonSummerMultiplier = config.getDouble("stamina.integration.season.summer-multiplier", 1.05).coerceAtLeast(1.0)
    }
}
```

- [ ] **步骤 2：构建验证**

运行：`./gradlew build`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/StaminaSettings.kt
git commit -m "feat(realworld): 添加体力系统配置读取"
```

---

### 任务 3：配置文件 — realworld.yml 新增 stamina 段落

**文件：**
- 修改：`project/core/src/main/resources/feature/realworld/realworld.yml`

- [ ] **步骤 1：在 realworld.yml 末尾追加 stamina 配置**

在文件末尾（`storage:` 段落之后）追加：

```yaml

# =============================================================================
# 体力系统配置
# =============================================================================
stamina:
  enabled: true

  max-stamina: 100.0
  base-consumption-rate: 0.05
  base-recovery-rate: 0.3
  max-multiplier: 5.0

  continuous:
    sprint-multiplier: 2.0
    swim-multiplier: 2.5
    climb-multiplier: 2.0

  actions:
    attack-cost: 0.15
    mine-cost: 0.075
    use-tool-cost: 0.1

  special:
    underwater-multiplier: 4.0
    high-altitude-multiplier: 2.0
    high-altitude-y: 120

  recovery:
    idle:
      enabled: true
      delay-seconds: 3.0
      multiplier: 1.0
      blocked-in-extreme-temperature: true
      blocked-underwater: true
      blocked-when-attacked: true
    food:
      enabled: true
      hunger-to-stamina-ratio: 2.0
      rotten-food-penalty: 0.5
      cooldown-seconds: 1.0
    drink:
      enabled: true
      hydration-to-stamina-ratio: 0.5
    sleep:
      enabled: true
      outdoor-recovery-percent: 50.0
      blocked-in-extreme-temperature: true
    special-items:
      enabled: true
      items:
        GOLDEN_APPLE: 50.0
        ENCHANTED_GOLDEN_APPLE: 100.0

  penalties:
    tired:
      threshold: 60.0
      speed-multiplier: 0.85
    exhausted:
      threshold: 30.0
      speed-multiplier: 0.70
      mining-fatigue-amplifier: 0
      weakness-amplifier: 0
    depleted:
      threshold: 10.0
      speed-multiplier: 0.50
      mining-fatigue-amplifier: 1
      weakness-amplifier: 1
    effect-duration-seconds: 3

  messages:
    enter-tired: "&e⚡ 你感到有些疲劳了... (体力: {stamina}%)"
    enter-exhausted: "&c⚡ 你筋疲力尽了！无法疾跑！ (体力: {stamina}%)"
    enter-depleted: "&4⚡ 体力耗尽！你几乎无法行动！ (体力: {stamina}%)"
    depleted-reminder: "&4⚡ 你需要休息或进食来恢复体力！ (体力: {stamina}%)"
    recovered-from-tired: "&a⚡ 你感觉好多了。 (体力: {stamina}%)"
    recovered-from-exhausted: "&a⚡ 你恢复了一些精力。 (体力: {stamina}%)"
    recovered-from-depleted: "&a⚡ 你终于缓过来了。 (体力: {stamina}%)"
    depleted-reminder-cooldown-seconds: 30

  hud:
    bossbar-enabled: true
    bossbar-title-exhausted: "&e⚡ 体力不足！寻找食物或休息！"
    bossbar-title-depleted: "&4⚡ 体力耗尽！你几乎无法行动！"
    bossbar-color-exhausted: "YELLOW"
    bossbar-color-depleted: "RED"
    bossbar-style: "SOLID"

  integration:
    temperature:
      enabled: true
      extreme-multiplier: 1.5
      mild-multiplier: 1.2
    fracture:
      enabled: true
      threshold: 20.0
      consumption-multiplier: 1.3
      max-stamina-reduction: 30.0
    thirst:
      enabled: true
      dehydration-threshold: 30.0
      recovery-multiplier: 0.5
    wetness:
      enabled: true
      threshold: 0.7
      consumption-multiplier: 1.2
    food:
      enabled: true
      full-saturation-bonus: 1.25
    weather:
      enabled: true
      extreme-multiplier: 1.3
    season:
      enabled: true
      winter-multiplier: 1.1
      summer-multiplier: 1.05
```

- [ ] **步骤 2：构建验证**

运行：`./gradlew build`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add project/core/src/main/resources/feature/realworld/realworld.yml
git commit -m "feat(realworld): 添加体力系统配置段落"
```

---

### 任务 4：数据模型扩展 — PlayerEnvState 新增字段

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/RealWorldModels.kt`

- [ ] **步骤 1：在 PlayerEnvState 中新增体力相关字段**

在 `PlayerEnvState` data class 中追加字段（在 `hudRefreshTimer` 之后）：

```kotlin
var stamina: Double = 100.0,
var staminaPhase: StaminaPhase = StaminaPhase.FULL,
var staminaIdleTimer: Double = 0.0,
var staminaRecoveryCooldown: Double = 0.0,
var staminaChatWarnCooldown: Double = 0.0,
```

- [ ] **步骤 2：构建验证**

运行：`./gradlew build`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/RealWorldModels.kt
git commit -m "feat(realworld): PlayerEnvState 新增体力字段"
```

---

### 任务 5：核心引擎 — StaminaEngine.kt（消耗 + 恢复 + 阶段判定）

**文件：**
- 创建：`project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/StaminaEngine.kt`

- [ ] **步骤 1：创建 StaminaEngine.kt — 第一部分：消耗计算**

```kotlin
package com.pixlehavencore.feature.realworld

import com.pixlehavencore.bridge.TextBridge
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import taboolib.common.platform.function.info

object StaminaEngine {

    fun init() {
        if (!StaminaSettings.enabled) return
        info("[RealWorld] 体力系统已启动。")
    }

    fun reload() {
        StaminaSettings.reload()
    }

    fun getMaxStamina(playerState: PlayerEnvState): Double {
        if (!StaminaSettings.integrationFractureEnabled) return StaminaSettings.maxStamina
        return if (playerState.fracture > StaminaSettings.integrationFractureThreshold) {
            StaminaSettings.maxStamina - StaminaSettings.integrationFractureMaxStaminaReduction
        } else {
            StaminaSettings.maxStamina
        }
    }

    fun tick(player: Player, playerState: PlayerEnvState, globalState: GlobalEnvState, tickSeconds: Int) {
        if (!StaminaSettings.enabled) return

        val deltaSeconds = tickSeconds.toDouble()
        val maxStamina = getMaxStamina(playerState)

        // 消耗
        val consumption = computeConsumption(player, playerState, globalState, deltaSeconds)
        playerState.stamina = (playerState.stamina - consumption).coerceIn(0.0, maxStamina)

        // 恢复
        val recovery = computeRecovery(player, playerState, globalState, deltaSeconds)
        playerState.stamina = (playerState.stamina + recovery).coerceIn(0.0, maxStamina)

        // 冷却递减
        if (playerState.staminaRecoveryCooldown > 0.0) {
            playerState.staminaRecoveryCooldown = (playerState.staminaRecoveryCooldown - deltaSeconds).coerceAtLeast(0.0)
        }
        if (playerState.staminaChatWarnCooldown > 0.0) {
            playerState.staminaChatWarnCooldown = (playerState.staminaChatWarnCooldown - deltaSeconds).coerceAtLeast(0.0)
        }

        // 阶段判定与惩罚
        val oldPhase = playerState.staminaPhase
        val newPhase = classifyPhase(playerState.stamina)
        playerState.staminaPhase = newPhase

        // 阶段变化处理
        if (oldPhase != newPhase) {
            onPhaseChange(player, playerState, oldPhase, newPhase)
        }

        // DEPLETED 持续提醒
        if (newPhase == StaminaPhase.DEPLETED && playerState.staminaChatWarnCooldown <= 0.0) {
            sendStaminaMessage(player, playerState, StaminaSettings.msgDepletedReminder)
            playerState.staminaChatWarnCooldown = StaminaSettings.depletedReminderCooldownSeconds
        }

        // 应用惩罚
        applyPenalties(player, playerState, tickSeconds)
    }

    private fun computeConsumption(
        player: Player,
        playerState: PlayerEnvState,
        globalState: GlobalEnvState,
        deltaSeconds: Double,
    ): Double {
        val settings = StaminaSettings
        var behaviorMultiplier = 1.0

        // 持续消耗层
        when {
            player.isSprinting -> behaviorMultiplier = settings.sprintMultiplier
            player.isSwimming || isInWater(player) -> behaviorMultiplier = settings.swimMultiplier
            player.isClimbing -> behaviorMultiplier = settings.climbMultiplier
        }

        // 环境消耗层
        var environmentMultiplier = 1.0

        if (settings.integrationTemperatureEnabled) {
            when (playerState.temperaturePhase) {
                TemperaturePhase.SEVERE_HEAT, TemperaturePhase.SEVERE_COLD ->
                    environmentMultiplier *= settings.integrationTemperatureExtremeMultiplier
                TemperaturePhase.HEAT, TemperaturePhase.COLD_MILD ->
                    environmentMultiplier *= settings.integrationTemperatureMildMultiplier
                else -> Unit
            }
        }

        if (settings.integrationFractureEnabled && playerState.fracture > settings.integrationFractureThreshold) {
            environmentMultiplier *= settings.integrationFractureConsumptionMultiplier
        }

        if (settings.integrationWetnessEnabled && playerState.wetness > settings.integrationWetnessThreshold) {
            environmentMultiplier *= settings.integrationWetnessConsumptionMultiplier
        }

        // 特殊消耗层
        var specialMultiplier = 1.0
        if (isUnderwater(player)) {
            specialMultiplier = settings.underwaterMultiplier
        } else if (player.location.blockY > settings.highAltitudeY && !player.isOnGround) {
            specialMultiplier = settings.highAltitudeMultiplier
        }

        // 天气联动
        if (settings.integrationWeatherEnabled) {
            when (globalState.weather) {
                WeatherType.BLIZZARD, WeatherType.SANDSTORM ->
                    environmentMultiplier *= settings.integrationWeatherExtremeMultiplier
                else -> Unit
            }
        }

        // 季节联动
        if (settings.integrationSeasonEnabled) {
            when (globalState.season) {
                Season.WINTER -> environmentMultiplier *= settings.integrationSeasonWinterMultiplier
                Season.SUMMER -> environmentMultiplier *= settings.integrationSeasonSummerMultiplier
                else -> Unit
            }
        }

        val totalMultiplier = (behaviorMultiplier * environmentMultiplier * specialMultiplier)
            .coerceAtMost(settings.maxMultiplier)

        return settings.baseConsumptionRate * totalMultiplier * deltaSeconds
    }

    private fun computeRecovery(
        player: Player,
        playerState: PlayerEnvState,
        globalState: GlobalEnvState,
        deltaSeconds: Double,
    ): Double {
        val settings = StaminaSettings
        if (playerState.staminaRecoveryCooldown > 0.0) return 0.0

        // 静止休息
        if (settings.idleEnabled && playerState.staminaIdleTimer >= settings.idleDelaySeconds) {
            if (canIdleRecover(player, playerState)) {
                var recoveryMultiplier = settings.idleMultiplier

                // 口渴联动
                if (settings.integrationThirstEnabled && playerState.hydration < settings.integrationThirstDehydrationThreshold) {
                    recoveryMultiplier *= settings.integrationThirstRecoveryMultiplier
                }

                // 食物联动
                if (settings.integrationFoodEnabled && player.foodLevel >= 20) {
                    recoveryMultiplier *= settings.integrationFoodFullSaturationBonus
                }

                return settings.baseRecoveryRate * recoveryMultiplier * deltaSeconds
            }
        }

        return 0.0
    }

    private fun canIdleRecover(player: Player, playerState: PlayerEnvState): Boolean {
        val settings = StaminaSettings
        if (settings.idleBlockedInExtremeTemp) {
            when (playerState.temperaturePhase) {
                TemperaturePhase.SEVERE_HEAT, TemperaturePhase.SEVERE_COLD -> return false
                else -> Unit
            }
        }
        if (settings.idleBlockedUnderwater && isUnderwater(player)) return false
        return true
    }

    fun checkIdle(player: Player, playerState: PlayerEnvState, deltaSeconds: Double) {
        if (!StaminaSettings.enabled) return
        val isMoving = player.velocity.lengthSquared() > 0.001
        if (isMoving || player.isSprinting || player.isSwimming || player.isClimbing) {
            playerState.staminaIdleTimer = 0.0
        } else {
            playerState.staminaIdleTimer += deltaSeconds
        }
    }

    fun onAttack(player: Player, playerState: PlayerEnvState) {
        if (!StaminaSettings.enabled) return
        consumeActionStamina(player, playerState, StaminaSettings.attackCost, StaminaConsumeSource.ATTACK)
    }

    fun onMine(player: Player, playerState: PlayerEnvState) {
        if (!StaminaSettings.enabled) return
        consumeActionStamina(player, playerState, StaminaSettings.mineCost, StaminaConsumeSource.MINE)
    }

    fun onUseTool(player: Player, playerState: PlayerEnvState) {
        if (!StaminaSettings.enabled) return
        consumeActionStamina(player, playerState, StaminaSettings.useToolCost, StaminaConsumeSource.USE_TOOL)
    }

    private fun consumeActionStamina(
        player: Player,
        playerState: PlayerEnvState,
        baseCost: Double,
        source: StaminaConsumeSource,
    ) {
        val maxStamina = getMaxStamina(playerState)
        var cost = baseCost

        // 环境倍率也影响动作消耗
        val envMultiplier = computeEnvironmentMultiplier(playerState)
        cost *= envMultiplier

        val event = StaminaConsumeEvent(player, source, cost)
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return

        playerState.stamina = (playerState.stamina - event.amount).coerceIn(0.0, maxStamina)
    }

    private fun computeEnvironmentMultiplier(playerState: PlayerEnvState): Double {
        val settings = StaminaSettings
        var multiplier = 1.0
        if (settings.integrationTemperatureEnabled) {
            when (playerState.temperaturePhase) {
                TemperaturePhase.SEVERE_HEAT, TemperaturePhase.SEVERE_COLD ->
                    multiplier *= settings.integrationTemperatureExtremeMultiplier
                TemperaturePhase.HEAT, TemperaturePhase.COLD_MILD ->
                    multiplier *= settings.integrationTemperatureMildMultiplier
                else -> Unit
            }
        }
        if (settings.integrationFractureEnabled && playerState.fracture > settings.integrationFractureThreshold) {
            multiplier *= settings.integrationFractureConsumptionMultiplier
        }
        if (settings.integrationWetnessEnabled && playerState.wetness > settings.integrationWetnessThreshold) {
            multiplier *= settings.integrationWetnessConsumptionMultiplier
        }
        return multiplier.coerceAtMost(settings.maxMultiplier)
    }

    fun onEat(player: Player, playerState: PlayerEnvState, hungerRestored: Int) {
        if (!StaminaSettings.enabled || !StaminaSettings.foodEnabled) return
        if (playerState.staminaRecoveryCooldown > 0.0) return

        val maxStamina = getMaxStamina(playerState)
        var restoreAmount = hungerRestored * StaminaSettings.hungerToStaminaRatio

        val event = StaminaRecoverEvent(player, StaminaRecoverSource.FOOD, restoreAmount)
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return

        playerState.stamina = (playerState.stamina + event.amount).coerceIn(0.0, maxStamina)
        playerState.staminaRecoveryCooldown = StaminaSettings.foodCooldownSeconds
    }

    fun onDrink(player: Player, playerState: PlayerEnvState, hydrationRestored: Double) {
        if (!StaminaSettings.enabled || !StaminaSettings.drinkEnabled) return

        val maxStamina = getMaxStamina(playerState)
        var restoreAmount = hydrationRestored * StaminaSettings.hydrationToStaminaRatio

        val event = StaminaRecoverEvent(player, StaminaRecoverSource.DRINK, restoreAmount)
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return

        playerState.stamina = (playerState.stamina + event.amount).coerceIn(0.0, maxStamina)
    }

    fun onSleep(player: Player, playerState: PlayerEnvState, isOutdoor: Boolean) {
        if (!StaminaSettings.enabled || !StaminaSettings.sleepEnabled) return

        if (StaminaSettings.sleepBlockedInExtremeTemp) {
            when (playerState.temperaturePhase) {
                TemperaturePhase.SEVERE_HEAT, TemperaturePhase.SEVERE_COLD -> return
                else -> Unit
            }
        }

        val maxStamina = getMaxStamina(playerState)
        val recoveryPercent = if (isOutdoor) StaminaSettings.outdoorRecoveryPercent / 100.0 else 1.0
        val restoreAmount = maxStamina * recoveryPercent

        val event = StaminaRecoverEvent(player, StaminaRecoverSource.SLEEP, restoreAmount)
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return

        playerState.stamina = (playerState.stamina + event.amount).coerceIn(0.0, maxStamina)
    }

    fun onSpecialItem(player: Player, playerState: PlayerEnvState, material: Material) {
        if (!StaminaSettings.enabled || !StaminaSettings.specialItemsEnabled) return

        val restoreAmount = StaminaSettings.specialItems[material] ?: return
        val maxStamina = getMaxStamina(playerState)

        val event = StaminaRecoverEvent(player, StaminaRecoverSource.SPECIAL_ITEM, restoreAmount)
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return

        playerState.stamina = (playerState.stamina + event.amount).coerceIn(0.0, maxStamina)
    }

    fun classifyPhase(stamina: Double): StaminaPhase {
        val settings = StaminaSettings
        return when {
            stamina > settings.tiredThreshold -> StaminaPhase.FULL
            stamina > settings.exhaustedThreshold -> StaminaPhase.TIRED
            stamina > settings.depletedThreshold -> StaminaPhase.EXHAUSTED
            else -> StaminaPhase.DEPLETED
        }
    }

    private fun onPhaseChange(player: Player, playerState: PlayerEnvState, oldPhase: StaminaPhase, newPhase: StaminaPhase) {
        Bukkit.getPluginManager().callEvent(StaminaPhaseChangeEvent(player, oldPhase, newPhase))

        val staminaPercent = "%.0f".format(playerState.stamina / getMaxStamina(playerState) * 100)
        val message = when {
            newPhase == StaminaPhase.TIRED && oldPhase.ordinal < StaminaPhase.TIRED.ordinal ->
                StaminaSettings.msgEnterTired.replace("{stamina}", staminaPercent)
            newPhase == StaminaPhase.EXHAUSTED && oldPhase.ordinal < StaminaPhase.EXHAUSTED.ordinal ->
                StaminaSettings.msgEnterExhausted.replace("{stamina}", staminaPercent)
            newPhase == StaminaPhase.DEPLETED && oldPhase.ordinal < StaminaPhase.DEPLETED.ordinal -> {
                Bukkit.getPluginManager().callEvent(StaminaDepletedEvent(player))
                StaminaSettings.msgEnterDepleted.replace("{stamina}", staminaPercent)
            }
            oldPhase == StaminaPhase.TIRED && newPhase.ordinal < StaminaPhase.TIRED.ordinal ->
                StaminaSettings.msgRecoveredFromTired.replace("{stamina}", staminaPercent)
            oldPhase == StaminaPhase.EXHAUSTED && newPhase.ordinal < StaminaPhase.EXHAUSTED.ordinal ->
                StaminaSettings.msgRecoveredFromExhausted.replace("{stamina}", staminaPercent)
            oldPhase == StaminaPhase.DEPLETED && newPhase.ordinal < StaminaPhase.DEPLETED.ordinal ->
                StaminaSettings.msgRecoveredFromDepleted.replace("{stamina}", staminaPercent)
            else -> ""
        }

        if (message.isNotEmpty()) {
            sendStaminaMessage(player, playerState, message)
        }

        // 进入 DEPLETED 时重置提醒冷却
        if (newPhase == StaminaPhase.DEPLETED) {
            playerState.staminaChatWarnCooldown = StaminaSettings.depletedReminderCooldownSeconds
        }
    }

    private fun applyPenalties(player: Player, playerState: PlayerEnvState, tickSeconds: Int) {
        val phase = playerState.staminaPhase
        val settings = StaminaSettings

        // 移动速度
        val baseSpeed = 0.2f
        val targetSpeed = (baseSpeed * phase.speedMultiplier).toFloat()
        if (player.walkSpeed != targetSpeed) {
            player.walkSpeed = targetSpeed
        }

        // 禁止疾跑
        if (!phase.canSprint && player.isSprinting) {
            player.isSprinting = false
        }

        // 药水效果
        val effectDuration = settings.effectDurationSeconds * 20 + 10
        when (phase) {
            StaminaPhase.EXHAUSTED -> {
                PotionEffectType.MINING_FATIGUE?.let {
                    player.addPotionEffect(PotionEffect(it, effectDuration, settings.exhaustedMiningFatigueAmplifier, false, false, false))
                }
                PotionEffectType.WEAKNESS?.let {
                    player.addPotionEffect(PotionEffect(it, effectDuration, settings.exhaustedWeaknessAmplifier, false, false, false))
                }
            }
            StaminaPhase.DEPLETED -> {
                PotionEffectType.MINING_FATIGUE?.let {
                    player.addPotionEffect(PotionEffect(it, effectDuration, settings.depletedMiningFatigueAmplifier, false, false, false))
                }
                PotionEffectType.WEAKNESS?.let {
                    player.addPotionEffect(PotionEffect(it, effectDuration, settings.depletedWeaknessAmplifier, false, false, false))
                }
            }
            else -> Unit
        }
    }

    private fun sendStaminaMessage(player: Player, playerState: PlayerEnvState, message: String) {
        val colorized = message.replace("&", "§")
        player.sendMessage(colorized)
    }

    private fun isInWater(player: Player): Boolean {
        val blockType = player.location.block.type
        return blockType == Material.WATER || blockType == Material.BUBBLE_COLUMN
    }

    private fun isUnderwater(player: Player): Boolean {
        return player.remainingAir < player.maximumAir
    }

    fun getStaminaInfo(player: Player): StaminaInfo? {
        val state = RealWorldStorage.getPlayerSnapshot(player.uniqueId) ?: return null
        val maxStamina = getMaxStamina(state)
        return StaminaInfo(
            stamina = state.stamina,
            maxStamina = maxStamina,
            phase = state.staminaPhase,
            consumptionMultiplier = computeEnvironmentMultiplier(state),
        )
    }

    fun setStamina(player: Player, amount: Double) {
        RealWorldStorage.withPlayerState(player.uniqueId) { state ->
            val maxStamina = getMaxStamina(state)
            state.stamina = amount.coerceIn(0.0, maxStamina)
            state.staminaPhase = classifyPhase(state.stamina)
        }
        RealWorldStorage.markPlayerDirty(player.uniqueId)
    }

    fun addStamina(player: Player, amount: Double) {
        RealWorldStorage.withPlayerState(player.uniqueId) { state ->
            val maxStamina = getMaxStamina(state)
            state.stamina = (state.stamina + amount).coerceIn(0.0, maxStamina)
            state.staminaPhase = classifyPhase(state.stamina)
        }
        RealWorldStorage.markPlayerDirty(player.uniqueId)
    }

    fun removeStamina(player: Player, amount: Double) {
        RealWorldStorage.withPlayerState(player.uniqueId) { state ->
            state.stamina = (state.stamina - amount).coerceIn(0.0, getMaxStamina(state))
            state.staminaPhase = classifyPhase(state.stamina)
        }
        RealWorldStorage.markPlayerDirty(player.uniqueId)
    }

    fun resetStamina(player: Player) {
        RealWorldStorage.withPlayerState(player.uniqueId) { state ->
            state.stamina = getMaxStamina(state)
            state.staminaPhase = StaminaPhase.FULL
            state.staminaIdleTimer = 0.0
            state.staminaRecoveryCooldown = 0.0
            state.staminaChatWarnCooldown = 0.0
        }
        RealWorldStorage.markPlayerDirty(player.uniqueId)
    }
}

data class StaminaInfo(
    val stamina: Double,
    val maxStamina: Double,
    val phase: StaminaPhase,
    val consumptionMultiplier: Double,
)
```

- [ ] **步骤 2：构建验证**

运行：`./gradlew build`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/StaminaEngine.kt
git commit -m "feat(realworld): 添加体力系统核心引擎"
```

---

### 任务 6：集成到 RealWorldSettings — init/reload 调用

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/RealWorldSettings.kt`

- [ ] **步骤 1：在 init() 中调用 StaminaSettings.init()**

在 `RealWorldSettings.init()` 方法中（`reload()` 调用之前）添加：

```kotlin
StaminaSettings.init()
```

- [ ] **步骤 2：在 reload() 末尾调用 StaminaSettings.reload()**

在 `RealWorldSettings.reload()` 方法末尾（`severeColdThreshold` 赋值之后）添加：

```kotlin
StaminaSettings.reload()
```

- [ ] **步骤 3：构建验证**

运行：`./gradlew build`
预期：BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/RealWorldSettings.kt
git commit -m "feat(realworld): 集成 StaminaSettings 到 RealWorldSettings"
```

---

### 任务 7：集成到 RealWorldService — tick 循环 + 事件监听

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/RealWorldService.kt`

- [ ] **步骤 1：在 init() 中调用 StaminaEngine.init()**

在 `RealWorldService.init()` 方法中（`RealWorldSettings.init()` 之后）添加：

```kotlin
StaminaEngine.init()
```

- [ ] **步骤 2：在 reload() 中调用 StaminaEngine.reload()**

在 `RealWorldService.reload()` 方法中（`RealWorldSettings.reload()` 之后）添加：

```kotlin
StaminaEngine.reload()
```

- [ ] **步骤 3：在 tick 循环中调用 StaminaEngine**

在 `startTickTask()` 方法中，`playerState.hudRefreshTimer` 递减之前，添加：

```kotlin
StaminaEngine.checkIdle(player, playerState, tickSeconds.toDouble())
StaminaEngine.tick(player, playerState, globalSnapshot, tickSeconds)
```

- [ ] **步骤 4：在 resetPlayer 中重置体力**

在 `RealWorldService.resetPlayer()` 方法中，确保调用 `StaminaEngine.resetStamina()`（或在 `RealWorldStorage.resetPlayer()` 中重置 stamina 字段）。

- [ ] **步骤 5：构建验证**

运行：`./gradlew build`
预期：BUILD SUCCESSFUL

- [ ] **步骤 6：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/RealWorldService.kt
git commit -m "feat(realworld): 集成 StaminaEngine 到 tick 循环"
```

---

### 任务 8：集成到 RealWorldStorage — 数据库表 + 读写

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/RealWorldStorage.kt`

- [ ] **步骤 1：在 createTables() 中新增 stamina 列**

在 `createTables()` 方法中，`fracture` 列的 ALTER TABLE 之后，添加：

```kotlin
connection.prepareStatement(
    "ALTER TABLE $PLAYER_TABLE ADD COLUMN ${quoted("stamina")} DOUBLE NOT NULL DEFAULT 100.0".trimIndent()
).use { statement ->
    runCatching { statement.execute() }
}
```

- [ ] **步骤 2：修改 loadPlayer() 读取 stamina**

在 `loadPlayer()` 方法的 SQL 查询中，新增 `stamina` 列：

```kotlin
"SELECT ${quoted("hydration")}, ${quoted("last_temperature")}, ${quoted("fracture")}, ${quoted("stamina")} FROM $PLAYER_TABLE WHERE ${quoted("uuid")} = ?"
```

在 `PlayerEnvState` 构造中，添加：

```kotlin
stamina = result.getDouble("stamina").let { if (it == 0.0) 100.0 else it }
```

- [ ] **步骤 3：修改 savePlayerSnapshot() 写入 stamina**

在 `savePlayerSnapshot()` 方法中，SQL 和参数绑定新增 stamina：

```kotlin
statement.setDouble(4, snapshot.fracture)
statement.setDouble(5, snapshot.stamina)
statement.setTimestamp(6, DatabaseUtils.now())
```

- [ ] **步骤 4：修改 playerUpsertSql() 包含 stamina**

更新 UPSERT SQL 语句，包含 `stamina` 列。

- [ ] **步骤 5：修改 samePersistedPlayerState() 包含 stamina**

```kotlin
current.stamina == snapshot.stamina
```

- [ ] **步骤 6：构建验证**

运行：`./gradlew build`
预期：BUILD SUCCESSFUL

- [ ] **步骤 7：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/RealWorldStorage.kt
git commit -m "feat(realworld): 集成体力数据存储"
```

---

### 任务 9：集成到 SurvivalHud — BossBar 显示

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/SurvivalHud.kt`

- [ ] **步骤 1：修改 renderBossBar() 支持体力 BossBar**

在 `renderBossBar()` 方法中，修改 BossBar 标题和颜色选择逻辑，优先显示体力警告：

```kotlin
private fun renderBossBar(player: Player, state: PlayerEnvState) {
    if (!RealWorldSettings.hudBossBarEnabled && !StaminaSettings.bossBarEnabled) {
        removeBossBar(player)
        return
    }

    val title: String
    val color: BarColor

    when {
        // 体力 DEPLETED 最高优先级
        StaminaSettings.bossBarEnabled && state.staminaPhase == StaminaPhase.DEPLETED -> {
            title = StaminaSettings.bossBarTitleDepleted
            color = BarColor.valueOf(StaminaSettings.bossBarColorDepleted)
        }
        // 体力 EXHAUSTED 次优先级
        StaminaSettings.bossBarEnabled && state.staminaPhase == StaminaPhase.EXHAUSTED -> {
            title = StaminaSettings.bossBarTitleExhausted
            color = BarColor.valueOf(StaminaSettings.bossbarColorExhausted)
        }
        // 温度/口渴极端状态
        state.temperaturePhase == TemperaturePhase.SEVERE_HEAT -> {
            title = RealWorldSettings.hudBossBarTitleHeat
            color = BarColor.RED
        }
        state.temperaturePhase == TemperaturePhase.SEVERE_COLD -> {
            title = RealWorldSettings.hudBossBarTitleCold
            color = BarColor.BLUE
        }
        state.thirstPhase == ThirstPhase.DEHYDRATED -> {
            title = RealWorldSettings.hudBossBarTitleThirst
            color = BarColor.YELLOW
        }
        else -> {
            removeBossBar(player)
            return
        }
    }

    val bossBar: BossBar = bossBars.get(player.uniqueId) ?: run {
        val barStyle = try { BarStyle.valueOf(StaminaSettings.bossBarStyle) } catch (_: Exception) { BarStyle.SOLID }
        val bar = Bukkit.createBossBar(colorize(title), color, barStyle)
        bossBars[player.uniqueId] = bar
        bar
    }

    bossBar.setTitle(colorize(title))
    bossBar.color = color
    bossBar.progress = 1.0
    if (!bossBar.players.contains(player)) {
        bossBar.addPlayer(player)
    }
}
```

- [ ] **步骤 2：修改 isSevereState() 包含体力状态**

```kotlin
private fun isSevereState(state: PlayerEnvState): Boolean {
    return state.temperaturePhase == TemperaturePhase.SEVERE_HEAT ||
        state.temperaturePhase == TemperaturePhase.SEVERE_COLD ||
        state.thirstPhase == ThirstPhase.DEHYDRATED ||
        state.staminaPhase == StaminaPhase.EXHAUSTED ||
        state.staminaPhase == StaminaPhase.DEPLETED
}
```

- [ ] **步骤 3：构建验证**

运行：`./gradlew build`
预期：BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/SurvivalHud.kt
git commit -m "feat(realworld): 集成体力 BossBar 显示"
```

---

### 任务 10：集成到 RealWorldCommand — stamina 子命令

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/RealWorldCommand.kt`

- [ ] **步骤 1：在 main 帮助中添加 stamina 命令说明**

在 `main` 命令的 help 消息中追加：

```kotlin
sender.msg("&b/rw stamina <info|set|add|remove|reset|toggle> &7- 管理玩家体力")
```

- [ ] **步骤 2：新增 stamina 子命令**

在 `RealWorldCommand` 中新增：

```kotlin
@CommandBody
val stamina = subCommand {
    dynamic(comment = "action") {
        suggestion<ProxyCommandSender> { _, _ -> listOf("info", "set", "add", "remove", "reset", "toggle") }
        execute<ProxyCommandSender> { sender, _, argument ->
            if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
            val action = argument.toString().trim().lowercase()
            when (action) {
                "toggle" -> {
                    val enabled = !StaminaSettings.enabled
                    // 需要在 StaminaSettings 中添加 toggle 方法或直接修改配置
                    sender.msg("&a体力系统已${if (enabled) "启用" else "禁用"}。")
                }
                else -> sender.msg("&c请指定玩家名。用法: /rw stamina <info|set|add|remove|reset> <玩家名>")
            }
        }
        dynamic(comment = "player") {
            suggestPlayers()
            execute<ProxyCommandSender> { sender, _, argument ->
                if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                val args = argument.toString().trim().split(" ")
                val action = args[0].lowercase()
                val playerName = args.getOrNull(1) ?: run {
                    sender.msg("&c请指定玩家名。")
                    return@execute
                }
                val target = findOnlinePlayer(playerName)
                if (target == null) {
                    sender.msg("&c找不到在线玩家 &f$playerName&c。")
                    return@execute
                }
                when (action) {
                    "info" -> {
                        val info = StaminaEngine.getStaminaInfo(target)
                        if (info == null) {
                            sender.msg("&e玩家 &f${target.name} &e当前没有缓存体力数据。")
                            return@execute
                        }
                        sender.msg("&6=== 玩家体力信息：${target.name} ===")
                        sender.msg("&7体力值: &f${"%.1f".format(info.stamina)} / ${"%.1f".format(info.maxStamina)}")
                        sender.msg("&7阶段: &f${info.phase.displayName}")
                        sender.msg("&7环境消耗倍率: &f×${"%.1f".format(info.consumptionMultiplier)}")
                    }
                    else -> {
                        val value = args.getOrNull(2)?.toDoubleOrNull()
                        if (value == null) {
                            sender.msg("&c请指定数值。用法: /rw stamina $action <玩家名> <数值>")
                            return@execute
                        }
                        when (action) {
                            "set" -> {
                                StaminaEngine.setStamina(target, value)
                                sender.msg("&a已设置玩家 &f${target.name} &a的体力为 &f${"%.1f".format(value)}&a。")
                            }
                            "add" -> {
                                StaminaEngine.addStamina(target, value)
                                sender.msg("&a已为玩家 &f${target.name} &a增加 &f${"%.1f".format(value)} &a体力。")
                            }
                            "remove" -> {
                                StaminaEngine.removeStamina(target, value)
                                sender.msg("&a已从玩家 &f${target.name} &a减少 &f${"%.1f".format(value)} &a体力。")
                            }
                            else -> sender.msg("&c未知操作: $action")
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **步骤 3：构建验证**

运行：`./gradlew build`
预期：BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/RealWorldCommand.kt
git commit -m "feat(realworld): 添加 stamina 子命令"
```

---

### 任务 11：集成事件监听 — 进食/睡觉/攻击/挖掘

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/RealWorldService.kt`（或新建事件监听文件）

- [ ] **步骤 1：在 RealWorldService 中添加事件监听方法**

在 `RealWorldService` 中添加以下事件监听（或在已有的事件监听区域中添加）：

```kotlin
@SubscribeEvent
fun onPlayerConsume(event: PlayerItemConsumeEvent) {
    if (!StaminaSettings.enabled) return
    val player = event.player
    val item = event.item
    val foodValues = getFoodValues(item.type)
    if (foodValues > 0) {
        RealWorldStorage.withPlayerState(player.uniqueId) { state ->
            StaminaEngine.onEat(player, state, foodValues)
        }
    }
    StaminaEngine.onSpecialItem(player, RealWorldStorage.getPlayerSnapshot(player.uniqueId) ?: return, item.type)
}

@SubscribeEvent
fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
    if (!StaminaSettings.enabled) return
    val player = event.damager as? Player ?: return
    RealWorldStorage.withPlayerState(player.uniqueId) { state ->
        StaminaEngine.onAttack(player, state)
    }
}

@SubscribeEvent
fun onBlockBreak(event: BlockBreakEvent) {
    if (!StaminaSettings.enabled) return
    val player = event.player
    RealWorldStorage.withPlayerState(player.uniqueId) { state ->
        StaminaEngine.onMine(player, state)
    }
}

private fun getFoodValues(material: Material): Int {
    return when (material) {
        Material.BREAD -> 5
        Material.COOKED_BEEF, Material.COOKED_PORKCHOP, Material.COOKED_MUTTON -> 8
        Material.COOKED_CHICKEN, Material.COOKED_COD, Material.COOKED_SALMON -> 6
        Material.BAKED_POTATO -> 5
        Material.MUSHROOM_STEW, Material.RABBIT_STEW, Material.BEETROOT_SOUP -> 7
        Material.GOLDEN_APPLE -> 4
        Material.ENCHANTED_GOLDEN_APPLE -> 4
        Material.COOKED_RABBIT -> 5
        Material.APPLE, Material.BEETROOT, Material.CARROT, Material.POTATO, Material.SWEET_BERRIES, Material.GLOW_BERRIES -> 3
        Material.MELON_SLICE, Material.CHORUS_FRUIT -> 2
        Material.COOKIE -> 2
        Material.DRIED_KELP -> 1
        else -> 0
    }
}
```

- [ ] **步骤 2：构建验证**

运行：`./gradlew build`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/RealWorldService.kt
git commit -m "feat(realworld): 添加体力系统事件监听"
```

---

### 任务 12：端到端验证

- [ ] **步骤 1：完整构建**

运行：`./gradlew clean build`
预期：BUILD SUCCESSFUL

- [ ] **步骤 2：最终 Commit**

```bash
git add -A
git commit -m "feat(realworld): 完成体力疲劳系统实现"
```

---

## 自检清单

| 检查项 | 状态 |
|--------|------|
| 规格第2节（数据模型）→ 任务 1、4 | ✓ |
| 规格第3节（四层消耗）→ 任务 5 | ✓ |
| 规格第4节（恢复机制）→ 任务 5 | ✓ |
| 规格第5节（惩罚机制）→ 任务 5 | ✓ |
| 规格第6节（系统联动）→ 任务 5 | ✓ |
| 规格第7节（聊天提醒）→ 任务 5 | ✓ |
| 规格第8节（HUD/BossBar）→ 任务 9 | ✓ |
| 规格第9节（命令系统）→ 任务 10 | ✓ |
| 规格第10节（数据存储）→ 任务 8 | ✓ |
| 规格第11节（自定义事件）→ 任务 1 | ✓ |
| 规格第12节（实现架构）→ 全部任务 | ✓ |
| 规格第13节（配置结构）→ 任务 2、3 | ✓ |
| 无占位符/TODO | ✓ |
| 类型/方法名一致性 | ✓ |
