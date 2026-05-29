package com.pixlehavencore.feature.realworld.tick.global

import com.pixlehavencore.feature.realworld.GlobalEnvState
import com.pixlehavencore.feature.realworld.tick.GlobalSubsystemTicker
import com.pixlehavencore.feature.realworld.tick.GlobalTickContext
import com.pixlehavencore.feature.realworld.weather.WeatherEngine

object WeatherTicker : GlobalSubsystemTicker {
    override fun tick(global: GlobalEnvState, dt: Int, context: GlobalTickContext) {
        WeatherEngine.tick(global, dt, context)
    }
}
