package com.pixlehavencore.feature.realworld.tick.player

import com.pixlehavencore.feature.realworld.GlobalEnvState
import com.pixlehavencore.feature.realworld.PlayerEnvState
import com.pixlehavencore.feature.realworld.fracture.FractureEngine
import com.pixlehavencore.feature.realworld.tick.PlayerSubsystemTicker
import org.bukkit.entity.Player

object FractureTicker : PlayerSubsystemTicker {
    override fun tick(player: Player, state: PlayerEnvState, global: GlobalEnvState, dt: Int) {
        FractureEngine.applyEffects(player, state, dt)
    }
}