package com.pixlehavencore.feature.realworld

import kotlin.math.cos
import org.bukkit.Bukkit
import taboolib.common.platform.function.info

object SeasonEngine {

    fun tick(global: GlobalEnvState, tickIntervalSeconds: Int) {
        val seasonDurationTicks = RealWorldSettings.seasonDurationTicks
        if (seasonDurationTicks <= 0L) {
            return
        }

        val elapsedTicks = tickIntervalSeconds.coerceAtLeast(0) * 20.0
        val progressPerTick = elapsedTicks / seasonDurationTicks.toDouble()
        global.seasonProgress += progressPerTick

        while (global.seasonProgress >= 1.0) {
            global.seasonProgress -= 1.0
            val previousSeason = global.season
            global.season = getNextSeason(previousSeason)
            val normalizedSeasonProgress = global.seasonProgress.coerceIn(0.0, 1.0)
            Bukkit.getPluginManager().callEvent(
                RealWorldSeasonChangedEvent(
                    previousSeason = previousSeason,
                    season = global.season,
                    seasonProgress = normalizedSeasonProgress,
                ),
            )
            info("[RealWorld] 季节切换为: ${global.season.displayName}")
        }

        if (global.seasonProgress < 0.0) {
            global.seasonProgress = 0.0
        }
    }

    fun getTemperatureModifier(global: GlobalEnvState): Double {
        return interpolateSeasonValue(global) { it.temperatureModifier }
    }

    fun getHydrationMultiplier(global: GlobalEnvState): Double {
        return interpolateSeasonValue(global) { it.hydrationMultiplier }
    }

    fun getTimeTemperatureModifier(dayPhase: DayPhase): Double {
        return when (dayPhase) {
            DayPhase.DAY -> 5.0
            DayPhase.DUSK -> 0.0
            DayPhase.NIGHT -> -5.0
        }
    }

    fun getTimeTemperatureModifier(worldTime: Long): Double {
        val normalizedTime = ((worldTime % 24000L) + 24000L) % 24000L
        val radians = 2.0 * Math.PI * (normalizedTime - 6000.0) / 24000.0
        return 5.0 * cos(radians)
    }

    fun computeDayPhase(worldTime: Long): DayPhase {
        val normalizedTime = ((worldTime % 24000L) + 24000L) % 24000L
        return when (normalizedTime) {
            in 1000L..11000L -> DayPhase.DAY
            in 11001L..13000L -> DayPhase.DUSK
            else -> DayPhase.NIGHT
        }
    }

    private fun interpolateSeasonValue(global: GlobalEnvState, selector: (Season) -> Double): Double {
        val currentSeason = global.season
        val transitionProgress = RealWorldSettings.seasonTransitionProgress.coerceIn(0.0, 1.0)
        if (transitionProgress <= 0.0) {
            return selector(currentSeason)
        }

        val seasonProgress = global.seasonProgress.coerceIn(0.0, 1.0)
        val transitionStart = 1.0 - transitionProgress
        if (seasonProgress <= transitionStart) {
            return selector(currentSeason)
        }

        val nextSeason = getNextSeason(currentSeason)
        val blend = ((seasonProgress - transitionStart) / transitionProgress).coerceIn(0.0, 1.0)
        val currentValue = selector(currentSeason)
        val nextValue = selector(nextSeason)
        return currentValue * (1.0 - blend) + nextValue * blend
    }

    private fun getNextSeason(season: Season): Season {
        val nextIndex = (season.ordinal + 1) % Season.entries.size
        return Season.entries[nextIndex]
    }
}
