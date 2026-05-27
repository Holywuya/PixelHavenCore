package com.pixlehavencore.feature.realworld.weather

import com.pixlehavencore.feature.realworld.Season
import com.pixlehavencore.feature.realworld.WeatherState
import com.pixlehavencore.feature.realworld.WeatherType
import com.pixlehavencore.util.FastNoiseLite

/**
 * 区块级天气计算引擎
 * 使用噪声函数生成连续的天气分布
 */
object ChunkWeatherEngine {

    private var typeNoise: FastNoiseLite? = null
    private var intensityNoise: FastNoiseLite? = null

    val isInitialized: Boolean
        get() = typeNoise != null

    fun init(worldSeed: Int, frequency: Float = 0.015f) {
        typeNoise = FastNoiseLite(worldSeed).apply {
            setFrequency(frequency)
        }
        intensityNoise = FastNoiseLite(worldSeed + 1).apply {
            setFrequency(frequency * 1.3f)
        }
    }

    fun computeWeather(chunkX: Int, chunkZ: Int, timeFactor: Float, season: Season): WeatherState {
        val typeGen = typeNoise ?: return WeatherState(WeatherType.CLEAR, 0.5)
        val intensityGen = intensityNoise ?: return WeatherState(WeatherType.CLEAR, 0.5)

        val typeValue = typeGen.getNoise(chunkX.toFloat(), chunkZ.toFloat(), timeFactor)
        val normalizedType = (typeValue + 1.0f) / 2.0f

        val weatherType = mapToWeatherType(normalizedType, season.weatherWeights)

        val intensityValue = intensityGen.getNoise(chunkX.toFloat(), chunkZ.toFloat(), timeFactor)
        val intensity = 0.5 + (intensityValue + 1.0) / 4.0

        return WeatherState(weatherType, intensity)
    }
    
    /**
     * 将噪声值映射到天气类型
     * 使用季节权重划分区间
     */
    private fun mapToWeatherType(noiseValue: Float, weights: Map<WeatherType, Double>): WeatherType {
        val totalWeight = weights.values.sum()
        if (totalWeight <= 0.0) return WeatherType.CLEAR
        
        var threshold = 0.0
        
        for ((type, weight) in weights) {
            threshold += weight / totalWeight
            if (noiseValue <= threshold) {
                return type
            }
        }
        
        return WeatherType.CLEAR
    }
    
    /**
     * 计算时间因子
     * @param worldTime 世界游戏时间（ticks）
     * @param changeSpeed 变化速度系数
     */
    fun calculateTimeFactor(worldTime: Long, changeSpeed: Float = 0.001f): Float {
        return (worldTime * changeSpeed).toFloat()
    }
}
