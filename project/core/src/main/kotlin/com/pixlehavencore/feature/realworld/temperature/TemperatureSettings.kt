package com.pixlehavencore.feature.realworld.temperature

import org.bukkit.Material
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object TemperatureSettings {

    @Config("feature/realworld/temperature.yml")
    private lateinit var config: Configuration

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
    var absorptionRate: Double = 0.1
        private set
    var shelterGlassCountsAsShelter: Boolean = false
        private set
    var shelterLeavesCountAsShelter: Boolean = false
        private set
    var shelterHorizontalRadius: Int = 1
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
    var temperatureBlocks: Map<Material, Double> = emptyMap()
        private set
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
    var blockDecayFactor: Double = 0.5
        private set

    private const val MAX_SHELTER_HORIZONTAL_RADIUS = 2

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        comfortMin = config.getDouble("comfort-min", 15.0)
        comfortMax = config.getDouble("comfort-max", 36.0)
        altitudeThresholdY = config.getInt("altitude-threshold-y", 80)
        altitudeDropPerBlock = config.getDouble("altitude-drop-per-block", 0.5).coerceAtLeast(0.0)
        heatSourceScanIntervalSeconds = config.getInt("scan-interval-seconds", 5).coerceAtLeast(1)
        maxChangePerTick = config.getDouble("max-change-per-tick", 0.5).coerceAtLeast(0.0)
        absorptionRate = config.getDouble("temperature.absorption-rate", 0.1).coerceAtLeast(0.01)
        shelterGlassCountsAsShelter = config.getBoolean("shelter.glass-counts-as-shelter", false)
        shelterLeavesCountAsShelter = config.getBoolean("shelter.leaves-counts-as-shelter", false)
        shelterHorizontalRadius = config.getInt("shelter.horizontal-radius", 1).coerceIn(0, MAX_SHELTER_HORIZONTAL_RADIUS)
        armorBonusLeather = config.getDouble("armor-bonus.leather", 5.0)
        armorBonusNetherite = config.getDouble("armor-bonus.netherite", 10.0)
        severeHeatThreshold = config.getDouble("thresholds.severe-heat", 42.0)
        heatThreshold = config.getDouble("thresholds.heat", 36.0)
        coldMildThreshold = config.getDouble("thresholds.cold-mild", 15.0)
        coldThreshold = config.getDouble("thresholds.cold", 5.0)
        severeColdThreshold = config.getDouble("thresholds.severe-cold", -5.0)
        frostOverlayColdIntensity = config.getInt("frost-overlay.cold-intensity", 100).coerceIn(0, 299)
        frostOverlaySevereColdIntensity = config.getInt("frost-overlay.severe-cold-intensity", 250).coerceIn(0, 299)
        heatOverlayHeatIntensity = config.getDouble("heat-overlay.heat-intensity", 0.3).coerceIn(0.0, 1.0)
        heatOverlaySevereIntensity = config.getDouble("heat-overlay.severe-intensity", 0.8).coerceIn(0.0, 1.0)
        wetnessRateSubmerge = config.getDouble("wetness.rate-submerge", 0.05).coerceAtLeast(0.0)
        wetnessRateRain = config.getDouble("wetness.rate-rain", 0.01).coerceAtLeast(0.0)
        wetnessDryRate = config.getDouble("wetness.dry-rate", 0.005).coerceAtLeast(0.0)
        temperatureBlocks = config.getConfigurationSection("temperature-blocks")
            ?.getKeys(false)
            ?.mapNotNull { key ->
                val material = runCatching { Material.valueOf(key.uppercase()) }.getOrNull()
                val temp = config.getDouble("temperature-blocks.$key")
                if (material != null) material to temp else null
            }
            ?.toMap()
            ?: emptyMap()

        comfortMax = comfortMax.coerceAtLeast(comfortMin)
        heatThreshold = heatThreshold.coerceAtMost(severeHeatThreshold)
        coldMildThreshold = coldMildThreshold.coerceAtMost(heatThreshold)
        coldThreshold = coldThreshold.coerceAtMost(coldMildThreshold)
        severeColdThreshold = severeColdThreshold.coerceAtMost(coldThreshold)

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
        blockDecayFactor = config.getDouble("temperature-blocks.decay-factor", 0.5).coerceIn(0.1, 0.9)
    }
}
