package com.pixlehavencore.feature.realworld.weather

import com.pixlehavencore.feature.realworld.Season
import com.pixlehavencore.util.FastNoiseLite

/**
 * 区块级天气计算引擎 - RealLife 噪声层
 * 使用 3D 噪声函数生成基于区块坐标的降雨值
 */
object ChunkWeatherEngine {

    private var rainNoise: FastNoiseLite? = null

    val isInitialized: Boolean
        get() = rainNoise != null

    fun init(worldSeed: Long, frequency: Float = 0.015f) {
        rainNoise = FastNoiseLite(worldSeed.toInt()).apply {
            setFrequency(frequency)
        }
    }

    /**
     * 计算区块降雨值
     * @return 降雨值 [0.0, 1.0]，越小越可能下雨
     */
    fun computeRainValue(chunkX: Int, chunkZ: Int, timeFactor: Float): Double {
        val noise = rainNoise ?: return 1.0
        val rawNoise = noise.getNoise(chunkX.toFloat(), chunkZ.toFloat(), timeFactor)
        // 归一化到 [0.0, 1.0]
        return (rawNoise + 1.0) / 2.0
    }

    /**
     * 获取季节降雨概率修正
     * RealLife 1.38.0 季节修正：春 2.5, 夏 1.0, 秋 0.8, 冬 4.0
     */
    fun getSeasonRainMultiplier(season: Season): Double {
        return when (season) {
            Season.SPRING -> 2.5
            Season.SUMMER -> 1.0
            Season.AUTUMN -> 0.8
            Season.WINTER -> 4.0
        }
    }

    /**
     * 计算时间因子
     * @param gameTime 世界游戏时间（ticks，单调递增）
     * @param changeSpeed 变化速度系数
     */
    fun calculateTimeFactor(gameTime: Long, changeSpeed: Float = WeatherSettings.localChangeSpeed): Float {
        return (gameTime * changeSpeed).toFloat()
    }
}
