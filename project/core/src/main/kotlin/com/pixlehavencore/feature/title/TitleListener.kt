package com.pixlehavencore.feature.title

import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.platform.event.SubscribeEvent

object TitleListener {

    @SubscribeEvent
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!TitleSettings.enabled) return
        TitleService.onPlayerJoin(event.player)
    }

    @SubscribeEvent
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (!TitleSettings.enabled) return
        TitleService.onPlayerQuit(event.player)
    }

}