package com.pixlehavencore.feature.realworld

import java.util.concurrent.ConcurrentHashMap

/**
 * 天气缓存
 * 避免频繁计算噪声值，提升性能
 */
object WeatherCache {
    
    private val cache = ConcurrentHashMap<Long, CachedWeather>()
    private var maxCacheSize = 1000
    
    /**
     * 获取或计算区块天气
     */
    fun getOrCompute(chunkX: Int, chunkZ: Int, timeFactor: Float, season: Season): WeatherState {
        val key = chunkKey(chunkX, chunkZ)
        val cached = cache[key]
        
        // 检查缓存是否有效（相同时间因子）
        if (cached != null && cached.timeFactor == timeFactor) {
            return cached.weather
        }
        
        // 计算新天气
        val weather = ChunkWeatherEngine.computeWeather(chunkX, chunkZ, timeFactor, season)
        
        // 存入缓存（如果超过大小限制，清空缓存）
        if (cache.size >= maxCacheSize) {
            cache.clear()
        }
        cache[key] = CachedWeather(timeFactor, weather)
        
        return weather
    }
    
    /**
     * 清空缓存
     */
    fun clear() {
        cache.clear()
    }
    
    /**
     * 设置最大缓存大小
     */
    fun setMaxSize(size: Int) {
        maxCacheSize = size.coerceAtLeast(100)
    }
    
    private fun chunkKey(x: Int, z: Int): Long {
        return (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)
    }
    
    private data class CachedWeather(
        val timeFactor: Float,
        val weather: WeatherState,
    )
}