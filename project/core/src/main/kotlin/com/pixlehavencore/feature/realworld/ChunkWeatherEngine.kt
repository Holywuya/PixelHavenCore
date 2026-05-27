package com.pixlehavencore.feature.realworld

import com.pixlehavencore.util.FastNoiseLite

/**
 * 区块级天气计算引擎
 * 使用噪声函数生成连续的天气分布
 */
object ChunkWeatherEngine {
    
    private lateinit var typeNoise: FastNoiseLite
    private lateinit var intensityNoise: FastNoiseLite
    
    /**
     * 初始化噪声生成器
     * @param worldSeed 世界种子
     * @param frequency 噪声频率，值越小天气区域越大
     */
    fun init(worldSeed: Int, frequency: Float = 0.015f) {
        typeNoise = FastNoiseLite(worldSeed).apply {
            setFrequency(frequency)
        }
        intensityNoise = FastNoiseLite(worldSeed + 1).apply {
            setFrequency(frequency * 1.3f)  // 强度噪声频率略高，增加变化
        }
    }
    
    /**
     * 计算指定区块的天气状态
     * @param chunkX 区块 X 坐标
     * @param chunkZ 区块 Z 坐标
     * @param timeFactor 时间因子，用于天气随时间变化
     * @param season 当前季节
     * @return 天气状态
     */
    fun computeWeather(chunkX: Int, chunkZ: Int, timeFactor: Float, season: Season): WeatherState {
        // 1. 生成类型噪声值 (-1 ~ 1)
        val typeValue = typeNoise.getNoise(chunkX.toFloat(), chunkZ.toFloat(), timeFactor)
        // 归一化到 0 ~ 1
        val normalizedType = (typeValue + 1.0f) / 2.0f
        
        // 2. 按季节权重映射到天气类型
        val weatherType = mapToWeatherType(normalizedType, season.weatherWeights)
        
        // 3. 生成强度噪声值 (-1 ~ 1)
        val intensityValue = intensityNoise.getNoise(chunkX.toFloat(), chunkZ.toFloat(), timeFactor)
        // 映射到 0.5 ~ 1.0（保证最小强度为 0.5）
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
