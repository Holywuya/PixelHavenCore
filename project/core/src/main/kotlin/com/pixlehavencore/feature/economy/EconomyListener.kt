package com.pixlehavencore.feature.economy

import org.bukkit.event.player.PlayerJoinEvent
import taboolib.common.platform.event.SubscribeEvent

object EconomyListener {

    @SubscribeEvent
    fun onJoin(event: PlayerJoinEvent) {
        if (!EconomySettings.enabled) {
            return
        }
        EconomyStorageService.markSeen(event.player.uniqueId)
    }
}
