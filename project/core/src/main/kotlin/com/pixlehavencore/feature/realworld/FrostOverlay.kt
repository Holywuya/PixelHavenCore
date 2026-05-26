package com.pixlehavencore.feature.realworld

import org.bukkit.entity.Player

/**
 * 通过 Bukkit API 设置 freezeTicks 触发客户端原生霜冻遮罩。
 * 值域 0~299，>= 300 会触发服务端冻伤减速。
 */
object FrostOverlay {

    fun update(player: Player, phase: TemperaturePhase) {
        val ticks = when (phase) {
            TemperaturePhase.COLD -> RealWorldSettings.frostOverlayColdIntensity
            TemperaturePhase.SEVERE_COLD -> RealWorldSettings.frostOverlaySevereColdIntensity
            else -> 0
        }
        player.freezeTicks = ticks
    }

    fun clear(player: Player) {
        player.freezeTicks = 0
    }
}
