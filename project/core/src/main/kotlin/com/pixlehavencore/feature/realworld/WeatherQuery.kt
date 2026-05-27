package com.pixlehavencore.feature.realworld

import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.World

/**
 * 天气查询接口
 * 提供基于位置的天气查询
 */
object WeatherQuery {
    
    /**
     * 获取区块的天气状态
     */
    fun getWeatherAt(chunk: Chunk, global: GlobalEnvState): WeatherState {
        return getWeatherState(chunk.x, chunk.z, chunk.world, global)
    }
    
    /**
     * 获取位置的天气状态
     */
    fun getWeatherAt(location: Location, global: GlobalEnvState): WeatherState {
        val chunkX = location.blockX shr 4
        val chunkZ = location.blockZ shr 4
        return getWeatherState(chunkX, chunkZ, location.world, global)
    }
    
    /**
     * 获取指定坐标的天气状态
     */
    fun getWeatherState(chunkX: Int, chunkZ: Int, world: World, global: GlobalEnvState): WeatherState {
        val timeFactor = ChunkWeatherEngine.calculateTimeFactor(world.gameTime)
        return WeatherCache.getOrCompute(chunkX, chunkZ, timeFactor, global.season)
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