package com.pixlehavencore.feature.realworld

enum class Season(
    val displayName: String,
    val temperatureModifier: Double,
    val hydrationMultiplier: Double,
) {
    SPRING("春", 3.0, 1.0),
    SUMMER("夏", 10.0, 1.5),
    AUTUMN("秋", 0.0, 0.9),
    WINTER("冬", -20.0, 0.6);

    companion object {
        fun fromName(name: String): Season? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

/**
 * 天气类型仅保留晴天和雨天。
 */
enum class WeatherType(
    val displayName: String,
    val temperatureModifier: Double,
    val hydrationMultiplier: Double,
    val affectsVisibility: Boolean,
    val hasDamageEffect: Boolean,
) {
    CLEAR("晴", 0.0, 1.2, false, false),
    RAIN("雨", -3.0, 0.5, false, false);

    val isExtreme: Boolean
        get() = false

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

enum class ShelterType {
    NONE,
    CANOPY,
    BUILDING,
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
    var shelterType: ShelterType = ShelterType.NONE,
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
    var shelterCacheTimer: Double = 0.0,
    var shelterCacheBlockX: Int = Int.MIN_VALUE,
    var shelterCacheBlockY: Int = Int.MIN_VALUE,
    var shelterCacheBlockZ: Int = Int.MIN_VALUE,
    var hudRefreshTimer: Double = 0.0,
    var lastWaterTemp: Double = 20.0,
    var coldExposurePressure: Double = 0.0,
    var heatExposurePressure: Double = 0.0,
    var heatSourceCacheBlockX: Int = Int.MIN_VALUE,
    var heatSourceCacheBlockY: Int = Int.MIN_VALUE,
    var heatSourceCacheBlockZ: Int = Int.MIN_VALUE,
    var heatSourceCacheWorldName: String = "",
    var heatSourceCacheBiomeTemperature: Double = 20.0,
    var isClientRaining: Boolean = false,
    var isActuallyRaining: Boolean = false,
    var deathProtectionTimer: Double = 0.0,
    var deathProtectionCooldown: Double = 0.0,
)

data class GlobalEnvState(
    var season: Season = Season.SPRING,
    var seasonProgress: Double = 0.0,
    var forcedWeather: WeatherType? = null,
    var forcedWeatherIntensity: Double = 0.0,
    var dayPhase: DayPhase = DayPhase.DAY,
)

/**
 * 区块级天气状态
 */
data class WeatherState(
    val type: WeatherType,
    val intensity: Double,  // 0.0 ~ 1.0
)
