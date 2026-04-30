package com.pixlehavencore.feature.craftingbench

import net.momirealms.craftengine.bukkit.api.event.CustomBlockBreakEvent
import net.momirealms.craftengine.bukkit.api.event.CustomBlockInteractEvent
import net.momirealms.craftengine.bukkit.api.event.FurnitureBreakEvent
import net.momirealms.craftengine.bukkit.api.event.FurnitureInteractEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerJoinEvent
import taboolib.common.platform.event.SubscribeEvent

object CraftingBenchListener {

    fun isAdminGuiInventory(player: org.bukkit.entity.Player, inventory: org.bukkit.inventory.Inventory): Boolean {
        return AdminGuiMenu.isAdminGuiInventory(player.uniqueId, inventory)
    }

    @SubscribeEvent
    fun onCustomBlockInteract(event: CustomBlockInteractEvent) {
        if (!CraftingBenchService.isEnabled()) return
        if (event.action() != CustomBlockInteractEvent.Action.RIGHT_CLICK) return
        val player = event.player()
        val tier = CraftingBenchService.resolveTierByBlockId(event.customBlock().id().asString()) ?: return
        event.isCancelled = true
        CraftingBenchMenu.open(player, tier)
    }

    @SubscribeEvent
    fun onFurnitureInteract(event: FurnitureInteractEvent) {
        if (!CraftingBenchService.isEnabled()) return
        val player = event.player()
        val tier = CraftingBenchService.resolveTierByBlockId(event.furniture().id().asString()) ?: return
        event.isCancelled = true
        CraftingBenchMenu.open(player, tier)
    }

    @SubscribeEvent
    fun onCustomBlockBreak(event: CustomBlockBreakEvent) {
        if (!CraftingBenchService.isEnabled()) return
        if (CraftingBenchService.resolveTierByBlockId(event.customBlock().id().asString()) == null) return
    }

    @SubscribeEvent
    fun onFurnitureBreak(event: FurnitureBreakEvent) {
        if (!CraftingBenchService.isEnabled()) return
        if (CraftingBenchService.resolveTierByBlockId(event.furniture().id().asString()) == null) return
    }

    @SubscribeEvent
    fun onInventoryClick(event: InventoryClickEvent) {
        val clicker = event.whoClicked as? org.bukkit.entity.Player
        if (clicker != null && isAdminGuiInventory(clicker, event.view.topInventory)) {
            AdminGuiListener.onInventoryClick(event)
            return
        }
        val holder = event.inventory.holder as? CraftingBenchMenuHolder ?: return
        val player = event.whoClicked as? org.bukkit.entity.Player ?: return
        if (event.clickedInventory == null || event.clickedInventory != event.view.topInventory) {
            return
        }
        event.isCancelled = true
        val tier = CraftingBenchSettings.getTier(holder.tierId) ?: return
        when (holder.mode) {
            CraftingBenchMenuMode.LIST -> handleListClick(event.rawSlot, player, holder, tier)
            CraftingBenchMenuMode.DETAIL -> handleDetailClick(event.rawSlot, player, holder, tier)
        }
    }

    private fun handleListClick(slot: Int, player: org.bukkit.entity.Player, holder: CraftingBenchMenuHolder, tier: BenchTier) {
        val previews = CraftingBenchService.getAvailableRecipes(player, tier)
        if (slot in 0 until 45) {
            val recipeIndex = holder.page * 45 + slot
            val preview = previews.getOrNull(recipeIndex) ?: return
            CraftingBenchMenu.openRecipeDetail(player, tier, holder.page, preview.recipe.id, 1)
            return
        }
        if (slot == 52 && holder.page > 0) {
            CraftingBenchMenu.open(player, tier, holder.page - 1)
            return
        }
        if (slot == 53) {
            CraftingBenchMenu.open(player, tier, holder.page + 1)
        }
    }

    private fun handleDetailClick(slot: Int, player: org.bukkit.entity.Player, holder: CraftingBenchMenuHolder, tier: BenchTier) {
        val recipeId = holder.recipeId ?: return
        when (slot) {
            47 -> CraftingBenchMenu.openRecipeDetail(player, tier, holder.page, recipeId, (holder.craftCount - 1).coerceAtLeast(1))
            49 -> {
                val result = CraftingBenchService.submitCraft(player, tier, recipeId, holder.craftCount)
                player.sendMessage(((if (result.success) "&a" else "&c") + result.message).replace('&', '§'))
                CraftingBenchMenu.openRecipeDetail(player, tier, holder.page, recipeId, holder.craftCount)
            }
            50 -> CraftingBenchMenu.openRecipeDetail(player, tier, holder.page, recipeId, holder.craftCount + 1)
            53 -> CraftingBenchMenu.open(player, tier, holder.page)
        }
    }

    @SubscribeEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? org.bukkit.entity.Player
        if (player != null && isAdminGuiInventory(player, event.inventory)) {
            AdminGuiListener.onInventoryClose(event)
            return
        }
        if (event.inventory.holder !is CraftingBenchMenuHolder) {
            return
        }
        CraftingBenchMenu.unregister(player?.uniqueId ?: return)
    }

    @SubscribeEvent
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!CraftingBenchService.isEnabled()) return
        CraftingBenchService.flushPendingClaims(event.player)
    }
}
