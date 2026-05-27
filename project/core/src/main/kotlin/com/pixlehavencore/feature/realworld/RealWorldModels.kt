package com.pixlehavencore.feature.realworld

enum class Season(
    val displayName: String,
    val temperatureModifier: Double,
    val hydrationMultiplier: Double,
    val weatherWeights: Map<WeatherType, Double>,
) {
    SPRING(
        "春",
        5.0,
        1.0,
        mapOf(
            WeatherType.CLEAR to 3.0,
            WeatherType.RAIN to 4.0,
            WeatherType.THUNDER to 1.0,
        ),
    ),
    SUMMER(
        "夏",
        15.0,
        1.5,
        mapOf(
            WeatherType.CLEAR to 5.0,
            WeatherType.RAIN to 2.0,
            WeatherType.THUNDER to 2.0,
            WeatherType.SANDSTORM to 1.0,
        ),
    ),
    AUTUMN(
        "秋",
        0.0,
        0.9,
        mapOf(
            WeatherType.CLEAR to 3.0,
            WeatherType.RAIN to 4.0,
            WeatherType.FOG to 2.0,
        ),
    ),
    WINTER(
        "冬",
        -15.0,
        0.6,
        mapOf(
            WeatherType.CLEAR to 3.0,
            WeatherType.SNOW to 4.0,
            WeatherType.BLIZZARD to 1.0,
        ),
    );

    companion object {
        fun fromName(name: String): Season? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

enum class WeatherType(
    val displayName: String,
    val temperatureModifier: Double,
    val hydrationMultiplier: Double,
    val affectsVisibility: Boolean,
    val hasDamageEffect: Boolean,
) {
    CLEAR("晴", 0.0, 1.2, false, false),
    RAIN("雨", -3.0, 0.5, false, false),
    THUNDER("雷暴", -5.0, 0.6, false, false),
    SNOW("雪", -8.0, 0.4, true, false),
    BLIZZARD("暴风雪", -15.0, 0.3, true, true),
    SANDSTORM("沙尘暴", 5.0, 1.8, true, true),
    FOG("雾", -2.0, 0.7, true, false),
    ACID_RAIN("酸雨", -4.0, 0.3, false, true);

    val isExtreme: Boolean
        get() = this == BLIZZARD || this == SANDSTORM || this == ACID_RAIN

    companion object {
        fun fromName(name: String): WeatherType? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

enum class HeatSource(
    val range: Int,
    val modifier: Double,
) {
    LAVA(3, 15.0),
    CAMPFIRE(3, 10.0),
    SOUL_CAMPFIRE(3, 8.0),
    FURNACE(2, 5.0),
    FIRE(2, 8.0),
    ICE(2, -8.0),
    PACKED_ICE(2, -10.0),
    BLUE_ICE(2, -12.0),
    MAGMA_BLOCK(2, 8.0),
}

enum class DayPhase {
    DAY,
    DUSK,
    NIGHT,
}

enum class TemperaturePhase {
    SEVERE_HEAT,
    HEAT,
    COMFORTABLE,
    COLD_MILD,
    COLD,
    SEVERE_COLD,
}

enum class ThirstPhase {
    FULL,
    THIRSTY,
    SEVERE_THIRST,
    DEHYDRATED,
}

data class PlayerEnvState(
    var temperature: Double = 20.0,
    var hydration: Double = 100.0,
    var wetness: Double = 0.0,
    var fracture: Double = 0.0,
    var isSheltered: Boolean = false,
    var isWeatherSheltered: Boolean = false,
    var nearHeatSource: HeatSource? = null,
    var temperatureBlockModifier: Double = 0.0,
    var biomeTemperature: Double = 20.0,
    var temperaturePhase: TemperaturePhase = TemperaturePhase.COMFORTABLE,
    var thirstPhase: ThirstPhase = ThirstPhase.FULL,
    var graceTimer: Double = 0.0,
    var damageTimer: Double = 0.0,
    var weatherExposureSource: WeatherType? = null,
    var weatherExposureGraceTimer: Double = 0.0,
    var weatherExposureDamageTimer: Double = 0.0,
    var heatSourceScanTimer: Double = 0.0,
    var hudRefreshTimer: Double = 0.0,
)

data class GlobalEnvState(
    var season: Season = Season.SPRING,
    var seasonProgress: Double = 0.0,
    var weather: WeatherType = WeatherType.CLEAR,
    var weatherIntensity: Double = 0.5,
    var pendingWeather: WeatherType? = null,
    var pendingWeatherIntensity: Double = 0.0,
    var warningRemainingSeconds: Double = 0.0,
    var dayPhase: DayPhase = DayPhase.DAY,
    var weatherDecisionTimer: Double = 0.0,
    var lastDominantWeather: WeatherType? = null,
)

/**
 * 区块级天气状态
 */
data class WeatherState(
    val type: WeatherType,
    val intensity: Double,  // 0.0 ~ 1.0
)
