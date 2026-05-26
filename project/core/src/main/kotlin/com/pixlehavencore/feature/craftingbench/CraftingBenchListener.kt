package com.pixlehavencore.feature.craftingbench

import net.momirealms.craftengine.bukkit.api.event.CustomBlockBreakEvent
import net.momirealms.craftengine.bukkit.api.event.CustomBlockInteractEvent
import net.momirealms.craftengine.bukkit.api.event.FurnitureBreakEvent
import net.momirealms.craftengine.bukkit.api.event.FurnitureInteractEvent
import com.pixlehavencore.util.TextUtils
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
        val holder = CraftingBenchMenu.getOpenHolder(event.view.topInventory) ?: return
        val player = event.whoClicked as? org.bukkit.entity.Player ?: return
        event.isCancelled = true
        if (event.clickedInventory != event.view.topInventory) return
        val tier = CraftingBenchSettings.getTier(holder.tierId) ?: return
        when (holder.mode) {
            CraftingBenchMenuMode.LIST -> handleListClick(event.rawSlot, player, holder, tier)
            CraftingBenchMenuMode.DETAIL -> handleDetailClick(event.rawSlot, player, holder, tier)
        }
    }

    private fun handleListClick(slot: Int, player: org.bukkit.entity.Player, holder: CraftingBenchMenuHolder, tier: BenchTier) {
        val allPreviews = CraftingBenchService.getAvailableRecipes(player, tier)
        val filtered = if (holder.category != null) allPreviews.filter { it.recipe.category == holder.category } else allPreviews
        val pageSize = CraftingBenchSettings.guiPageSize
        val recipeStartSlot = CraftingBenchSettings.guiRecipeStartSlot
        val recipeEndSlot = recipeStartSlot + pageSize
        if (slot in recipeStartSlot until recipeEndSlot) {
            val recipeIndex = holder.page * pageSize + (slot - recipeStartSlot)
            val preview = filtered.getOrNull(recipeIndex) ?: return
            CraftingBenchMenu.openRecipeDetail(player, tier, holder.category, holder.page, preview.recipe.id, 1)
            return
        }
        if (slot == CraftingBenchSettings.guiPrevPageSlot && holder.page > 0) {
            CraftingBenchMenu.open(player, tier, holder.category, holder.page - 1)
            return
        }
        if (slot == CraftingBenchSettings.guiNextPageSlot) {
            CraftingBenchMenu.open(player, tier, holder.category, holder.page + 1)
        }
    }

    private fun handleDetailClick(slot: Int, player: org.bukkit.entity.Player, holder: CraftingBenchMenuHolder, tier: BenchTier) {
        val recipeId = holder.recipeId ?: return
        when (slot) {
            CraftingBenchMenu.SLOT_DETAIL_MINUS -> CraftingBenchMenu.openRecipeDetail(player, tier, holder.category, holder.page, recipeId, (holder.craftCount - 1).coerceAtLeast(1))
            CraftingBenchMenu.SLOT_DETAIL_CRAFT -> {
                val result = CraftingBenchService.submitCraft(player, tier, recipeId, holder.craftCount)
                player.sendMessage(TextUtils.parse((if (result.success) "&a" else "&c") + result.message))
                CraftingBenchMenu.openRecipeDetail(player, tier, holder.category, holder.page, recipeId, holder.craftCount)
            }
            CraftingBenchMenu.SLOT_DETAIL_PLUS -> CraftingBenchMenu.openRecipeDetail(player, tier, holder.category, holder.page, recipeId, holder.craftCount + 1)
            CraftingBenchMenu.SLOT_DETAIL_BACK -> CraftingBenchMenu.open(player, tier, holder.category, holder.page)
        }
    }

    @SubscribeEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? org.bukkit.entity.Player
        if (player != null && isAdminGuiInventory(player, event.inventory)) {
            AdminGuiListener.onInventoryClose(event)
            return
        }
        if (CraftingBenchMenu.getOpenHolder(event.inventory) == null) {
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
