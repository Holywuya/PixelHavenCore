package com.pixlehavencore.feature.playtime

import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.platform.event.SubscribeEvent

object PlaytimeListener {

    @SubscribeEvent
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!PlaytimeSettings.enabled) return
        PlaytimeService.onPlayerJoin(event.player)
    }

    @SubscribeEvent
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (!PlaytimeSettings.enabled) return
        PlaytimeService.onPlayerQuit(event.player)
    }
}
