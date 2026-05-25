package com.pixlehavencore.feature.realworld

import java.util.concurrent.ThreadLocalRandom
import org.bukkit.Bukkit
import taboolib.common.platform.function.info

object WeatherEngine {

    fun tick(global: GlobalEnvState, tickIntervalSeconds: Int) {
        val safeTickSeconds = tickIntervalSeconds.coerceAtLeast(0).toDouble()
        if (tickWarning(global, safeTickSeconds)) {
            return
        }

        global.weatherDecisionTimer -= safeTickSeconds
        if (global.weatherDecisionTimer > 0.0) {
            return
        }

        decideWeather(global)
        val interval = RealWorldSettings.weatherDecisionIntervalSeconds.toDouble()
        while (global.weatherDecisionTimer <= 0.0) {
            global.weatherDecisionTimer += interval
        }
    }

    private fun tickWarning(global: GlobalEnvState, tickIntervalSeconds: Double): Boolean {
        if (global.pendingWeather == null || global.warningRemainingSeconds <= 0.0) {
            return false
        }

        global.warningRemainingSeconds = (global.warningRemainingSeconds - tickIntervalSeconds).coerceAtLeast(0.0)
        if (global.warningRemainingSeconds > 0.0) {
            return true
        }

        val targetWeather = global.pendingWeather ?: return false
        val targetIntensity = global.pendingWeatherIntensity
        clearWarning(global)
        setWeather(global, targetWeather, targetIntensity)
        global.weatherDecisionTimer = RealWorldSettings.weatherDecisionIntervalSeconds.toDouble()
        return true
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

            val intensity = random.nextDouble(0.5, 1.0).coerceIn(0.0, 1.0)
            scheduleOrSetWeather(global, weatherType, intensity)
            return
        }

        setWeather(global, WeatherType.CLEAR, 0.5)
    }

    private fun scheduleOrSetWeather(global: GlobalEnvState, weather: WeatherType, intensity: Double) {
        if (weather == global.weather) {
            global.weatherIntensity = intensity.coerceIn(0.0, 1.0)
            info("[RealWorld] 天气延续为: ${weather.displayName} (强度: ${"%.1f".format(global.weatherIntensity)})")
            return
        }

        if (!weather.isExtreme) {
            setWeather(global, weather, intensity)
            return
        }

        val warningSeconds = RealWorldSettings.extremeWarningSeconds.toDouble()
        if (warningSeconds <= 0.0) {
            setWeather(global, weather, intensity)
            return
        }

        if (global.pendingWeather == weather && global.warningRemainingSeconds > 0.0) {
            global.pendingWeatherIntensity = intensity
            info("[RealWorld] 极端天气预警延续为: ${weather.displayName} (剩余 ${global.warningRemainingSeconds.toInt()} 秒)")
            return
        }

        global.pendingWeather = weather
        global.pendingWeatherIntensity = intensity
        global.warningRemainingSeconds = warningSeconds
        Bukkit.getPluginManager().callEvent(
            RealWorldWeatherWarningStartedEvent(
                currentWeather = global.weather,
                currentWeatherIntensity = global.weatherIntensity,
                targetWeather = weather,
                targetWeatherIntensity = intensity,
                warningDurationSeconds = warningSeconds,
                remainingWarningSeconds = global.warningRemainingSeconds,
            ),
        )
        info("[RealWorld] 极端天气预警开始: ${weather.displayName} 将在 ${warningSeconds.toInt()} 秒后到来 (强度: ${"%.1f".format(intensity)})")
    }

    fun currentVisibilityWeather(global: GlobalEnvState): WeatherType? {
        return global.weather.takeIf { it.affectsVisibility }
    }

    fun setWeather(global: GlobalEnvState, weather: WeatherType, intensity: Double = 0.7) {
        val previousWeather = global.weather
        val previousWeatherIntensity = global.weatherIntensity
        val normalizedIntensity = intensity.coerceIn(0.0, 1.0)
        if (previousWeather == weather && previousWeatherIntensity == normalizedIntensity) {
            return
        }
        clearWarning(global)
        global.weather = weather
        global.weatherIntensity = normalizedIntensity
        Bukkit.getPluginManager().callEvent(
            RealWorldWeatherChangedEvent(
                previousWeather = previousWeather,
                previousWeatherIntensity = previousWeatherIntensity,
                weather = global.weather,
                intensity = global.weatherIntensity,
            ),
        )
        info("[RealWorld] 天气切换为: ${weather.displayName} (强度: ${"%.1f".format(global.weatherIntensity)})")
    }

    private fun clearWarning(global: GlobalEnvState) {
        global.pendingWeather = null
        global.pendingWeatherIntensity = 0.0
        global.warningRemainingSeconds = 0.0
    }
}
