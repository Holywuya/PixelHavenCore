package com.pixlehavencore.feature.realworld.temperature

import com.pixlehavencore.feature.realworld.TemperaturePhase
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 通过 Bukkit API 设置 freezeTicks 触发客户端原生霜冻遮罩。
 * 值域 0~299，>= 300 会触发服务端冻伤减速。
 *
 * 服务端只在玩家处于细雪/冻水中时才递减 freezeTicks，
 * 因此只要阶段不变，值会一直保持不变，不会闪烁。
 * 仅在温度阶段变化时才更新 freezeTicks，避免 HUD 每 tick 重置导致闪烁。
 */
object FrostOverlay {

    private val lastPhases = ConcurrentHashMap<UUID, TemperaturePhase>()

    fun update(player: Player, phase: TemperaturePhase) {
        val lastPhase = lastPhases[player.uniqueId]
        if (lastPhase == phase) {
            return
        }

        val ticks = when (phase) {
            TemperaturePhase.COLD -> TemperatureSettings.frostOverlayColdIntensity
            TemperaturePhase.SEVERE_COLD -> TemperatureSettings.frostOverlaySevereColdIntensity
            else -> 0
        }

        player.freezeTicks = ticks
        if (ticks > 0) {
            lastPhases[player.uniqueId] = phase
        } else {
            lastPhases.remove(player.uniqueId)
        }
    }

    fun clear(player: Player) {
        player.freezeTicks = 0
        lastPhases.remove(player.uniqueId)
    }
}
