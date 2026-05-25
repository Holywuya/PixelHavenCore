package com.pixlehavencore.feature.realworld

import java.util.concurrent.ThreadLocalRandom
import taboolib.common.platform.function.info

object WeatherEngine {

    fun tick(global: GlobalEnvState, tickIntervalSeconds: Int) {
        global.weatherDecisionTimer -= tickIntervalSeconds.coerceAtLeast(0)
        if (global.weatherDecisionTimer > 0.0) {
            return
        }

        decideWeather(global)
        val interval = RealWorldSettings.weatherDecisionIntervalSeconds.toDouble()
        while (global.weatherDecisionTimer <= 0.0) {
            global.weatherDecisionTimer += interval
        }
    }

    private fun decideWeather(global: GlobalEnvState) {
        val random = ThreadLocalRandom.current()
        val currentWeather = global.weather
        if (currentWeather != WeatherType.CLEAR && random.nextDouble() < RealWorldSettings.weatherPersistenceChance) {
            global.weatherIntensity = random.nextDouble(0.5, 1.0).coerceIn(0.0, 1.0)
            info("[RealWorld] 天气延续为: ${currentWeather.displayName} (强度: ${"%.1f".format(global.weatherIntensity)})")
            return
        }

        val weatherWeights = global.season.weatherWeights.filterValues { it > 0.0 }
        val totalWeight = weatherWeights.values.sum()
        if (totalWeight <= 0.0) {
            setWeather(global, WeatherType.CLEAR, 0.5)
            return
        }

        var roll = random.nextDouble(totalWeight)
        for ((weatherType, weight) in weatherWeights) {
            roll -= weight
            if (roll > 0.0) {
                continue
            }

            val intensity = random.nextDouble(0.5, 1.0)
            global.weather = weatherType
            global.weatherIntensity = intensity.coerceIn(0.0, 1.0)
            info("[RealWorld] 天气切换为: ${weatherType.displayName} (强度: ${"%.1f".format(global.weatherIntensity)})")
            return
        }

        setWeather(global, WeatherType.CLEAR, 0.5)
    }

    fun setWeather(global: GlobalEnvState, weather: WeatherType, intensity: Double = 0.7) {
        global.weather = weather
        global.weatherIntensity = intensity.coerceIn(0.0, 1.0)
        info("[RealWorld] 天气切换为: ${weather.displayName} (强度: ${"%.1f".format(global.weatherIntensity)})")
    }
}
