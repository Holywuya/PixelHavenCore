package com.pixlehavencore.feature.optimization.viewdistance

import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import taboolib.common.platform.event.SubscribeEvent

object ViewDistanceListener {

    @SubscribeEvent
    fun onJoin(event: PlayerJoinEvent) {
        ViewDistanceService.onJoin(event.player)
    }

    @SubscribeEvent
    fun onQuit(event: PlayerQuitEvent) {
        ViewDistanceService.onQuit(event.player)
    }

    @SubscribeEvent
    fun onMove(event: PlayerMoveEvent) {
        val to = event.to ?: return
        if (event.from.x != to.x || event.from.y != to.y || event.from.z != to.z) {
            ViewDistanceService.markMoved(event.player)
        }
    }

    @SubscribeEvent
    fun onTeleport(event: PlayerTeleportEvent) {
        ViewDistanceService.markMoved(event.player)
    }
}
