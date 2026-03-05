package com.pixlehavencore.feature.menu

import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import taboolib.common.platform.event.SubscribeEvent

object TrMenuLiteListener {

    @SubscribeEvent
    fun onInventoryClick(event: InventoryClickEvent) {
        if (!SimpleMenuSettings.enabled) {
            return
        }
        val player = event.whoClicked as? Player ?: return
        val handled = TrMenuLiteService.handleClick(player, event.view.topInventory, event.rawSlot, event.click)
        if (handled) {
            event.isCancelled = true
        }
    }

    @SubscribeEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (!SimpleMenuSettings.enabled) {
            return
        }
        TrMenuLiteService.handleClose(event.player as? Player ?: return)
    }
}
