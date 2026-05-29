package com.pixlehavencore.feature.realworld.tick.global

import com.pixlehavencore.feature.realworld.GlobalEnvState
import com.pixlehavencore.feature.realworld.season.SeasonEngine
import com.pixlehavencore.feature.realworld.tick.GlobalSubsystemTicker
import com.pixlehavencore.feature.realworld.tick.GlobalTickContext

object SeasonTicker : GlobalSubsystemTicker {
    override fun tick(global: GlobalEnvState, dt: Int, context: GlobalTickContext) {
        SeasonEngine.tick(global, dt)
    }
}
