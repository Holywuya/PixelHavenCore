package com.pixlehavencore.feature.realworld.weather

import com.pixlehavencore.feature.realworld.GlobalEnvState
import com.pixlehavencore.feature.realworld.WeatherState
import com.pixlehavencore.feature.realworld.WeatherType
import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.World

/**
 * 降雨状态（底层噪声）
 */
data class RainState(
    val rainValue: Double,
    val rainProbability: Double,
    val isRaining: Boolean,
    val intensity: Double,
)

/**
 * 天气查询接口
 * 双层架构：底层噪声降雨判断 + 上层天气类型映射
 */
object WeatherQuery {

    /**
     * 获取区块的降雨状态（底层噪声）
     */
    fun getRainState(chunkX: Int, chunkZ: Int, world: World, global: GlobalEnvState): RainState {
        val timeFactor = ChunkWeatherEngine.calculateTimeFactor(world.gameTime)
        val rainValue = ChunkWeatherEngine.computeRainValue(chunkX, chunkZ, timeFactor)
        val seasonMultiplier = ChunkWeatherEngine.getSeasonRainMultiplier(global.season)
        val rainProbability = (WeatherSettings.rainProbability * seasonMultiplier).coerceAtMost(1.0)

        val isRaining = rainValue < rainProbability
        val intensity = if (isRaining) {
            (1.0 - rainValue / rainProbability).coerceIn(0.0, 1.0)
        } else {
            0.0
        }

        return RainState(rainValue, rainProbability, isRaining, intensity)
    }

    /**
     * 获取区块的天气状态（上层映射）
     */
    fun getWeatherAt(chunk: Chunk, global: GlobalEnvState): WeatherState {
        return getWeatherState(chunk.x, chunk.z, chunk.world, global)
    }

    /**
     * 获取位置的天气状态（上层映射）
     */
    fun getWeatherAt(location: Location, global: GlobalEnvState): WeatherState {
        val chunkX = location.blockX shr 4
        val chunkZ = location.blockZ shr 4
        return getWeatherState(chunkX, chunkZ, location.world, global)
    }

    /**
     * 获取指定坐标的天气状态（上层映射）
     */
    fun getWeatherState(chunkX: Int, chunkZ: Int, world: World, global: GlobalEnvState): WeatherState {
        // 强制天气优先，绕过缓存
        global.forcedWeather?.let { forced ->
            return WeatherState(forced, global.forcedWeatherIntensity)
        }

        // 同一区块在 TTL 窗口内复用计算结果，避免每 tick 多个消费者重复采样噪声
        return WeatherCache.getOrCompute(chunkX, chunkZ) {
            val rainState = getRainState(chunkX, chunkZ, world, global)
            val weatherType = mapRainToWeatherType(rainState)
            WeatherState(weatherType, rainState.intensity)
        }
    }

    /**
     * 降雨状态 → 天气类型映射，仅保留晴天和雨天。
     */
    private fun mapRainToWeatherType(rain: RainState): WeatherType {
        return if (rain.isRaining) WeatherType.RAIN else WeatherType.CLEAR
    }

    /**
     * 检查位置是否有极端天气
     */
    fun isExtremeWeatherAt(location: Location, global: GlobalEnvState): Boolean {
        val weather = getWeatherAt(location, global)
        return weather.type.isExtreme
    }

    /**
     * 获取位置的天气温度修正
     */
    fun getTemperatureModifierAt(location: Location, global: GlobalEnvState): Double {
        val weather = getWeatherAt(location, global)
        return weather.type.temperatureModifier * weather.intensity
    }

    /**
     * 获取位置的天气能见度类型
     */
    fun getVisibilityWeatherAt(location: Location, global: GlobalEnvState): WeatherType? {
        val weather = getWeatherAt(location, global)
        return weather.type.takeIf { it.affectsVisibility }
    }
}
