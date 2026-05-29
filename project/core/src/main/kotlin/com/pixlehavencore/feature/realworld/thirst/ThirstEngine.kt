package com.pixlehavencore.feature.realworld.thirst

import com.pixlehavencore.feature.realworld.GlobalEnvState
import com.pixlehavencore.feature.realworld.PlayerEnvState
import com.pixlehavencore.feature.realworld.ThirstPhase
import com.pixlehavencore.feature.realworld.WeatherType
import com.pixlehavencore.feature.realworld.season.SeasonEngine
import com.pixlehavencore.feature.realworld.temperature.TemperatureSettings
import java.util.concurrent.ThreadLocalRandom
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

object ThirstEngine {

    enum class NaturalWaterSourceType {
        FRESH_WATER,
        SEA_WATER,
    }

    fun compute(
        player: Player,
        state: PlayerEnvState,
        global: GlobalEnvState,
        tickIntervalSeconds: Int,
    ) {
        val settings = ThirstSettings
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
        val altitudeMultiplier = if (player.location.blockY > settings.altitudeThresholdY) {
            settings.altitudeMultiplier
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

        // 复用 TemperatureEngine 已缓存的 isWeatherSheltered，避免重复调用 getHighestBlockYAt
        if (supportsRainHydration(global) && !state.isWeatherSheltered && !isInWater(player)) {
            val rainRestore = settings.rainRestorePerMinute / 60.0 * intervalSeconds
            hydration = (hydration + rainRestore).coerceIn(0.0, 100.0)
        }

        state.hydration = hydration
        state.thirstPhase = classifyThirst(state.hydration)
    }

    fun onWaterBottleConsume(state: PlayerEnvState) {
        restoreHydration(state, ThirstSettings.waterBottleRestore)
    }

    fun isNaturalWaterSource(block: Block): Boolean {
        return resolveNaturalWaterSourceType(block) != null
    }

    fun onRightClickNaturalWaterSource(player: Player, state: PlayerEnvState, block: Block): Boolean {
        val sourceType = resolveNaturalWaterSourceType(block) ?: return false
        val settings = ThirstSettings
        val restore = when (sourceType) {
            NaturalWaterSourceType.FRESH_WATER -> settings.waterSourceRestore
            NaturalWaterSourceType.SEA_WATER -> settings.seaWaterRestore
        }
        val nauseaChance = when (sourceType) {
            NaturalWaterSourceType.FRESH_WATER -> settings.riverNauseaChance
            NaturalWaterSourceType.SEA_WATER -> settings.seaWaterNauseaChance
        }

        val changed = restoreHydration(state, restore)
        if (!changed) {
            return false
        }
        maybeApplyNaturalWaterSideEffects(player, sourceType, nauseaChance)
        return true
    }

    fun isDrinker(block: Block): Boolean {
        return block.type == Material.WATER_CAULDRON
    }

    fun onRightClickDrinker(state: PlayerEnvState, block: Block): Boolean {
        if (!isDrinker(block)) {
            return false
        }
        return restoreHydration(state, ThirstSettings.drinkerRestore)
    }

    private fun resolveNaturalWaterSourceType(block: Block): NaturalWaterSourceType? {
        if (block.type != Material.WATER) {
            return null
        }

        val biomeName = block.biome.toString().lowercase()
        return if (biomeName.contains("ocean")) {
            NaturalWaterSourceType.SEA_WATER
        } else {
            NaturalWaterSourceType.FRESH_WATER
        }
    }

    /**
     * 喝自然水源后的一次性副作用（恶心/饥饿）。属于事件驱动效果，
     * 不进入 SurvivalEffectApplier 的持续 tick 效果链路。
     */
    private fun maybeApplyNaturalWaterSideEffects(
        player: Player,
        sourceType: NaturalWaterSourceType,
        nauseaChance: Double,
    ) {
        if (ThreadLocalRandom.current().nextDouble() >= nauseaChance) {
            return
        }

        PotionEffectType.NAUSEA?.let { effectType ->
            player.addPotionEffect(PotionEffect(effectType, 20 * 30, 0, false, false, false))
        }
        if (sourceType == NaturalWaterSourceType.SEA_WATER) {
            PotionEffectType.HUNGER?.let { effectType ->
                player.addPotionEffect(PotionEffect(effectType, 20 * 30, 0, false, false, false))
            }
        }
    }

    private fun restoreHydration(state: PlayerEnvState, amount: Double): Boolean {
        val previousHydration = state.hydration
        state.hydration = (state.hydration + amount).coerceIn(0.0, 100.0)
        state.thirstPhase = classifyThirst(state.hydration)
        return state.hydration > previousHydration
    }

    private fun computeTemperatureDeviation(temperature: Double): Double {
        return when {
            temperature < TemperatureSettings.comfortMin -> TemperatureSettings.comfortMin - temperature
            temperature > TemperatureSettings.comfortMax -> temperature - TemperatureSettings.comfortMax
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

    private fun supportsRainHydration(global: GlobalEnvState): Boolean {
        return global.weather == WeatherType.RAIN || global.weather == WeatherType.THUNDER
    }

    fun classifyThirst(hydration: Double): ThirstPhase {
        return when {
            hydration >= ThirstSettings.thirstFull -> ThirstPhase.FULL
            hydration >= ThirstSettings.thirstThirsty -> ThirstPhase.THIRSTY
            hydration >= ThirstSettings.thirstSevere -> ThirstPhase.SEVERE_THIRST
            else -> ThirstPhase.DEHYDRATED
        }
    }
}
