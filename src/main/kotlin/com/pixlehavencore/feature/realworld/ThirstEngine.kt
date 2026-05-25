package com.pixlehavencore.feature.realworld

import java.util.concurrent.ThreadLocalRandom
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

object ThirstEngine {

    fun compute(
        player: Player,
        state: PlayerEnvState,
        global: GlobalEnvState,
        tickIntervalSeconds: Int,
    ) {
        val settings = RealWorldSettings
        val intervalSeconds = tickIntervalSeconds.coerceAtLeast(0)
        val baseConsumption = settings.baseThirstRatePerMinute / 60.0 * intervalSeconds
        val seasonMultiplier = SeasonEngine.getHydrationMultiplier(global)
        val weatherMultiplier = global.weather.hydrationMultiplier
        val temperatureMultiplier = 1.0 + (
            computeTemperatureDeviation(state.temperature) * settings.tempDeviationPercentPerDegree / 100.0
            )
        val actionMultiplier = when {
            player.isSprinting -> settings.sprintMultiplier
            isInWater(player) -> settings.submergeMultiplier
            else -> 1.0
        }
        val altitudeMultiplier = if (player.location.blockY > settings.thirstAltitudeThresholdY) {
            settings.thirstAltitudeMultiplier
        } else {
            1.0
        }

        val consumption = baseConsumption *
            seasonMultiplier *
            weatherMultiplier *
            temperatureMultiplier *
            actionMultiplier *
            altitudeMultiplier

        var hydration = (state.hydration - consumption).coerceIn(0.0, 100.0)

        if (isRaining(player) && isExposedToRain(player) && !isInWater(player)) {
            val rainRestore = settings.rainRestorePerMinute / 60.0 * intervalSeconds
            hydration = (hydration + rainRestore).coerceIn(0.0, 100.0)
        }

        state.hydration = hydration
        state.thirstPhase = classifyThirst(state.hydration)
    }

    fun onWaterBottleConsume(state: PlayerEnvState) {
        state.hydration = (state.hydration + RealWorldSettings.waterBottleRestore).coerceIn(0.0, 100.0)
        state.thirstPhase = classifyThirst(state.hydration)
    }

    fun onRightClickWaterSource(player: Player, state: PlayerEnvState, block: Block) {
        if (block.type != Material.WATER) {
            return
        }

        val biomeName = block.biome.toString().lowercase()
        val isSeaWater = biomeName.contains("ocean")
        val settings = RealWorldSettings
        val restore = if (isSeaWater) settings.seaWaterRestore else settings.waterSourceRestore
        val nauseaChance = if (isSeaWater) settings.seaWaterNauseaChance else settings.riverNauseaChance

        state.hydration = (state.hydration + restore).coerceIn(0.0, 100.0)
        state.thirstPhase = classifyThirst(state.hydration)

        if (ThreadLocalRandom.current().nextDouble() >= nauseaChance) {
            return
        }

        PotionEffectType.NAUSEA?.let { effectType ->
            player.addPotionEffect(PotionEffect(effectType, 20 * 30, 0, false, false, false))
        }
        if (isSeaWater) {
            PotionEffectType.HUNGER?.let { effectType ->
                player.addPotionEffect(PotionEffect(effectType, 20 * 30, 0, false, false, false))
            }
        }
    }

    private fun computeTemperatureDeviation(temperature: Double): Double {
        return when {
            temperature < RealWorldSettings.comfortMin -> RealWorldSettings.comfortMin - temperature
            temperature > RealWorldSettings.comfortMax -> temperature - RealWorldSettings.comfortMax
            else -> 0.0
        }
    }

    private fun isInWater(player: Player): Boolean {
        val locationBlockType = player.location.block.type
        if (locationBlockType == Material.WATER || locationBlockType == Material.BUBBLE_COLUMN) {
            return true
        }

        val eyeBlockType = player.eyeLocation.block.type
        return eyeBlockType == Material.WATER || eyeBlockType == Material.BUBBLE_COLUMN
    }

    private fun isExposedToRain(player: Player): Boolean {
        val location = player.location
        val world = location.world ?: return false
        val highestBlockY = world.getHighestBlockYAt(location.blockX, location.blockZ)
        return location.blockY >= highestBlockY
    }

    private fun isRaining(player: Player): Boolean {
        val location = player.location
        val world = location.world ?: return false
        if (!world.hasStorm()) {
            return false
        }

        val biomeName = location.block.biome.toString().lowercase()
        return !biomeName.contains("snow") &&
            !biomeName.contains("ice") &&
            !biomeName.contains("frozen")
    }

    fun classifyThirst(hydration: Double): ThirstPhase {
        return when {
            hydration >= RealWorldSettings.thirstFull -> ThirstPhase.FULL
            hydration >= RealWorldSettings.thirstThirsty -> ThirstPhase.THIRSTY
            hydration >= RealWorldSettings.thirstSevere -> ThirstPhase.SEVERE_THIRST
            else -> ThirstPhase.DEHYDRATED
        }
    }
}
