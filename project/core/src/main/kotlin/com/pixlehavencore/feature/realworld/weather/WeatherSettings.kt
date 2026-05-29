package com.pixlehavencore.feature.realworld.weather

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object WeatherSettings {

    @Config("feature/realworld/weather.yml")
    private lateinit var config: Configuration

    // 严重需求（极端体温/脱水）伤害参数，仍由 SurvivalEffectApplier 使用
    var extremeGracePeriodSeconds: Int = 10
        private set
    var extremeDamageIntervalSeconds: Int = 3
        private set
    var extremeBaseDamageHearts: Double = 2.0
        private set

    // 噪声驱动降雨
    var localEnabled: Boolean = true
        private set
    var localNoiseFrequency: Float = 0.015f
        private set
    var localChangeSpeed: Float = 0.001f
        private set
    var localCacheMaxSize: Int = 1000
        private set

    /** 基础降雨概率，会乘以季节修正后作为噪声阈值 */
    var rainProbability: Double = 0.2
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        extremeGracePeriodSeconds = config.getInt("extreme.grace-period-seconds", 10).coerceAtLeast(0)
        extremeDamageIntervalSeconds = config.getInt("extreme.damage-interval-seconds", 3).coerceAtLeast(1)
        extremeBaseDamageHearts = config.getDouble("extreme.base-damage-hearts", 2.0).coerceAtLeast(0.0)
        localEnabled = config.getBoolean("local.enabled", true)
        localNoiseFrequency = config.getDouble("local.noise-frequency", 0.015).toFloat().coerceIn(0.001f, 0.1f)
        localChangeSpeed = config.getDouble("local.change-speed", 0.001).toFloat().coerceIn(0.0001f, 0.01f)
        localCacheMaxSize = config.getInt("local.cache-max-size", 1000).coerceIn(100, 10000)
        rainProbability = config.getDouble("rain.probability", 0.2).coerceIn(0.0, 1.0)
    }
}
