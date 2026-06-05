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
    var maxChangeBase: Double = 0.3
        private set
    var maxChangeDynamicScale: Double = 0.02
        private set
    var absorptionRate: Double = 0.1
        private set
    var shelterGlassCountsAsShelter: Boolean = false
        private set
    var shelterLeavesCountAsShelter: Boolean = false
        private set
    var shelterHorizontalRadius: Int = 1
        private set
    var armorInsulationLeather: Double = 0.08
        private set
    var armorInsulationChainmail: Double = 0.04
        private set
    var armorInsulationIron: Double = 0.06
        private set
    var armorInsulationGold: Double = 0.05
        private set
    var armorInsulationDiamond: Double = 0.10
        private set
    var armorInsulationNetherite: Double = 0.12
        private set
    var armorInsulationMax: Double = 0.7
        private set
    var protectionInsulationPerLevel: Double = 0.05
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
    var shelterCanopyInsulation: Double = 0.15
        private set
    var shelterBuildingInsulation: Double = 0.25
        private set
    var shelterLeavesCountAsCanopy: Boolean = true
        private set
    var blockDecayFactor: Double = 0.5
        private set
    var regulationEnabled: Boolean = true
        private set
    var regulationStrength: Double = 0.05
        private set
    var waterExitBlendThreshold: Double = 0.5
        private set
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
        maxChangeBase = config.getDouble("max-change.base", 0.3).coerceAtLeast(0.0)
        maxChangeDynamicScale = config.getDouble("max-change.dynamic-scale", 0.02).coerceAtLeast(0.0)
        absorptionRate = config.getDouble("temperature.absorption-rate", 0.1).coerceAtLeast(0.01)
        shelterGlassCountsAsShelter = config.getBoolean("shelter.glass-counts-as-shelter", false)
        shelterLeavesCountAsShelter = config.getBoolean("shelter.leaves-counts-as-shelter", false)
        shelterHorizontalRadius = config.getInt("shelter.horizontal-radius", 1).coerceIn(0, MAX_SHELTER_HORIZONTAL_RADIUS)
        armorInsulationLeather = config.getDouble("armor-insulation.leather", 0.08).coerceAtLeast(0.0)
        armorInsulationChainmail = config.getDouble("armor-insulation.chainmail", 0.04).coerceAtLeast(0.0)
        armorInsulationIron = config.getDouble("armor-insulation.iron", 0.06).coerceAtLeast(0.0)
        armorInsulationGold = config.getDouble("armor-insulation.gold", 0.05).coerceAtLeast(0.0)
        armorInsulationDiamond = config.getDouble("armor-insulation.diamond", 0.10).coerceAtLeast(0.0)
        armorInsulationNetherite = config.getDouble("armor-insulation.netherite", 0.12).coerceAtLeast(0.0)
        armorInsulationMax = config.getDouble("armor-insulation.max", 0.7).coerceIn(0.0, 1.0)
        protectionInsulationPerLevel = config.getDouble("armor-insulation.protection-per-level", 0.05).coerceAtLeast(0.0)
        severeHeatThreshold = config.getDouble("thresholds.severe-heat", 42.0)
        heatThreshold = config.getDouble("thresholds.heat", 36.0)
        coldMildThreshold = config.getDouble("thresholds.cold-mild", 15.0)
        coldThreshold = config.getDouble("thresholds.cold", 5.0)
        severeColdThreshold = config.getDouble("thresholds.severe-cold", -5.0)
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
        waterConductivityMultiplier = config.getDouble("water.thermal-conductivity", 4.0).coerceAtLeast(1.0)
        waterDepthCoolPer10Blocks = config.getDouble("water.depth-cool-per-10-blocks", 1.0).coerceAtLeast(0.0)
        waterMaxDepthCool = config.getDouble("water.max-depth-cool", 5.0).coerceAtLeast(0.0)
        waterSeasonLagRatio = config.getDouble("water.season-lag-ratio", 0.3).coerceIn(0.0, 1.0)
        shelterCanopyInsulation = config.getDouble("shelter.canopy-insulation", 0.15).coerceIn(0.0, 1.0)
        shelterBuildingInsulation = config.getDouble("shelter.building-insulation", 0.25).coerceIn(0.0, 1.0)
        shelterLeavesCountAsCanopy = config.getBoolean("shelter.leaves-count-as-canopy", true)
        blockDecayFactor = config.getDouble("temperature-blocks.decay-factor", 0.5).coerceIn(0.1, 0.9)
        regulationEnabled = config.getBoolean("regulation.enabled", true)
        regulationStrength = config.getDouble("regulation.strength", 0.05).coerceAtLeast(0.0)
        waterExitBlendThreshold = config.getDouble("water.exit-blend-threshold", 0.5).coerceIn(0.0, 1.0)
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
    }
}
