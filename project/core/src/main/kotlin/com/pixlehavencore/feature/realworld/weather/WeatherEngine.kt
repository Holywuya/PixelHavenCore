package com.pixlehavencore.feature.realworld.weather

import com.pixlehavencore.feature.realworld.GlobalEnvState
import com.pixlehavencore.feature.realworld.WeatherType
import com.pixlehavencore.feature.realworld.tick.GlobalTickContext
import org.bukkit.Bukkit
import taboolib.common.platform.function.info

/**
 * 天气引擎 - 双层架构协调器
 * 底层：噪声驱动降雨判断（RealLife 风格）
 * 上层：降雨强度映射到天气类型（影响温度/伤害等）
 */
object WeatherEngine {

    /**
     * 初始化天气引擎
     */
    fun init(worldSeed: Int) {
        if (WeatherSettings.localEnabled) {
            ChunkWeatherEngine.init(worldSeed, WeatherSettings.localNoiseFrequency)
            WeatherCache.setMaxSize(WeatherSettings.localCacheMaxSize)
            info("[RealWorld] 局部天气引擎已初始化 (频率: ${WeatherSettings.localNoiseFrequency})")
        }
    }

    /**
     * 天气系统 tick
     * 噪声驱动架构下无需全局 tick，所有状态通过 WeatherQuery 按需计算
     */
    fun tick(global: GlobalEnvState, tickIntervalSeconds: Int, @Suppress("UNUSED_PARAMETER") context: GlobalTickContext) {
        // 双层架构下天气状态完全由噪声决定，无需全局决策
        // 保留此方法签名以兼容 GlobalTickContext 接口
    }

    /**
     * 设置强制天气（管理员命令）
     */
    fun setWeather(global: GlobalEnvState, weather: WeatherType, intensity: Double = 0.7) {
        global.forcedWeather = weather
        global.forcedWeatherIntensity = intensity.coerceIn(0.0, 1.0)
        info("[RealWorld] 强制天气已设置为: ${weather.displayName} (强度: ${"%.2f".format(intensity)})")
    }

    /**
     * 清除强制天气，恢复噪声驱动
     */
    fun clearForcedWeather(global: GlobalEnvState) {
        global.forcedWeather = null
        global.forcedWeatherIntensity = 0.0
        info("[RealWorld] 强制天气已清除，恢复噪声驱动")
    }

}
