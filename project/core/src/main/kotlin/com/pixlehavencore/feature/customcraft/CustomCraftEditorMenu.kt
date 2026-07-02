package com.pixlehavencore.feature.customcraft

import com.pixlehavencore.bridge.TextBridge
import com.pixlehavencore.util.TextUtils
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import taboolib.common.platform.event.SubscribeEvent

object CustomCraftEditorMenu {

    private const val ROWS = 5
    private val editSessions = mutableMapOf<Int, EditorSession>()
    private val actionKey = NamespacedKey("phcore", "customcraft_action")

    private val matSlots = intArrayOf(10, 11, 12, 19, 20, 21, 28, 29, 30)
    private val resultSlot = 25
    private val saveSlot = 40
    private val clearSlot = 41
    private val decorativeSlots = listOf(
        0, 1, 2, 7, 8,
        9, 15, 16, 17,
        18, 22, 26,
        27, 33, 34, 35,
        36, 37, 38, 39, 42, 43, 44
    )

    private data class EditorSession(
        val player: Player,
        val recipeId: String
    )

    fun open(player: Player, recipeId: String) {
        val title = TextUtils.parse("&8编辑配方 - $recipeId")
        val inv = Bukkit.createInventory(null, ROWS * 9, title)
        val filler = decorativeItem()

        decorativeSlots.forEach { inv.setItem(it, filler) }

        inv.setItem(saveSlot, actionItem(Material.LIME_CONCRETE, "&a保存配方", "save"))
        inv.setItem(clearSlot, actionItem(Material.RED_CONCRETE, "&c清空所有格子", "clear"))

        editSessions[System.identityHashCode(inv)] = EditorSession(player, recipeId)
        player.openInventory(inv)
    }

    @SubscribeEvent
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val session = editSessions[System.identityHashCode(event.view.topInventory)] ?: return
        if (session.player.uniqueId != player.uniqueId) return

        val clicked = event.clickedInventory
        if (clicked != event.view.topInventory) return

        val slot = event.slot
        if (slot in decorativeSlots) {
            event.isCancelled = true
            return
        }

        if (slot == saveSlot) {
            event.isCancelled = true
            saveRecipe(player, event.view.topInventory, session)
            return
        }

        if (slot == clearSlot) {
            event.isCancelled = true
            clearEditor(event.view.topInventory)
            return
        }
    }

    @SubscribeEvent
    fun onClose(event: InventoryCloseEvent) {
        editSessions.remove(System.identityHashCode(event.inventory))
    }

    private fun saveRecipe(player: Player, inv: org.bukkit.inventory.Inventory, session: EditorSession) {
        val materials = mutableListOf<RecipeIngredient>()

        for (i in matSlots.indices) {
            val item = inv.getItem(matSlots[i])
            if (item != null && item.type != Material.AIR) {
                val ing = CustomCraftRecipeLoader.itemToIngredient(item)
                materials.add(ing.copy(slot = i))
            }
        }

        val resultItem = inv.getItem(resultSlot)
        if (resultItem == null || resultItem.type == Material.AIR) {
            player.sendMessage(TextUtils.parse("&c请在 R 格放入合成结果物品"))
            return
        }
        val result = CustomCraftRecipeLoader.itemToIngredient(resultItem)

        val type = if (materials.size <= 4) RecipeType.SHAPELESS else RecipeType.SHAPED

        val recipe = CraftingRecipe(
            id = session.recipeId,
            type = type,
            materials = materials,
            result = result
        )

        CustomCraftService.saveAndRegister(recipe)
        player.closeInventory()
        player.sendMessage(TextUtils.parse("&a配方 &e${session.recipeId} &a已保存并注册"))
    }

    private fun clearEditor(inv: org.bukkit.inventory.Inventory) {
        matSlots.forEach { inv.setItem(it, null) }
        inv.setItem(resultSlot, null)
    }

    private fun decorativeItem(): ItemStack {
        val item = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        TextBridge.setDisplayName(item, TextUtils.parseItem("&7"))
        return item
    }

    private fun actionItem(material: Material, name: String, action: String): ItemStack {
        val item = ItemStack(material)
        TextBridge.setDisplayName(item, TextUtils.parseItem(name))
        item.editMeta { meta ->
            meta.persistentDataContainer.set(actionKey, PersistentDataType.STRING, action)
        }
        return item
    }
}
