package com.pixlehavencore.feature.base.firstjoin

import org.bukkit.event.player.PlayerJoinEvent
import taboolib.common.platform.event.SubscribeEvent

object FirstJoinListener {

    @SubscribeEvent
    fun onPlayerJoin(event: PlayerJoinEvent) {
        FirstJoinService.handleJoin(event.player)
    }
}
