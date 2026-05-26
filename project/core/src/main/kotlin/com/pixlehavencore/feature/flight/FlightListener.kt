package com.pixlehavencore.feature.flight

import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit

object FlightListener {

    @SubscribeEvent
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!FlightSettings.enabled) return
        FlightService.handlePlayerJoin(event.player)
    }

    @SubscribeEvent
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (!FlightSettings.enabled) return
        FlightService.handlePlayerQuit(event.player)
    }

    @SubscribeEvent
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        if (!FlightSettings.enabled) return
        FlightService.handleWorldChange(event.player)
    }

    @SubscribeEvent
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        if (!FlightSettings.enabled) return
        submit(delay = 2L) {
            FlightService.handleRespawn(event.player)
        }
    }
}
