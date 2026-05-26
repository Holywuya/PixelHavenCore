package com.pixlehavencore.feature.realworld

import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class RealWorldWeatherWarningStartedEvent(
    val currentWeather: WeatherType,
    val currentWeatherIntensity: Double,
    val targetWeather: WeatherType,
    val targetWeatherIntensity: Double,
    val warningDurationSeconds: Double,
    val remainingWarningSeconds: Double,
) : Event(true) {

    override fun getHandlers(): HandlerList {
        return getHandlerList()
    }

    companion object {
        private val handlers = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList {
            return handlers
        }
    }
}

class RealWorldWeatherChangedEvent(
    val previousWeather: WeatherType,
    val previousWeatherIntensity: Double,
    val weather: WeatherType,
    val intensity: Double,
) : Event(true) {

    override fun getHandlers(): HandlerList {
        return getHandlerList()
    }

    companion object {
        private val handlers = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList {
            return handlers
        }
    }
}

class RealWorldSeasonChangedEvent(
    val previousSeason: Season,
    val season: Season,
    val seasonProgress: Double,
) : Event(true) {

    override fun getHandlers(): HandlerList {
        return getHandlerList()
    }

    companion object {
        private val handlers = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList {
            return handlers
        }
    }
}
