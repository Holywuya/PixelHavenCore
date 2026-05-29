package com.pixlehavencore.feature.realworld.stamina

import org.bukkit.Material
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object StaminaSettings {

    @Config("feature/realworld/stamina.yml")
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
        enabled = config.getBoolean("enabled", true)
        maxStamina = config.getDouble("max-stamina", 100.0).coerceAtLeast(1.0)
        baseConsumptionRate = config.getDouble("base-consumption-rate", 0.05).coerceAtLeast(0.0)
        baseRecoveryRate = config.getDouble("base-recovery-rate", 0.3).coerceAtLeast(0.0)
        maxMultiplier = config.getDouble("max-multiplier", 5.0).coerceAtLeast(1.0)
        sprintMultiplier = config.getDouble("continuous.sprint-multiplier", 2.0).coerceAtLeast(0.0)
        swimMultiplier = config.getDouble("continuous.swim-multiplier", 2.5).coerceAtLeast(0.0)
        climbMultiplier = config.getDouble("continuous.climb-multiplier", 2.0).coerceAtLeast(0.0)
        attackCost = config.getDouble("actions.attack-cost", 0.15).coerceAtLeast(0.0)
        mineCost = config.getDouble("actions.mine-cost", 0.075).coerceAtLeast(0.0)
        useToolCost = config.getDouble("actions.use-tool-cost", 0.1).coerceAtLeast(0.0)
        underwaterMultiplier = config.getDouble("special.underwater-multiplier", 4.0).coerceAtLeast(0.0)
        highAltitudeMultiplier = config.getDouble("special.high-altitude-multiplier", 2.0).coerceAtLeast(0.0)
        highAltitudeY = config.getInt("special.high-altitude-y", 120)
        idleEnabled = config.getBoolean("recovery.idle.enabled", true)
        idleDelaySeconds = config.getDouble("recovery.idle.delay-seconds", 3.0).coerceAtLeast(0.0)
        idleMultiplier = config.getDouble("recovery.idle.multiplier", 1.0).coerceAtLeast(0.0)
        idleBlockedInExtremeTemp = config.getBoolean("recovery.idle.blocked-in-extreme-temperature", true)
        idleBlockedUnderwater = config.getBoolean("recovery.idle.blocked-underwater", true)
        idleBlockedWhenAttacked = config.getBoolean("recovery.idle.blocked-when-attacked", true)
        foodEnabled = config.getBoolean("recovery.food.enabled", true)
        hungerToStaminaRatio = config.getDouble("recovery.food.hunger-to-stamina-ratio", 2.0).coerceAtLeast(0.0)
        rottenFoodPenalty = config.getDouble("recovery.food.rotten-food-penalty", 0.5).coerceIn(0.0, 1.0)
        foodCooldownSeconds = config.getDouble("recovery.food.cooldown-seconds", 1.0).coerceAtLeast(0.0)
        drinkEnabled = config.getBoolean("recovery.drink.enabled", true)
        hydrationToStaminaRatio = config.getDouble("recovery.drink.hydration-to-stamina-ratio", 0.5).coerceAtLeast(0.0)
        sleepEnabled = config.getBoolean("recovery.sleep.enabled", true)
        outdoorRecoveryPercent = config.getDouble("recovery.sleep.outdoor-recovery-percent", 50.0).coerceIn(0.0, 100.0)
        sleepBlockedInExtremeTemp = config.getBoolean("recovery.sleep.blocked-in-extreme-temperature", true)
        specialItemsEnabled = config.getBoolean("recovery.special-items.enabled", true)
        specialItems = config.getConfigurationSection("recovery.special-items.items")
            ?.getKeys(false)
            ?.mapNotNull { key ->
                val material = runCatching { Material.valueOf(key.uppercase()) }.getOrNull()
                val amount = config.getDouble("recovery.special-items.items.$key")
                if (material != null && amount > 0) material to amount else null
            }
            ?.toMap()
            ?: emptyMap()
        tiredThreshold = config.getDouble("penalties.tired.threshold", 60.0).coerceIn(0.0, 100.0)
        tiredSpeedMultiplier = config.getDouble("penalties.tired.speed-multiplier", 0.85).coerceIn(0.0, 1.0)
        exhaustedThreshold = config.getDouble("penalties.exhausted.threshold", 30.0).coerceIn(0.0, tiredThreshold)
        exhaustedSpeedMultiplier = config.getDouble("penalties.exhausted.speed-multiplier", 0.70).coerceIn(0.0, 1.0)
        exhaustedMiningFatigueAmplifier = config.getInt("penalties.exhausted.mining-fatigue-amplifier", 0).coerceAtLeast(0)
        exhaustedWeaknessAmplifier = config.getInt("penalties.exhausted.weakness-amplifier", 0).coerceAtLeast(0)
        depletedThreshold = config.getDouble("penalties.depleted.threshold", 10.0).coerceIn(0.0, exhaustedThreshold)
        depletedSpeedMultiplier = config.getDouble("penalties.depleted.speed-multiplier", 0.50).coerceIn(0.0, 1.0)
        depletedMiningFatigueAmplifier = config.getInt("penalties.depleted.mining-fatigue-amplifier", 1).coerceAtLeast(0)
        depletedWeaknessAmplifier = config.getInt("penalties.depleted.weakness-amplifier", 1).coerceAtLeast(0)
        effectDurationSeconds = config.getInt("penalties.effect-duration-seconds", 3).coerceAtLeast(1)
        msgEnterTired = config.getString("messages.enter-tired") ?: msgEnterTired
        msgEnterExhausted = config.getString("messages.enter-exhausted") ?: msgEnterExhausted
        msgEnterDepleted = config.getString("messages.enter-depleted") ?: msgEnterDepleted
        msgDepletedReminder = config.getString("messages.depleted-reminder") ?: msgDepletedReminder
        msgRecoveredFromTired = config.getString("messages.recovered-from-tired") ?: msgRecoveredFromTired
        msgRecoveredFromExhausted = config.getString("messages.recovered-from-exhausted") ?: msgRecoveredFromExhausted
        msgRecoveredFromDepleted = config.getString("messages.recovered-from-depleted") ?: msgRecoveredFromDepleted
        depletedReminderCooldownSeconds = config.getDouble("messages.depleted-reminder-cooldown-seconds", 30.0).coerceAtLeast(1.0)
        bossBarEnabled = config.getBoolean("hud.bossbar-enabled", true)
        bossBarTitleExhausted = config.getString("hud.bossbar-title-exhausted") ?: bossBarTitleExhausted
        bossBarTitleDepleted = config.getString("hud.bossbar-title-depleted") ?: bossBarTitleDepleted
        bossBarColorExhausted = config.getString("hud.bossbar-color-exhausted") ?: "YELLOW"
        bossBarColorDepleted = config.getString("hud.bossbar-color-depleted") ?: "RED"
        bossBarStyle = config.getString("hud.bossbar-style") ?: "SOLID"
        integrationTemperatureEnabled = config.getBoolean("integration.temperature.enabled", true)
        integrationTemperatureExtremeMultiplier = config.getDouble("integration.temperature.extreme-multiplier", 1.5).coerceAtLeast(1.0)
        integrationTemperatureMildMultiplier = config.getDouble("integration.temperature.mild-multiplier", 1.2).coerceAtLeast(1.0)
        integrationFractureEnabled = config.getBoolean("integration.fracture.enabled", true)
        integrationFractureThreshold = config.getDouble("integration.fracture.threshold", 20.0).coerceAtLeast(0.0)
        integrationFractureConsumptionMultiplier = config.getDouble("integration.fracture.consumption-multiplier", 1.3).coerceAtLeast(1.0)
        integrationFractureMaxStaminaReduction = config.getDouble("integration.fracture.max-stamina-reduction", 30.0).coerceAtLeast(0.0)
        integrationThirstEnabled = config.getBoolean("integration.thirst.enabled", true)
        integrationThirstDehydrationThreshold = config.getDouble("integration.thirst.dehydration-threshold", 30.0).coerceAtLeast(0.0)
        integrationThirstRecoveryMultiplier = config.getDouble("integration.thirst.recovery-multiplier", 0.5).coerceIn(0.0, 1.0)
        integrationWetnessEnabled = config.getBoolean("integration.wetness.enabled", true)
        integrationWetnessThreshold = config.getDouble("integration.wetness.threshold", 0.7).coerceIn(0.0, 1.0)
        integrationWetnessConsumptionMultiplier = config.getDouble("integration.wetness.consumption-multiplier", 1.2).coerceAtLeast(1.0)
        integrationFoodEnabled = config.getBoolean("integration.food.enabled", true)
        integrationFoodFullSaturationBonus = config.getDouble("integration.food.full-saturation-bonus", 1.25).coerceAtLeast(1.0)
        integrationSeasonEnabled = config.getBoolean("integration.season.enabled", true)
        integrationSeasonWinterMultiplier = config.getDouble("integration.season.winter-multiplier", 1.1).coerceAtLeast(1.0)
        integrationSeasonSummerMultiplier = config.getDouble("integration.season.summer-multiplier", 1.05).coerceAtLeast(1.0)
    }
}
