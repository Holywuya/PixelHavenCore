package com.pixlehavencore.feature.realworld

import org.bukkit.Material
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object RealWorldSettings {

    @Config("feature/realworld/realworld.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = false
        private set

    var timeControlEnabled: Boolean = false
        private set

    var tickIntervalSeconds: Int = 2
        private set

    var seasonDurationDays: Int = 7
        private set

    val seasonDurationTicks: Long
        get() {
            val ticksPerDay = if (timeControlEnabled) 72000L else 24000L
            return seasonDurationDays.toLong() * ticksPerDay
        }

    var seasonTransitionProgress: Double = 0.1
        private set

    private const val TIME_CONTROL_MULTIPLIER = 3.0

    var weatherDecisionIntervalSeconds: Int = 300
        private set
    var weatherPersistenceChance: Double = 0.6
        private set
    var extremeWarningSeconds: Int = 30
        private set
    var extremeGracePeriodSeconds: Int = 10
        private set
    var extremeDamageIntervalSeconds: Int = 3
        private set
    var extremeBaseDamageHearts: Double = 2.0
        private set
    var visibilityEffectDurationSeconds: Int = 3
        private set
    var fogBlindnessAmplifier: Int = 0
        private set
    var blizzardBlindnessAmplifier: Int = 0
        private set
    var sandstormBlindnessAmplifier: Int = 1
        private set

    var localWeatherEnabled: Boolean = false
        private set
    var localWeatherNoiseFrequency: Float = 0.015f
        private set
    var localWeatherChangeSpeed: Float = 0.001f
        private set
    var localWeatherCacheEnabled: Boolean = true
        private set
    var localWeatherCacheMaxSize: Int = 1000
        private set

    var comfortMin: Double = 15.0
        private set
    var comfortMax: Double = 36.0
        private set
    var altitudeThresholdY: Int = 80
        private set
    var altitudeDropPerBlock: Double = 0.5
        private set
    var heatSourceScanIntervalSeconds: Int = 5
        private set
    var maxChangePerTick: Double = 0.5
        private set
    var shelterGlassCountsAsShelter: Boolean = false
        private set
    var shelterLeavesCountAsShelter: Boolean = false
        private set
    var shelterHorizontalRadius: Int = 1
        private set

    private const val MAX_SHELTER_HORIZONTAL_RADIUS = 2
    var armorBonusLeather: Double = 5.0
        private set
    var armorBonusNetherite: Double = 10.0
        private set

    var severeHeatThreshold: Double = 42.0
        private set
    var heatThreshold: Double = 36.0
        private set
    var coldMildThreshold: Double = 15.0
        private set
    var coldThreshold: Double = 5.0
        private set
    var severeColdThreshold: Double = -5.0
        private set

    var baseThirstRatePerMinute: Double = 0.5
        private set
    var sprintMultiplier: Double = 2.0
        private set
    var submergeMultiplier: Double = 0.1
        private set
    var thirstAltitudeThresholdY: Int = 100
        private set
    var thirstAltitudeMultiplier: Double = 1.2
        private set
    var tempDeviationPercentPerDegree: Double = 2.0
        private set

    var thirstFull: Double = 60.0
        private set
    var thirstThirsty: Double = 30.0
        private set
    var thirstSevere: Double = 10.0
        private set

    var waterBottleRestore: Double = 30.0
        private set
    var waterSourceRestore: Double = 20.0
        private set
    var drinkerRestore: Double = 40.0
        private set
    var rainRestorePerMinute: Double = 2.0
        private set
    var seaWaterRestore: Double = 10.0
        private set
    var drinkerCooldownSeconds: Int = 2
        private set
    var seaWaterNauseaChance: Double = 1.0
        private set
    var riverNauseaChance: Double = 0.1
        private set

    var hudActionBarFormat: String = "&c{temp}°C  &b{hydration}/100  &9{wetness}%  {sheltered}  &f{weather}  &a{season}"
        private set
    var hudShelteredIndicator: String = "&a🏠"
        private set
    var hudUnshelteredIndicator: String = "&7☁"
        private set
    var hudRefreshIntervalSeconds: Int = 2
        private set
    var hudBossBarEnabled: Boolean = true
        private set
    var hudBossBarTitleHeat: String = "&c严重过热！寻找阴凉处！"
        private set
    var hudBossBarTitleCold: String = "&b严重过冷！寻找热源！"
        private set
    var hudBossBarTitleThirst: String = "&e严重脱水！寻找水源！"
        private set

    var frostOverlayColdIntensity: Int = 100
        private set
    var frostOverlaySevereColdIntensity: Int = 250
        private set

    var heatOverlayHeatIntensity: Double = 0.3
        private set
    var heatOverlaySevereIntensity: Double = 0.8
        private set

    var wetnessRateSubmerge: Double = 0.05
        private set
    var wetnessRateRain: Double = 0.01
        private set
    var wetnessDryRate: Double = 0.005
        private set

    var fractureEnabled: Boolean = true
        private set
    var fractureMinFallDamage: Double = 4.0
        private set
    var fractureDamageMultiplier: Double = 5.0
        private set
    var fractureRecoveryRate: Double = 2.0
        private set
    var fractureBandageHealAmount: Double = 30.0
        private set
    var fractureBandageMaterial: Material = Material.PAPER
        private set
    var fractureCastMaterial: Material = Material.CLAY_BALL
        private set
    var fractureMildThreshold: Double = 20.0
        private set
    var fractureModerateThreshold: Double = 50.0
        private set
    var fractureSevereThreshold: Double = 80.0
        private set

    var temperatureBlocks: Map<Material, Double> = emptyMap()
        private set

    var autoSaveIntervalMinutes: Int = 5
        private set

    fun init() {
        StaminaSettings.init()
        reload()
    }

    private fun adjustForTimeControl(baseValue: Int): Int {
        if (!timeControlEnabled) return baseValue
        return (baseValue * TIME_CONTROL_MULTIPLIER).toInt()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", false)

        timeControlEnabled = config.getBoolean("time-control.enabled", false)

        tickIntervalSeconds = config.getInt("tick-interval-seconds", 2).coerceAtLeast(1)

        seasonDurationDays = config.getInt("season.duration-days", 7).coerceAtLeast(1)
        seasonTransitionProgress = config.getDouble("season.transition-progress", 0.1).coerceIn(0.0, 1.0)

        val baseWeatherDecisionInterval = config.getInt("weather.decision-interval-seconds", 300).coerceAtLeast(1)
        weatherDecisionIntervalSeconds = adjustForTimeControl(baseWeatherDecisionInterval)
        weatherPersistenceChance = config.getDouble("weather.persistence-chance", 0.6).coerceIn(0.0, 1.0)
        extremeWarningSeconds = config.getInt("weather.extreme.warning-seconds", 30).coerceAtLeast(0)
        extremeGracePeriodSeconds = config.getInt("weather.extreme.grace-period-seconds", 10).coerceAtLeast(0)
        extremeDamageIntervalSeconds = config.getInt("weather.extreme.damage-interval-seconds", 3).coerceAtLeast(1)
        extremeBaseDamageHearts = config.getDouble("weather.extreme.base-damage-hearts", 2.0).coerceAtLeast(0.0)
        visibilityEffectDurationSeconds = config.getInt("weather.visibility.effect-duration-seconds", 3).coerceAtLeast(1)
        fogBlindnessAmplifier = config.getInt("weather.visibility.fog.blindness-amplifier", 0).coerceAtLeast(0)
        blizzardBlindnessAmplifier = config.getInt("weather.visibility.blizzard.blindness-amplifier", 1).coerceAtLeast(0)
        sandstormBlindnessAmplifier = config.getInt("weather.visibility.sandstorm.blindness-amplifier", 2).coerceAtLeast(0)

        localWeatherEnabled = config.getBoolean("weather.local.enabled", false)
        localWeatherNoiseFrequency = config.getDouble("weather.local.noise-frequency", 0.015).toFloat().coerceIn(0.001f, 0.1f)
        localWeatherChangeSpeed = config.getDouble("weather.local.change-speed", 0.001).toFloat().coerceIn(0.0001f, 0.01f)
        localWeatherCacheEnabled = config.getBoolean("weather.local.cache-enabled", true)
        localWeatherCacheMaxSize = config.getInt("weather.local.cache-max-size", 1000).coerceIn(100, 10000)

        comfortMin = config.getDouble("temperature.comfort-min", 15.0)
        comfortMax = config.getDouble("temperature.comfort-max", 36.0)
        altitudeThresholdY = config.getInt("temperature.altitude-threshold-y", 80)
        altitudeDropPerBlock = config.getDouble("temperature.altitude-drop-per-block", 0.5).coerceAtLeast(0.0)
        heatSourceScanIntervalSeconds = config.getInt("temperature.scan-interval-seconds", 5).coerceAtLeast(1)
        maxChangePerTick = config.getDouble("temperature.max-change-per-tick", 0.5).coerceAtLeast(0.0)
        shelterGlassCountsAsShelter = config.getBoolean("temperature.shelter.glass-counts-as-shelter", false)
        shelterLeavesCountAsShelter = config.getBoolean("temperature.shelter.leaves-count-as-shelter", false)
        shelterHorizontalRadius = config.getInt("temperature.shelter.horizontal-radius", 1)
            .coerceIn(0, MAX_SHELTER_HORIZONTAL_RADIUS)
        armorBonusLeather = config.getDouble("temperature.armor-bonus.leather", 5.0)
        armorBonusNetherite = config.getDouble("temperature.armor-bonus.netherite", 10.0)

        severeHeatThreshold = config.getDouble("temperature.thresholds.severe-heat", 42.0)
        heatThreshold = config.getDouble("temperature.thresholds.heat", 36.0)
        coldMildThreshold = config.getDouble("temperature.thresholds.cold-mild", 15.0)
        coldThreshold = config.getDouble("temperature.thresholds.cold", 5.0)
        severeColdThreshold = config.getDouble("temperature.thresholds.severe-cold", -5.0)

        baseThirstRatePerMinute = config.getDouble("thirst.base-rate-per-minute", 0.5).coerceAtLeast(0.0)
        sprintMultiplier = config.getDouble("thirst.sprint-multiplier", 2.0).coerceAtLeast(0.0)
        submergeMultiplier = config.getDouble("thirst.submerge-multiplier", 0.1).coerceAtLeast(0.0)
        thirstAltitudeThresholdY = config.getInt("thirst.altitude-threshold-y", 100)
        thirstAltitudeMultiplier = config.getDouble("thirst.altitude-multiplier", 1.2).coerceAtLeast(0.0)
        tempDeviationPercentPerDegree = config.getDouble("thirst.temperature-deviation-percent-per-degree", 2.0).coerceAtLeast(0.0)

        thirstFull = config.getDouble("thirst.thresholds.full", 60.0)
        thirstThirsty = config.getDouble("thirst.thresholds.thirsty", 30.0)
        thirstSevere = config.getDouble("thirst.thresholds.severe", 10.0)

        waterBottleRestore = config.getDouble("thirst.water-sources.water-bottle", 30.0).coerceAtLeast(0.0)
        waterSourceRestore = config.getDouble("thirst.water-sources.right-click-source", 20.0).coerceAtLeast(0.0)
        drinkerRestore = config.getDouble("thirst.water-sources.drinker", 40.0).coerceAtLeast(0.0)
        rainRestorePerMinute = config.getDouble("thirst.water-sources.rain-per-minute", 2.0).coerceAtLeast(0.0)
        seaWaterRestore = config.getDouble("thirst.water-sources.sea-water", 10.0).coerceAtLeast(0.0)
        drinkerCooldownSeconds = config.getInt("thirst.drinker-cooldown-seconds", 2).coerceAtLeast(0)
        seaWaterNauseaChance = config.getDouble("thirst.water-quality.sea-water-nausea-chance", 1.0).coerceIn(0.0, 1.0)
        riverNauseaChance = config.getDouble("thirst.water-quality.river-nausea-chance", 0.1).coerceIn(0.0, 1.0)

        hudActionBarFormat = config.getString("hud.actionbar-format") ?: "&c{temp}°C  &b{hydration}/100  &9{wetness}%  {sheltered}  &f{weather}  &a{season}"
        hudShelteredIndicator = config.getString("hud.sheltered-indicator") ?: "&a🏠"
        hudUnshelteredIndicator = config.getString("hud.unsheltered-indicator") ?: "&7☁"
        hudRefreshIntervalSeconds = config.getInt("hud.refresh-interval-seconds", 2).coerceAtLeast(1)
        hudBossBarEnabled = config.getBoolean("hud.bossbar-enabled", true)
        hudBossBarTitleHeat = config.getString("hud.bossbar-title-heat") ?: "&c严重过热！寻找阴凉处！"
        hudBossBarTitleCold = config.getString("hud.bossbar-title-cold") ?: "&b严重过冷！寻找热源！"
        hudBossBarTitleThirst = config.getString("hud.bossbar-title-thirst") ?: "&e严重脱水！寻找水源！"

        frostOverlayColdIntensity = config.getInt("frost-overlay.cold-intensity", 100).coerceIn(0, 299)
        frostOverlaySevereColdIntensity = config.getInt("frost-overlay.severe-cold-intensity", 250).coerceIn(0, 299)

        heatOverlayHeatIntensity = config.getDouble("heat-overlay.heat-intensity", 0.3).coerceIn(0.0, 1.0)
        heatOverlaySevereIntensity = config.getDouble("heat-overlay.severe-intensity", 0.8).coerceIn(0.0, 1.0)

        wetnessRateSubmerge = config.getDouble("wetness.rate-submerge", 0.05).coerceAtLeast(0.0)
        wetnessRateRain = config.getDouble("wetness.rate-rain", 0.01).coerceAtLeast(0.0)
        wetnessDryRate = config.getDouble("wetness.dry-rate", 0.005).coerceAtLeast(0.0)

        fractureEnabled = config.getBoolean("fracture.enabled", true)
        fractureMinFallDamage = config.getDouble("fracture.min-fall-damage", 4.0).coerceAtLeast(0.0)
        fractureDamageMultiplier = config.getDouble("fracture.damage-multiplier", 5.0).coerceAtLeast(0.0)
        fractureRecoveryRate = config.getDouble("fracture.recovery-rate", 2.0).coerceAtLeast(0.0)
        fractureBandageHealAmount = config.getDouble("fracture.bandage-heal-amount", 30.0).coerceAtLeast(0.0)
        fractureBandageMaterial = runCatching {
            Material.valueOf((config.getString("fracture.bandage-material", "PAPER") ?: "PAPER").uppercase())
        }.getOrDefault(Material.PAPER)
        fractureCastMaterial = runCatching {
            Material.valueOf((config.getString("fracture.cast-material", "CLAY_BALL") ?: "CLAY_BALL").uppercase())
        }.getOrDefault(Material.CLAY_BALL)
        fractureMildThreshold = config.getDouble("fracture.thresholds.mild", 20.0).coerceAtLeast(0.0)
        fractureModerateThreshold = config.getDouble("fracture.thresholds.moderate", 50.0).coerceAtLeast(fractureMildThreshold)
        fractureSevereThreshold = config.getDouble("fracture.thresholds.severe", 80.0).coerceAtLeast(fractureModerateThreshold)

        temperatureBlocks = config.getConfigurationSection("temperature-blocks")
            ?.getKeys(false)
            ?.mapNotNull { key ->
                val material = runCatching { Material.valueOf(key.uppercase()) }.getOrNull()
                val temp = config.getDouble("temperature-blocks.$key")
                if (material != null) material to temp else null
            }
            ?.toMap()
            ?: emptyMap()

        autoSaveIntervalMinutes = config.getInt("storage.auto-save-interval-minutes", 5).coerceAtLeast(1)

        comfortMax = comfortMax.coerceAtLeast(comfortMin)
        heatThreshold = heatThreshold.coerceAtMost(severeHeatThreshold)
        coldMildThreshold = coldMildThreshold.coerceAtMost(heatThreshold)
        coldThreshold = coldThreshold.coerceAtMost(coldMildThreshold)
        severeColdThreshold = severeColdThreshold.coerceAtMost(coldThreshold)
        thirstThirsty = thirstThirsty.coerceAtMost(thirstFull)
        thirstSevere = thirstSevere.coerceAtMost(thirstThirsty)

        StaminaSettings.reload()
    }
}
