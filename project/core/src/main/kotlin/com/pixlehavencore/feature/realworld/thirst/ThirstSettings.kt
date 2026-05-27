package com.pixlehavencore.feature.realworld.thirst

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object ThirstSettings {

    @Config("feature/realworld/thirst.yml")
    private lateinit var config: Configuration

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

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()

        baseThirstRatePerMinute = config.getDouble("base-rate-per-minute", 0.5).coerceAtLeast(0.0)
        sprintMultiplier = config.getDouble("sprint-multiplier", 2.0).coerceAtLeast(0.0)
        submergeMultiplier = config.getDouble("submerge-multiplier", 0.1).coerceAtLeast(0.0)
        thirstAltitudeThresholdY = config.getInt("altitude-threshold-y", 100)
        thirstAltitudeMultiplier = config.getDouble("altitude-multiplier", 1.2).coerceAtLeast(0.0)
        tempDeviationPercentPerDegree = config.getDouble("temperature-deviation-percent-per-degree", 2.0).coerceAtLeast(0.0)

        thirstFull = config.getDouble("thresholds.full", 60.0)
        thirstThirsty = config.getDouble("thresholds.thirsty", 30.0)
        thirstSevere = config.getDouble("thresholds.severe", 10.0)

        waterBottleRestore = config.getDouble("water-sources.water-bottle", 30.0).coerceAtLeast(0.0)
        waterSourceRestore = config.getDouble("water-sources.right-click-source", 20.0).coerceAtLeast(0.0)
        drinkerRestore = config.getDouble("water-sources.drinker", 40.0).coerceAtLeast(0.0)
        rainRestorePerMinute = config.getDouble("water-sources.rain-per-minute", 2.0).coerceAtLeast(0.0)
        seaWaterRestore = config.getDouble("water-sources.sea-water", 10.0).coerceAtLeast(0.0)
        drinkerCooldownSeconds = config.getInt("drinker-cooldown-seconds", 2).coerceAtLeast(0)
        seaWaterNauseaChance = config.getDouble("water-quality.sea-water-nausea-chance", 1.0).coerceIn(0.0, 1.0)
        riverNauseaChance = config.getDouble("water-quality.river-nausea-chance", 0.1).coerceIn(0.0, 1.0)

        thirstThirsty = thirstThirsty.coerceAtMost(thirstFull)
        thirstSevere = thirstSevere.coerceAtMost(thirstThirsty)
    }
}
