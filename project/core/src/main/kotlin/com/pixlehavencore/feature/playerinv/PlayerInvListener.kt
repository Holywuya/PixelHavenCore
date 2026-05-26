package com.pixlehavencore.feature.playerinv

import com.pixlehavencore.bridge.TextBridge
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import io.papermc.paper.event.player.AsyncChatEvent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent

object PlayerInvListener {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onInventoryClick(event: InventoryClickEvent) {
        if (!PlayerInvSettings.enabled) {
            return
        }
        val player = event.whoClicked as? Player ?: return
        val top = event.view.topInventory

        if (event.clickedInventory != top || event.slot < 0) {
            return
        }

        if (PlayerInvService.handleInventoryClick(player, top, event.slot, event.click)) {
            event.isCancelled = true
        }
    }

    @SubscribeEvent
    fun onChatInput(event: AsyncChatEvent) {
        if (event.isCancelled) return
        if (!PlayerInvSettings.enabled) {
            return
        }
        val message = TextBridge.toPlain(event.message())
        if (PlayerInvService.handleMemberChatInput(event.player, message)) {
            event.isCancelled = true
        }
    }

    @SubscribeEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (!PlayerInvSettings.enabled) {
            return
        }
        PlayerInvService.handleClose(event.player as? Player ?: return, event.inventory)
    }
}
