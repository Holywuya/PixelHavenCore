package com.pixlehavencore.feature.realworld.weather

import com.pixlehavencore.feature.realworld.WeatherState
import java.util.concurrent.ConcurrentHashMap

/**
 * 天气缓存
 * 使用 TTL 策略避免每 tick 重新计算噪声值
 */
object WeatherCache {

    private val cache = ConcurrentHashMap<Long, CachedWeather>()
    private var maxCacheSize = 1000
    private var ttlMillis = 10_000L

    fun setMaxSize(size: Int) {
        maxCacheSize = size
    }

    fun setTtl(millis: Long) {
        ttlMillis = millis.coerceAtLeast(1000L)
    }

    /**
     * 获取或计算区块天气（lambda 版本）
     * 缓存有效期内（默认 10 秒）直接返回缓存结果
     */
    fun getOrCompute(chunkX: Int, chunkZ: Int, compute: () -> WeatherState): WeatherState {
        val key = chunkKey(chunkX, chunkZ)
        val now = System.currentTimeMillis()
        val cached = cache[key]

        if (cached != null && now - cached.timestamp < ttlMillis) {
            return cached.weather
        }

        val weather = compute()

        if (cache.size >= maxCacheSize) {
            // O(n) 单次遍历找最旧条目移除，避免 O(n log n) 排序
            var oldestKey: Long? = null
            var oldestTimestamp = Long.MAX_VALUE
            for (entry in cache.entries) {
                if (entry.value.timestamp < oldestTimestamp) {
                    oldestTimestamp = entry.value.timestamp
                    oldestKey = entry.key
                }
            }
            oldestKey?.let { cache.remove(it) }
        }
        cache[key] = CachedWeather(weather, now)

        return weather
    }

    fun clear() {
        cache.clear()
    }

    private fun chunkKey(x: Int, z: Int): Long {
        return (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)
    }

    private data class CachedWeather(
        val weather: WeatherState,
        val timestamp: Long,
    )
}
