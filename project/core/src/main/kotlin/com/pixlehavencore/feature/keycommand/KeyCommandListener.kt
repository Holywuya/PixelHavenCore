package com.pixlehavencore.feature.keycommand

import org.bukkit.event.player.PlayerSwapHandItemsEvent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent

object KeyCommandListener {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onSwapHand(event: PlayerSwapHandItemsEvent) {
        if (!KeyCommandSettings.enabled) {
            return
        }
        if (KeyCommandService.triggerF(event.player, event.player.isSneaking)) {
            event.isCancelled = true
        }
    }
}
