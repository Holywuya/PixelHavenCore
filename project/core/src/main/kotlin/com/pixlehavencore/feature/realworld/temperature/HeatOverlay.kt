package com.pixlehavencore.feature.realworld.temperature

import com.pixlehavencore.feature.realworld.TemperaturePhase
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.platform.util.PlayerSessionMap
import java.util.UUID

/**
 * 通过 WorldBorder 的 warningDistance 制造全屏红色叠加效果。
 * 效果范围 0.0~1.0，0 表示清除。
 */
object HeatOverlay {

    private const val BORDER_SIZE = 200_000.0
    private const val HALF_SIZE = BORDER_SIZE / 2.0

    private val activeBorders = PlayerSessionMap<Boolean>({ false })

    fun update(player: Player, phase: TemperaturePhase) {
        val intensity = when (phase) {
            TemperaturePhase.SEVERE_HEAT -> TemperatureSettings.heatOverlaySevereIntensity
            TemperaturePhase.HEAT -> TemperatureSettings.heatOverlayHeatIntensity
            else -> 0.0
        }
        if (intensity <= 0.01) {
            clear(player)
            return
        }
        val border = Bukkit.createWorldBorder()
        border.setCenter(player.location.x, player.location.z)
        border.size = BORDER_SIZE
        border.warningDistance = (HALF_SIZE + intensity * HALF_SIZE).toInt()
        player.worldBorder = border
        activeBorders[player.uniqueId] = true
    }

    fun clear(player: Player) {
        if (activeBorders.remove(player.uniqueId) != null) {
            player.worldBorder = null
        }
    }

    fun onPlayerQuit(player: Player) {
        // PlayerSessionMap auto-cleans on quit; no manual removal needed
    }
}
