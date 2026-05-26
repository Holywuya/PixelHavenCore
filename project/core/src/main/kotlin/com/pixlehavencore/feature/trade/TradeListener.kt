package com.pixlehavencore.feature.trade

import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.platform.event.SubscribeEvent
object TradeListener {

    @SubscribeEvent
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!TradeService.isTradeInventory(player, event.view.topInventory)) {
            return
        }
        event.isCancelled = if (event.clickedInventory == event.view.bottomInventory) {
            TradeService.handleBottomClick(player, event.action, event.currentItem, event.isShiftClick)
        } else {
            TradeService.handleClick(player, event.rawSlot)
        }
    }

    @SubscribeEvent
    fun onInteractPlayer(event: PlayerInteractEntityEvent) {
        val player = event.player
        val target = event.rightClicked as? Player ?: return
        if (!player.isSneaking) {
            return
        }
        TradeService.requestTradeByInteract(player, target)
        event.isCancelled = true
    }

    @SubscribeEvent
    fun onClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        if (TradeService.isTradeInventory(player, event.inventory)) {
            TradeService.handleClose(player)
        }
    }

    @SubscribeEvent
    fun onDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!TradeService.isTradeInventory(player, event.view.topInventory)) {
            return
        }
        if (event.rawSlots.any { it < event.view.topInventory.size }) {
            event.isCancelled = true
        }
    }

    @SubscribeEvent
    fun onQuit(event: PlayerQuitEvent) {
        TradeService.handlePlayerQuit(event.player)
    }
}
