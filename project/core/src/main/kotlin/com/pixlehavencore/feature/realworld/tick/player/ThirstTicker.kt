package com.pixlehavencore.feature.realworld.tick.player

import com.pixlehavencore.feature.realworld.GlobalEnvState
import com.pixlehavencore.feature.realworld.PlayerEnvState
import com.pixlehavencore.feature.realworld.thirst.ThirstEngine
import com.pixlehavencore.feature.realworld.tick.PlayerSubsystemTicker
import org.bukkit.entity.Player

object ThirstTicker : PlayerSubsystemTicker {
    override fun tick(player: Player, state: PlayerEnvState, global: GlobalEnvState, dt: Int) {
        ThirstEngine.compute(player, state, global, dt)
    }
}