package com.pixlehavencore.feature.realworld.tick.player

import com.pixlehavencore.feature.realworld.GlobalEnvState
import com.pixlehavencore.feature.realworld.PlayerEnvState
import com.pixlehavencore.feature.realworld.temperature.TemperatureEngine
import com.pixlehavencore.feature.realworld.tick.PlayerSubsystemTicker
import org.bukkit.entity.Player

object TemperatureTicker : PlayerSubsystemTicker {
    override fun tick(player: Player, state: PlayerEnvState, global: GlobalEnvState, dt: Int) {
        TemperatureEngine.compute(player, state, global, dt)
    }
}