package com.pixlehavencore.feature.realworld.weather

import com.pixlehavencore.feature.realworld.GlobalEnvState
import com.pixlehavencore.feature.realworld.RealWorldWeatherChangedEvent
import com.pixlehavencore.feature.realworld.RealWorldWeatherWarningStartedEvent
import com.pixlehavencore.feature.realworld.WeatherType
import com.pixlehavencore.feature.realworld.tick.GlobalTickContext
import java.util.concurrent.ThreadLocalRandom
import org.bukkit.Bukkit
import taboolib.common.platform.function.info

object WeatherEngine {

    /**
     * 初始化天气引擎
     */
    fun init(worldSeed: Int) {
        if (WeatherSettings.localEnabled) {
            ChunkWeatherEngine.init(worldSeed, WeatherSettings.localNoiseFrequency)
            WeatherCache.setMaxSize(WeatherSettings.localCacheMaxSize)
            info("[RealWorld] 局部天气引擎已初始化 (频率: ${WeatherSettings.localNoiseFrequency})")
        }
    }

    /**
     * 天气系统 tick
     * 始终执行全局天气决策（驱动原版雨雪视觉效果），
     * 局部天气启用时区块噪声天气独立工作（温度修正等局部效果由 WeatherQuery 按需查询）。
     */
    fun tick(global: GlobalEnvState, tickIntervalSeconds: Int, @Suppress("UNUSED_PARAMETER") context: GlobalTickContext) {
        tickGlobalWeather(global, tickIntervalSeconds)
    }

    /**
     * 全局天气决策：递减计时器，到期时调用 decideWeather 切换天气
     */
    private fun tickGlobalWeather(global: GlobalEnvState, tickIntervalSeconds: Int) {
        val safeTickSeconds = tickIntervalSeconds.coerceAtLeast(0).toDouble()
        if (tickWarning(global, safeTickSeconds)) {
            return
        }

        global.weatherDecisionTimer -= safeTickSeconds
        if (global.weatherDecisionTimer > 0.0) {
            return
        }

        decideWeather(global)
        val interval = WeatherSettings.decisionIntervalSeconds.toDouble()
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
        global.weatherDecisionTimer = WeatherSettings.decisionIntervalSeconds.toDouble()
        return true
    }

    private fun decideWeather(global: GlobalEnvState) {
        val random = ThreadLocalRandom.current()
        val currentWeather = global.weather

        // 延续逻辑：当前天气有概率持续，仅改变强度（支持同天气不同强度）
        if (currentWeather != WeatherType.CLEAR && random.nextDouble() < WeatherSettings.persistenceChance) {
            global.weatherIntensity = random.nextDouble(0.5, 1.0).coerceIn(0.0, 1.0)
            return
        }

        // 切换逻辑：必须切换到不同的天气类型
        val weatherWeights = global.season.weatherWeights
            .filterValues { it > 0.0 }
            .filterKeys { it != currentWeather } // 排除当前天气，确保切换到不同类型

        val totalWeight = weatherWeights.values.sum()
        if (totalWeight <= 0.0) {
            setWeather(global, WeatherType.CLEAR, 0.5)
            return
        }

        var roll = random.nextDouble(totalWeight)
        for ((weatherType, weight) in weatherWeights) {
            roll -= weight
            if (roll > 0.0) continue

            val intensity = random.nextDouble(0.5, 1.0).coerceIn(0.0, 1.0)
            scheduleOrSetWeather(global, weatherType, intensity)
            return
        }

        setWeather(global, WeatherType.CLEAR, 0.5)
    }

    private fun scheduleOrSetWeather(global: GlobalEnvState, weather: WeatherType, intensity: Double) {
        if (weather == global.weather) {
            global.weatherIntensity = intensity.coerceIn(0.0, 1.0)
            return
        }

        if (!weather.isExtreme) {
            setWeather(global, weather, intensity)
            return
        }

        val warningSeconds = WeatherSettings.extremeWarningSeconds.toDouble()
        if (warningSeconds <= 0.0) {
            setWeather(global, weather, intensity)
            return
        }

        if (global.pendingWeather == weather && global.warningRemainingSeconds > 0.0) {
            global.pendingWeatherIntensity = intensity
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
    }

    private fun clearWarning(global: GlobalEnvState) {
        global.pendingWeather = null
        global.pendingWeatherIntensity = 0.0
        global.warningRemainingSeconds = 0.0
    }
}
