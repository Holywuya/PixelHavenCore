package com.pixlehavencore.feature.realworld

import org.bukkit.Material
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object StaminaSettings {

    @Config("feature/realworld/stamina.yml")
    private lateinit var config: Configuration

    // 基础数值
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

    // 持续消耗倍率
    var sprintMultiplier: Double = 2.0
        private set
    var swimMultiplier: Double = 2.5
        private set
    var climbMultiplier: Double = 2.0
        private set

    // 动作消耗
    var attackCost: Double = 0.15
        private set
    var mineCost: Double = 0.075
        private set
    var useToolCost: Double = 0.1
        private set

    // 特殊场景
    var underwaterMultiplier: Double = 4.0
        private set
    var highAltitudeMultiplier: Double = 2.0
        private set
    var highAltitudeY: Int = 120
        private set

    // 恢复方式 - idle
    var recoveryIdleEnabled: Boolean = true
        private set
    var recoveryIdleDelaySeconds: Int = 3
        private set
    var recoveryIdleMultiplier: Double = 1.0
        private set
    var recoveryIdleBlockedInExtremeTemperature: Boolean = true
        private set
    var recoveryIdleBlockedUnderwater: Boolean = true
        private set
    var recoveryIdleBlockedWhenAttacked: Boolean = true
        private set

    // 恢复方式 - food
    var recoveryFoodEnabled: Boolean = true
        private set
    var recoveryFoodHungerToStaminaRatio: Double = 2.0
        private set
    var recoveryFoodRottenFoodPenalty: Double = 0.5
        private set
    var recoveryFoodCooldownSeconds: Int = 1
        private set

    // 恢复方式 - drink
    var recoveryDrinkEnabled: Boolean = true
        private set
    var recoveryDrinkHydrationToStaminaRatio: Double = 1.5
        private set

    // 恢复方式 - sleep
    var recoverySleepEnabled: Boolean = true
        private set
    var recoverySleepOutdoorRecoveryPercent: Double = 0.5
        private set
    var recoverySleepBlockedInExtremeTemperature: Boolean = true
        private set

    // 恢复方式 - special items
    var recoverySpecialItemsEnabled: Boolean = true
        private set
    var recoverySpecialItems: Map<Material, Double> = emptyMap()
        private set

    // 惩罚阶段 - tired
    var penaltyTiredThreshold: Double = 0.7
        private set
    var penaltyTiredSpeedMultiplier: Double = 0.85
        private set

    // 惩罚阶段 - exhausted
    var penaltyExhaustedThreshold: Double = 0.3
        private set
    var penaltyExhaustedSpeedMultiplier: Double = 0.6
        private set
    var penaltyExhaustedMiningFatigueAmplifier: Int = 1
        private set
    var penaltyExhaustedWeaknessAmplifier: Int = 1
        private set

    // 惩罚阶段 - depleted
    var penaltyDepletedThreshold: Double = 0.0
        private set
    var penaltyDepletedSpeedMultiplier: Double = 0.4
        private set
    var penaltyDepletedMiningFatigueAmplifier: Int = 2
        private set
    var penaltyDepletedWeaknessAmplifier: Int = 2
        private set

    // 惩罚效果持续时间
    var penaltyEffectDurationSeconds: Int = 3
        private set

    // 聊天提醒
    var messageEnterTired: String = "&e你感到疲惫了..."
        private set
    var messageEnterExhausted: String = "&c你精疲力竭了！"
        private set
    var messageEnterDepleted: String = "&4你已经耗尽了体力！"
        private set
    var messageDepletedReminder: String = "&4你的体力已耗尽，快休息吧！"
        private set
    var messageRecoveredFromTired: String = "&a你恢复了一些体力。"
        private set
    var messageRecoveredFromExhausted: String = "&a你不再精疲力竭了。"
        private set
    var messageRecoveredFromDepleted: String = "&a你恢复了行动能力。"
        private set
    var messageDepletedReminderCooldownSeconds: Int = 10
        private set

    // HUD - bossbar
    var hudBossBarEnabled: Boolean = true
        private set
    var hudBossBarTitleExhausted: String = "&c精疲力竭！"
        private set
    var hudBossBarTitleDepleted: String = "&4体力耗尽！"
        private set
    var hudBossBarColorExhausted: String = "YELLOW"
        private set
    var hudBossBarColorDepleted: String = "RED"
        private set
    var hudBossBarStyle: String = "SOLID"
        private set

    // 系统联动 - temperature
    var integrationTemperatureEnabled: Boolean = true
        private set
    var integrationTemperatureExtremeMultiplier: Double = 3.0
        private set
    var integrationTemperatureMildMultiplier: Double = 1.5
        private set

    // 系统联动 - fracture
    var integrationFractureEnabled: Boolean = true
        private set
    var integrationFractureThreshold: Double = 50.0
        private set
    var integrationFractureConsumptionMultiplier: Double = 2.0
        private set
    var integrationFractureMaxStaminaReduction: Double = 30.0
        private set

    // 系统联动 - thirst
    var integrationThirstEnabled: Boolean = true
        private set
    var integrationThirstDehydrationThreshold: Double = 20.0
        private set
    var integrationThirstRecoveryMultiplier: Double = 0.5
        private set

    // 系统联动 - wetness
    var integrationWetnessEnabled: Boolean = true
        private set
    var integrationWetnessThreshold: Double = 80.0
        private set
    var integrationWetnessConsumptionMultiplier: Double = 1.5
        private set

    // 系统联动 - food
    var integrationFoodEnabled: Boolean = true
        private set
    var integrationFoodFullSaturationBonus: Double = 1.2
        private set

    // 系统联动 - weather
    var integrationWeatherEnabled: Boolean = true
        private set
    var integrationWeatherExtremeMultiplier: Double = 2.0
        private set

    // 系统联动 - season
    var integrationSeasonEnabled: Boolean = true
        private set
    var integrationSeasonWinterMultiplier: Double = 1.5
        private set
    var integrationSeasonSummerMultiplier: Double = 1.2
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()

        // 基础数值
        enabled = config.getBoolean("stamina.enabled", true)
        maxStamina = config.getDouble("stamina.max-stamina", 100.0).coerceAtLeast(1.0)
        baseConsumptionRate = config.getDouble("stamina.base-consumption-rate", 0.05).coerceAtLeast(0.0)
        baseRecoveryRate = config.getDouble("stamina.base-recovery-rate", 0.3).coerceAtLeast(0.0)
        maxMultiplier = config.getDouble("stamina.max-multiplier", 5.0).coerceAtLeast(1.0)

        // 持续消耗倍率
        sprintMultiplier = config.getDouble("stamina.continuous.sprint-multiplier", 2.0).coerceAtLeast(0.0)
        swimMultiplier = config.getDouble("stamina.continuous.swim-multiplier", 2.5).coerceAtLeast(0.0)
        climbMultiplier = config.getDouble("stamina.continuous.climb-multiplier", 2.0).coerceAtLeast(0.0)

        // 动作消耗
        attackCost = config.getDouble("stamina.actions.attack-cost", 0.15).coerceAtLeast(0.0)
        mineCost = config.getDouble("stamina.actions.mine-cost", 0.075).coerceAtLeast(0.0)
        useToolCost = config.getDouble("stamina.actions.use-tool-cost", 0.1).coerceAtLeast(0.0)

        // 特殊场景
        underwaterMultiplier = config.getDouble("stamina.special.underwater-multiplier", 4.0).coerceAtLeast(0.0)
        highAltitudeMultiplier = config.getDouble("stamina.special.high-altitude-multiplier", 2.0).coerceAtLeast(0.0)
        highAltitudeY = config.getInt("stamina.special.high-altitude-y", 120)

        // 恢复方式 - idle
        recoveryIdleEnabled = config.getBoolean("stamina.recovery.idle.enabled", true)
        recoveryIdleDelaySeconds = config.getInt("stamina.recovery.idle.delay-seconds", 3).coerceAtLeast(0)
        recoveryIdleMultiplier = config.getDouble("stamina.recovery.idle.multiplier", 1.0).coerceAtLeast(0.0)
        recoveryIdleBlockedInExtremeTemperature = config.getBoolean("stamina.recovery.idle.blocked-in-extreme-temperature", true)
        recoveryIdleBlockedUnderwater = config.getBoolean("stamina.recovery.idle.blocked-underwater", true)
        recoveryIdleBlockedWhenAttacked = config.getBoolean("stamina.recovery.idle.blocked-when-attacked", true)

        // 恢复方式 - food
        recoveryFoodEnabled = config.getBoolean("stamina.recovery.food.enabled", true)
        recoveryFoodHungerToStaminaRatio = config.getDouble("stamina.recovery.food.hunger-to-stamina-ratio", 2.0).coerceAtLeast(0.0)
        recoveryFoodRottenFoodPenalty = config.getDouble("stamina.recovery.food.rotten-food-penalty", 0.5).coerceIn(0.0, 1.0)
        recoveryFoodCooldownSeconds = config.getInt("stamina.recovery.food.cooldown-seconds", 1).coerceAtLeast(0)

        // 恢复方式 - drink
        recoveryDrinkEnabled = config.getBoolean("stamina.recovery.drink.enabled", true)
        recoveryDrinkHydrationToStaminaRatio = config.getDouble("stamina.recovery.drink.hydration-to-stamina-ratio", 1.5).coerceAtLeast(0.0)

        // 恢复方式 - sleep
        recoverySleepEnabled = config.getBoolean("stamina.recovery.sleep.enabled", true)
        recoverySleepOutdoorRecoveryPercent = config.getDouble("stamina.recovery.sleep.outdoor-recovery-percent", 0.5).coerceIn(0.0, 1.0)
        recoverySleepBlockedInExtremeTemperature = config.getBoolean("stamina.recovery.sleep.blocked-in-extreme-temperature", true)

        // 恢复方式 - special items
        recoverySpecialItemsEnabled = config.getBoolean("stamina.recovery.special-items.enabled", true)
        recoverySpecialItems = config.getConfigurationSection("stamina.recovery.special-items.items")
            ?.getKeys(false)
            ?.mapNotNull { key ->
                val material = runCatching { Material.valueOf(key.uppercase()) }.getOrNull()
                val ratio = config.getDouble("stamina.recovery.special-items.items.$key")
                if (material != null) material to ratio else null
            }
            ?.toMap()
            ?: emptyMap()

        // 惩罚阶段 - tired
        penaltyTiredThreshold = config.getDouble("stamina.penalties.tired.threshold", 0.7).coerceIn(0.0, 1.0)
        penaltyTiredSpeedMultiplier = config.getDouble("stamina.penalties.tired.speed-multiplier", 0.85).coerceIn(0.0, 1.0)

        // 惩罚阶段 - exhausted
        penaltyExhaustedThreshold = config.getDouble("stamina.penalties.exhausted.threshold", 0.3).coerceIn(0.0, 1.0)
        penaltyExhaustedSpeedMultiplier = config.getDouble("stamina.penalties.exhausted.speed-multiplier", 0.6).coerceIn(0.0, 1.0)
        penaltyExhaustedMiningFatigueAmplifier = config.getInt("stamina.penalties.exhausted.mining-fatigue-amplifier", 1).coerceAtLeast(0)
        penaltyExhaustedWeaknessAmplifier = config.getInt("stamina.penalties.exhausted.weakness-amplifier", 1).coerceAtLeast(0)

        // 惩罚阶段 - depleted
        penaltyDepletedThreshold = config.getDouble("stamina.penalties.depleted.threshold", 0.0).coerceIn(0.0, 1.0)
        penaltyDepletedSpeedMultiplier = config.getDouble("stamina.penalties.depleted.speed-multiplier", 0.4).coerceIn(0.0, 1.0)
        penaltyDepletedMiningFatigueAmplifier = config.getInt("stamina.penalties.depleted.mining-fatigue-amplifier", 2).coerceAtLeast(0)
        penaltyDepletedWeaknessAmplifier = config.getInt("stamina.penalties.depleted.weakness-amplifier", 2).coerceAtLeast(0)

        // 惩罚效果持续时间
        penaltyEffectDurationSeconds = config.getInt("stamina.penalties.effect-duration-seconds", 3).coerceAtLeast(1)

        // 聊天提醒
        messageEnterTired = config.getString("stamina.messages.enter-tired") ?: "&e你感到疲惫了..."
        messageEnterExhausted = config.getString("stamina.messages.enter-exhausted") ?: "&c你精疲力竭了！"
        messageEnterDepleted = config.getString("stamina.messages.enter-depleted") ?: "&4你已经耗尽了体力！"
        messageDepletedReminder = config.getString("stamina.messages.depleted-reminder") ?: "&4你的体力已耗尽，快休息吧！"
        messageRecoveredFromTired = config.getString("stamina.messages.recovered-from-tired") ?: "&a你恢复了一些体力。"
        messageRecoveredFromExhausted = config.getString("stamina.messages.recovered-from-exhausted") ?: "&a你不再精疲力竭了。"
        messageRecoveredFromDepleted = config.getString("stamina.messages.recovered-from-depleted") ?: "&a你恢复了行动能力。"
        messageDepletedReminderCooldownSeconds = config.getInt("stamina.messages.depleted-reminder-cooldown-seconds", 10).coerceAtLeast(0)

        // HUD - bossbar
        hudBossBarEnabled = config.getBoolean("stamina.hud.bossbar-enabled", true)
        hudBossBarTitleExhausted = config.getString("stamina.hud.bossbar-title-exhausted") ?: "&c精疲力竭！"
        hudBossBarTitleDepleted = config.getString("stamina.hud.bossbar-title-depleted") ?: "&4体力耗尽！"
        hudBossBarColorExhausted = config.getString("stamina.hud.bossbar-color-exhausted") ?: "YELLOW"
        hudBossBarColorDepleted = config.getString("stamina.hud.bossbar-color-depleted") ?: "RED"
        hudBossBarStyle = config.getString("stamina.hud.bossbar-style") ?: "SOLID"

        // 系统联动 - temperature
        integrationTemperatureEnabled = config.getBoolean("stamina.integration.temperature.enabled", true)
        integrationTemperatureExtremeMultiplier = config.getDouble("stamina.integration.temperature.extreme-multiplier", 3.0).coerceAtLeast(0.0)
        integrationTemperatureMildMultiplier = config.getDouble("stamina.integration.temperature.mild-multiplier", 1.5).coerceAtLeast(0.0)

        // 系统联动 - fracture
        integrationFractureEnabled = config.getBoolean("stamina.integration.fracture.enabled", true)
        integrationFractureThreshold = config.getDouble("stamina.integration.fracture.threshold", 50.0).coerceAtLeast(0.0)
        integrationFractureConsumptionMultiplier = config.getDouble("stamina.integration.fracture.consumption-multiplier", 2.0).coerceAtLeast(0.0)
        integrationFractureMaxStaminaReduction = config.getDouble("stamina.integration.fracture.max-stamina-reduction", 30.0).coerceAtLeast(0.0)

        // 系统联动 - thirst
        integrationThirstEnabled = config.getBoolean("stamina.integration.thirst.enabled", true)
        integrationThirstDehydrationThreshold = config.getDouble("stamina.integration.thirst.dehydration-threshold", 20.0).coerceAtLeast(0.0)
        integrationThirstRecoveryMultiplier = config.getDouble("stamina.integration.thirst.recovery-multiplier", 0.5).coerceAtLeast(0.0)

        // 系统联动 - wetness
        integrationWetnessEnabled = config.getBoolean("stamina.integration.wetness.enabled", true)
        integrationWetnessThreshold = config.getDouble("stamina.integration.wetness.threshold", 80.0).coerceAtLeast(0.0)
        integrationWetnessConsumptionMultiplier = config.getDouble("stamina.integration.wetness.consumption-multiplier", 1.5).coerceAtLeast(0.0)

        // 系统联动 - food
        integrationFoodEnabled = config.getBoolean("stamina.integration.food.enabled", true)
        integrationFoodFullSaturationBonus = config.getDouble("stamina.integration.food.full-saturation-bonus", 1.2).coerceAtLeast(0.0)

        // 系统联动 - weather
        integrationWeatherEnabled = config.getBoolean("stamina.integration.weather.enabled", true)
        integrationWeatherExtremeMultiplier = config.getDouble("stamina.integration.weather.extreme-multiplier", 2.0).coerceAtLeast(0.0)

        // 系统联动 - season
        integrationSeasonEnabled = config.getBoolean("stamina.integration.season.enabled", true)
        integrationSeasonWinterMultiplier = config.getDouble("stamina.integration.season.winter-multiplier", 1.5).coerceAtLeast(0.0)
        integrationSeasonSummerMultiplier = config.getDouble("stamina.integration.season.summer-multiplier", 1.2).coerceAtLeast(0.0)

        // 确保惩罚阈值顺序正确：tired > exhausted > depleted
        penaltyExhaustedThreshold = penaltyExhaustedThreshold.coerceAtMost(penaltyTiredThreshold)
        penaltyDepletedThreshold = penaltyDepletedThreshold.coerceAtMost(penaltyExhaustedThreshold)
    }
}
