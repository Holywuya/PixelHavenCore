package com.pixlehavencore.feature.realworld

import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.data.Lightable
import org.bukkit.entity.Player
import kotlin.math.abs

object TemperatureEngine {

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
        val timeModifier = SeasonEngine.getTimeTemperatureModifier(worldTime)
        val weatherModifier = global.weather.temperatureModifier
        val altitudeModifier = computeAltitudeModifier(location.blockY)

        state.heatSourceScanTimer -= tickIntervalSeconds.coerceAtLeast(0)
        if (state.heatSourceScanTimer <= 0.0) {
            state.nearHeatSource = scanHeatSource(player)
            val interval = RealWorldSettings.heatSourceScanIntervalSeconds.toDouble()
            while (state.heatSourceScanTimer <= 0.0) {
                state.heatSourceScanTimer += interval
            }
        }
        val heatSourceModifier = state.nearHeatSource?.modifier ?: 0.0

        state.isSheltered = checkSheltered(player)
        val shelteredModifier = if (state.isSheltered) 5.0 else 0.0
        val armorModifier = getArmorTemperatureBonus(player)

        val targetTemperature = biomeBaseTemperature +
            seasonModifier +
            timeModifier +
            weatherModifier +
            altitudeModifier +
            heatSourceModifier +
            shelteredModifier +
            armorModifier

        val maxChangePerTick = RealWorldSettings.maxChangePerTick.coerceAtLeast(0.0)
        val temperatureDifference = targetTemperature - state.temperature
        val change = when {
            maxChangePerTick <= 0.0 -> 0.0
            abs(temperatureDifference) <= maxChangePerTick -> temperatureDifference
            temperatureDifference > 0.0 -> maxChangePerTick
            else -> -maxChangePerTick
        }

        state.temperature += change
        state.temperaturePhase = classifyTemperature(state.temperature)
    }

    fun getBiomeBaseTemperature(biomeName: String): Double {
        val normalizedName = biomeName.lowercase()
        return when {
            normalizedName.contains("nether") || normalizedName.contains("basalt") || normalizedName.contains("crimson") || normalizedName.contains("warped") -> 40.0
            normalizedName.contains("desert") || normalizedName.contains("badlands") -> 35.0
            normalizedName.contains("savanna") -> 30.0
            normalizedName.contains("jungle") || normalizedName.contains("bamboo") -> 28.0
            normalizedName.contains("swamp") || normalizedName.contains("mangrove") -> 24.0
            normalizedName.contains("snow") || normalizedName.contains("ice") || normalizedName.contains("frozen") -> -10.0
            normalizedName.contains("beach") || normalizedName.contains("stony_shore") -> 18.0
            normalizedName.contains("plains") || normalizedName.contains("sunflower") || normalizedName.contains("meadow") || normalizedName.contains("mushroom") -> 15.0
            normalizedName.contains("dark_forest") -> 12.0
            normalizedName.contains("forest") || normalizedName.contains("birch") || normalizedName.contains("cherry") -> 14.0
            normalizedName.contains("ocean") || normalizedName.contains("river") -> 12.0
            normalizedName.contains("end") -> 5.0
            normalizedName.contains("peak") || normalizedName.contains("mountain") || normalizedName.contains("windswept") || normalizedName.contains("stony") -> 5.0
            normalizedName.contains("taiga") || normalizedName.contains("grove") -> 3.0
            else -> 15.0
        }
    }

    private fun scanHeatSource(player: Player): HeatSource? {
        val originBlock = player.location.block
        var nearestHeatSource: HeatSource? = null
        var nearestDistanceSquared: Int? = null

        for (source in HeatSource.entries) {
            val range = source.range
            for (x in -range..range) {
                for (y in -range..range) {
                    for (z in -range..range) {
                        val block = originBlock.getRelative(x, y, z)
                        if (!matchesHeatSource(block, source)) {
                            continue
                        }

                        val distanceSquared = x * x + y * y + z * z
                        if (nearestDistanceSquared == null || distanceSquared < nearestDistanceSquared) {
                            nearestDistanceSquared = distanceSquared
                            nearestHeatSource = source
                        }
                    }
                }
            }
        }

        return nearestHeatSource
    }

    private fun matchesHeatSource(block: Block, source: HeatSource): Boolean {
        return when (source) {
            HeatSource.LAVA -> block.type == Material.LAVA
            HeatSource.CAMPFIRE -> block.type == Material.CAMPFIRE && isLit(block)
            HeatSource.SOUL_CAMPFIRE -> block.type == Material.SOUL_CAMPFIRE && isLit(block)
            HeatSource.FURNACE -> (
                block.type == Material.FURNACE ||
                    block.type == Material.BLAST_FURNACE ||
                    block.type == Material.SMOKER
                ) && isLit(block)
            HeatSource.FIRE -> block.type == Material.FIRE
            HeatSource.ICE -> block.type == Material.ICE
            HeatSource.PACKED_ICE -> block.type == Material.PACKED_ICE
            HeatSource.BLUE_ICE -> block.type == Material.BLUE_ICE
            HeatSource.MAGMA_BLOCK -> block.type == Material.MAGMA_BLOCK
        }
    }

    private fun isLit(block: Block): Boolean {
        return (block.blockData as? Lightable)?.isLit == true
    }

    private fun checkSheltered(player: Player): Boolean {
        val world = player.world
        val eyeLocation = player.eyeLocation
        val highestBlockY = world.getHighestBlockYAt(eyeLocation.blockX, eyeLocation.blockZ)
        return highestBlockY >= eyeLocation.blockY
    }

    private fun getArmorTemperatureBonus(player: Player): Double {
        var totalBonus = 0.0
        for (armorPiece in player.inventory.armorContents) {
            val material = armorPiece?.type ?: continue
            totalBonus += when (material) {
                Material.LEATHER_HELMET,
                Material.LEATHER_CHESTPLATE,
                Material.LEATHER_LEGGINGS,
                Material.LEATHER_BOOTS,
                -> RealWorldSettings.armorBonusLeather / 4.0

                Material.NETHERITE_HELMET,
                Material.NETHERITE_CHESTPLATE,
                Material.NETHERITE_LEGGINGS,
                Material.NETHERITE_BOOTS,
                -> RealWorldSettings.armorBonusNetherite / 4.0

                else -> 0.0
            }
        }
        return totalBonus
    }

    fun classifyTemperature(temp: Double): TemperaturePhase {
        return when {
            temp >= RealWorldSettings.severeHeatThreshold -> TemperaturePhase.SEVERE_HEAT
            temp >= RealWorldSettings.heatThreshold -> TemperaturePhase.HEAT
            temp >= RealWorldSettings.coldMildThreshold -> TemperaturePhase.COMFORTABLE
            temp >= RealWorldSettings.coldThreshold -> TemperaturePhase.COLD_MILD
            temp >= RealWorldSettings.severeColdThreshold -> TemperaturePhase.COLD
            else -> TemperaturePhase.SEVERE_COLD
        }
    }

    private fun computeAltitudeModifier(blockY: Int): Double {
        val threshold = RealWorldSettings.altitudeThresholdY
        if (blockY <= threshold) {
            return 0.0
        }
        val exceededHeight = blockY - threshold
        return -exceededHeight * RealWorldSettings.altitudeDropPerBlock
    }
}
