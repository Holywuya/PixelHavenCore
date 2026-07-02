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
    private val arrowSlot = 24
    private val resultSlot = 25
    private val saveSlot = 40
    private val toggleTypeSlot = 41

    private data class EditorSession(
        val player: Player,
        val recipeId: String,
        var recipeType: RecipeType = RecipeType.SHAPED
    )

    fun open(player: Player, recipeId: String) {
        val title = TextUtils.parse("&8编辑配方 - $recipeId")
        val inv = Bukkit.createInventory(null, ROWS * 9, title)
        val filler = decorativeItem()

        for (slot in 0 until ROWS * 9) {
            if (slot !in matSlots && slot != arrowSlot && slot != resultSlot && slot != saveSlot && slot != toggleTypeSlot) {
                inv.setItem(slot, filler)
            }
        }

        inv.setItem(arrowSlot, arrowItem())

        val session = EditorSession(player, recipeId)
        inv.setItem(saveSlot, actionItem(Material.LIME_CONCRETE, "&a保存配方", "save"))
        inv.setItem(toggleTypeSlot, toggleTypeItem(session.recipeType))

        editSessions[System.identityHashCode(inv)] = session
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

        if (slot == saveSlot) {
            event.isCancelled = true
            saveRecipe(player, event.view.topInventory, session)
            return
        }

        if (slot == toggleTypeSlot) {
            event.isCancelled = true
            session.recipeType = when (session.recipeType) {
                RecipeType.SHAPED -> RecipeType.SHAPELESS
                RecipeType.SHAPELESS -> RecipeType.SHAPED
            }
            event.view.topInventory.setItem(toggleTypeSlot, toggleTypeItem(session.recipeType))
            return
        }

        if (slot !in matSlots && slot != resultSlot) {
            event.isCancelled = true
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

        val recipe = CraftingRecipe(
            id = session.recipeId,
            type = session.recipeType,
            materials = materials,
            result = result
        )

        CustomCraftService.saveAndRegister(recipe)
        val key = NamespacedKey("phcore", session.recipeId.lowercase())
        runCatching { player.discoverRecipe(key) }
        player.closeInventory()
        player.sendMessage(TextUtils.parse("&a配方 &e${session.recipeId} &a已保存并注册"))
    }

    private fun decorativeItem(): ItemStack {
        val item = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        TextBridge.setDisplayName(item, TextUtils.parseItem("&7"))
        return item
    }

    private fun arrowItem(): ItemStack {
        val item = ItemStack(Material.ARROW)
        TextBridge.setDisplayName(item, TextUtils.parseItem("&7→"))
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

    private fun toggleTypeItem(type: RecipeType): ItemStack {
        val name = when (type) {
            RecipeType.SHAPED -> "&a有序合成"
            RecipeType.SHAPELESS -> "&e无序合成"
        }
        val lore = when (type) {
            RecipeType.SHAPED -> listOf("&7物品必须按指定位置放置", "&7点击切换为无序合成")
            RecipeType.SHAPELESS -> listOf("&7物品可放在任意位置", "&7点击切换为有序合成")
        }
        val item = ItemStack(Material.CRAFTING_TABLE)
        TextBridge.setDisplayName(item, TextUtils.parseItem(name))
        @Suppress("UNCHECKED_CAST")
        TextBridge.setLore(item, TextUtils.parseItemLore(lore) as List<net.kyori.adventure.text.Component>)
        return item
    }
}
