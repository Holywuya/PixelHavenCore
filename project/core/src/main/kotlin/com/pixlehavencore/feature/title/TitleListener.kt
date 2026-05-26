package com.pixlehavencore.feature.title

import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
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

    @SubscribeEvent
    fun onInventoryClick(event: InventoryClickEvent) {
        if (TitleMenu.getOpenHolder(event.view.topInventory) == null) return
        val player = event.whoClicked as? Player ?: return
        if (event.clickedInventory == null || event.clickedInventory != event.view.topInventory) return
        event.isCancelled = true
        TitleMenu.handleClick(player, event.rawSlot)
    }

    @SubscribeEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        if (TitleMenu.getOpenHolder(event.inventory) != null) {
            TitleMenu.unregister(player.uniqueId)
        }
    }
}
