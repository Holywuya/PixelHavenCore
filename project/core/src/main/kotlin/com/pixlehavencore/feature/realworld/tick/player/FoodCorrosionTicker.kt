package com.pixlehavencore.feature.realworld.tick.player

import com.pixlehavencore.feature.realworld.GlobalEnvState
import com.pixlehavencore.feature.realworld.PlayerEnvState
import com.pixlehavencore.feature.realworld.foodcorrosion.FoodCorrosionEngine
import com.pixlehavencore.feature.realworld.tick.PlayerSubsystemTicker
import org.bukkit.entity.Player
import taboolib.platform.util.PlayerSessionMap

object FoodCorrosionTicker : PlayerSubsystemTicker {
    // 每个玩家独立的腐败检查计时器，60 秒检查一次而非每 tick（2 秒）
    private val playerTimers = PlayerSessionMap<Double>({ 0.0 })
    private const val CHECK_INTERVAL_SECONDS = 60.0

    override fun tick(player: Player, state: PlayerEnvState, global: GlobalEnvState, dt: Int) {
        val timer = (playerTimers[player.uniqueId] ?: 0.0) - dt
        if (timer <= 0.0) {
            playerTimers[player.uniqueId] = CHECK_INTERVAL_SECONDS
            FoodCorrosionEngine.tickPlayer(player)
        } else {
            playerTimers[player.uniqueId] = timer
        }
    }
}