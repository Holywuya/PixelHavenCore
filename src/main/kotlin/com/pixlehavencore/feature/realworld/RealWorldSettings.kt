package com.pixlehavencore.feature.realworld

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object RealWorldSettings {

    @Config("feature/realworld/realworld.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = false
        private set

    var tickIntervalSeconds: Int = 2
        private set

    var seasonDurationTicks: Long = 12096000L
        private set
    var seasonTransitionProgress: Double = 0.1
        private set

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
    var rainRestorePerMinute: Double = 2.0
        private set
    var seaWaterRestore: Double = 10.0
        private set
    var seaWaterNauseaChance: Double = 1.0
        private set
    var riverNauseaChance: Double = 0.1
        private set

    var hudActionBarFormat: String = "&c{temp}°C  &b{hydration}/100  &f{weather}  &a{season}"
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

    var autoSaveIntervalMinutes: Int = 5
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", false)

        tickIntervalSeconds = config.getInt("tick-interval-seconds", 2).coerceAtLeast(1)

        seasonDurationTicks = config.getLong("season.duration-ticks", 12096000L).coerceAtLeast(1L)
        seasonTransitionProgress = config.getDouble("season.transition-progress", 0.1).coerceIn(0.0, 1.0)

        weatherDecisionIntervalSeconds = config.getInt("weather.decision-interval-seconds", 300).coerceAtLeast(1)
        weatherPersistenceChance = config.getDouble("weather.persistence-chance", 0.6).coerceIn(0.0, 1.0)
        extremeWarningSeconds = config.getInt("weather.extreme.warning-seconds", 30).coerceAtLeast(0)
        extremeGracePeriodSeconds = config.getInt("weather.extreme.grace-period-seconds", 10).coerceAtLeast(0)
        extremeDamageIntervalSeconds = config.getInt("weather.extreme.damage-interval-seconds", 3).coerceAtLeast(1)
        extremeBaseDamageHearts = config.getDouble("weather.extreme.base-damage-hearts", 2.0).coerceAtLeast(0.0)

        comfortMin = config.getDouble("temperature.comfort-min", 15.0)
        comfortMax = config.getDouble("temperature.comfort-max", 36.0)
        altitudeThresholdY = config.getInt("temperature.altitude-threshold-y", 80)
        altitudeDropPerBlock = config.getDouble("temperature.altitude-drop-per-block", 0.5).coerceAtLeast(0.0)
        heatSourceScanIntervalSeconds = config.getInt("temperature.scan-interval-seconds", 5).coerceAtLeast(1)
        maxChangePerTick = config.getDouble("temperature.max-change-per-tick", 0.5).coerceAtLeast(0.0)
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
        rainRestorePerMinute = config.getDouble("thirst.water-sources.rain-per-minute", 2.0).coerceAtLeast(0.0)
        seaWaterRestore = config.getDouble("thirst.water-sources.sea-water", 10.0).coerceAtLeast(0.0)
        seaWaterNauseaChance = config.getDouble("thirst.water-quality.sea-water-nausea-chance", 1.0).coerceIn(0.0, 1.0)
        riverNauseaChance = config.getDouble("thirst.water-quality.river-nausea-chance", 0.1).coerceIn(0.0, 1.0)

        hudActionBarFormat = config.getString("hud.actionbar-format") ?: "&c{temp}°C  &b{hydration}/100  &f{weather}  &a{season}"
        hudRefreshIntervalSeconds = config.getInt("hud.refresh-interval-seconds", 2).coerceAtLeast(1)
        hudBossBarEnabled = config.getBoolean("hud.bossbar-enabled", true)
        hudBossBarTitleHeat = config.getString("hud.bossbar-title-heat") ?: "&c严重过热！寻找阴凉处！"
        hudBossBarTitleCold = config.getString("hud.bossbar-title-cold") ?: "&b严重过冷！寻找热源！"
        hudBossBarTitleThirst = config.getString("hud.bossbar-title-thirst") ?: "&e严重脱水！寻找水源！"

        autoSaveIntervalMinutes = config.getInt("storage.auto-save-interval-minutes", 5).coerceAtLeast(1)

        comfortMax = comfortMax.coerceAtLeast(comfortMin)
        heatThreshold = heatThreshold.coerceAtMost(severeHeatThreshold)
        coldMildThreshold = coldMildThreshold.coerceAtMost(heatThreshold)
        coldThreshold = coldThreshold.coerceAtMost(coldMildThreshold)
        severeColdThreshold = severeColdThreshold.coerceAtMost(coldThreshold)
        thirstThirsty = thirstThirsty.coerceAtMost(thirstFull)
        thirstSevere = thirstSevere.coerceAtMost(thirstThirsty)
    }
}
