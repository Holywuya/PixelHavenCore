package com.pixlehavencore.feature.realworld.weather

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object WeatherSettings {

    @Config("feature/realworld/weather.yml")
    private lateinit var config: Configuration

    var decisionIntervalSeconds: Int = 300
        private set
    var persistenceChance: Double = 0.6
        private set
    var extremeWarningSeconds: Int = 30
        private set
    var extremeGracePeriodSeconds: Int = 10
        private set
    var extremeDamageIntervalSeconds: Int = 3
        private set
    var extremeBaseDamageHearts: Double = 2.0
        private set
    var visibilityEffectDurationSeconds: Int = 3
        private set
    var fogBlindnessAmplifier: Int = 0
        private set
    var blizzardBlindnessAmplifier: Int = 0
        private set
    var sandstormBlindnessAmplifier: Int = 1
        private set
    var localEnabled: Boolean = false
        private set
    var localNoiseFrequency: Float = 0.015f
        private set
    var localChangeSpeed: Float = 0.001f
        private set
    var localCacheEnabled: Boolean = true
        private set
    var localCacheMaxSize: Int = 1000
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        decisionIntervalSeconds = config.getInt("decision-interval-seconds", 300).coerceAtLeast(1)
        persistenceChance = config.getDouble("persistence-chance", 0.6).coerceIn(0.0, 1.0)
        extremeWarningSeconds = config.getInt("extreme.warning-seconds", 30).coerceAtLeast(0)
        extremeGracePeriodSeconds = config.getInt("extreme.grace-period-seconds", 10).coerceAtLeast(0)
        extremeDamageIntervalSeconds = config.getInt("extreme.damage-interval-seconds", 3).coerceAtLeast(1)
        extremeBaseDamageHearts = config.getDouble("extreme.base-damage-hearts", 2.0).coerceAtLeast(0.0)
        visibilityEffectDurationSeconds = config.getInt("visibility.effect-duration-seconds", 3).coerceAtLeast(1)
        fogBlindnessAmplifier = config.getInt("visibility.fog.blindness-amplifier", 0).coerceAtLeast(0)
        blizzardBlindnessAmplifier = config.getInt("visibility.blizzard.blindness-amplifier", 1).coerceAtLeast(0)
        sandstormBlindnessAmplifier = config.getInt("visibility.sandstorm.blindness-amplifier", 2).coerceAtLeast(0)
        localEnabled = config.getBoolean("local.enabled", false)
        localNoiseFrequency = config.getDouble("local.noise-frequency", 0.015).toFloat().coerceIn(0.001f, 0.1f)
        localChangeSpeed = config.getDouble("local.change-speed", 0.001).toFloat().coerceIn(0.0001f, 0.01f)
        localCacheEnabled = config.getBoolean("local.cache-enabled", true)
        localCacheMaxSize = config.getInt("local.cache-max-size", 1000).coerceIn(100, 10000)
    }
}