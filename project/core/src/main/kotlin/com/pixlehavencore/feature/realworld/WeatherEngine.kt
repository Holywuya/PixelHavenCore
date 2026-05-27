package com.pixlehavencore.feature.realworld

import java.util.concurrent.ThreadLocalRandom
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.function.info

object WeatherEngine {

    /**
     * 初始化天气引擎
     */
    fun init(worldSeed: Int) {
        if (RealWorldSettings.localWeatherEnabled) {
            ChunkWeatherEngine.init(worldSeed, RealWorldSettings.localWeatherNoiseFrequency)
            WeatherCache.setMaxSize(RealWorldSettings.localWeatherCacheMaxSize)
            info("[RealWorld] 局部天气引擎已初始化 (频率: ${RealWorldSettings.localWeatherNoiseFrequency})")
        }
    }

    /**
     * 天气系统 tick
     * 现在只负责更新全局主导天气和极端天气预警
     */
    fun tick(global: GlobalEnvState, tickIntervalSeconds: Int, players: List<Player>) {
        if (!RealWorldSettings.localWeatherEnabled) {
            // 保留旧的全局天气逻辑作为后备
            tickGlobalWeather(global, tickIntervalSeconds)
            return
        }

        // 更新全局主导天气
        updateDominantWeather(global, players)

        // 处理极端天气预警（保留原逻辑）
        tickWarning(global, tickIntervalSeconds.toDouble())
    }

    /**
     * 更新全局主导天气
     * 统计所有玩家周围区块的天气，取最常见的类型
     */
    private fun updateDominantWeather(global: GlobalEnvState, players: List<Player>) {
        if (players.isEmpty()) return

        val weatherCounts = mutableMapOf<WeatherType, Int>()

        // 统计每个玩家所在区块的天气
        for (player in players) {
            val weather = WeatherQuery.getWeatherAt(player.location, global)
            weatherCounts[weather.type] = (weatherCounts[weather.type] ?: 0) + 1
        }

        // 找出最常见的天气
        val dominantWeather = weatherCounts.maxByOrNull { it.value }?.key ?: WeatherType.CLEAR

        // 如果主导天气变化，触发事件
        if (dominantWeather != global.weather) {
            val previousWeather = global.weather
            global.weather = dominantWeather
            global.lastDominantWeather = previousWeather

            Bukkit.getPluginManager().callEvent(
                RealWorldWeatherChangedEvent(
                    previousWeather = previousWeather,
                    previousWeatherIntensity = global.weatherIntensity,
                    weather = dominantWeather,
                    intensity = 0.5,  // 主导天气强度取平均值
                ),
            )
            info("[RealWorld] 主导天气切换为: ${dominantWeather.displayName}")
        }
    }

    /**
     * 旧的全局天气逻辑（后备）
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

        val warningSeconds = RealWorldSettings.extremeWarningSeconds.toDouble()
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
