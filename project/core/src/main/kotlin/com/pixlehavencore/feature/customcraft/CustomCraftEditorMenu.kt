package com.pixlehavencore.feature.customcraft

import com.pixlehavencore.bridge.TextBridge
import com.pixlehavencore.util.TextUtils
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.event.SubscribeEvent
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

object CustomCraftEditorMenu {

    private val sessions = mutableMapOf<Int, EditorSession>()

    private val matSlots = intArrayOf(10, 11, 12, 19, 20, 21, 28, 29, 30)
    private val resultSlot = 25
    private val saveSlot = 40
    private val toggleSlot = 41

    private val mapLayout = listOf(
        "#########",
        "#MMM#####",
        "#MMM##AR#",
        "#MMM#####",
        "####ST###"
    )

    private data class EditorSession(
        val player: Player,
        val recipeId: String,
        var recipeType: RecipeType = RecipeType.SHAPED
    )

    fun open(player: Player, recipeId: String) {
        val title = TextBridge.toLegacy(TextBridge.fromMiniMessage("<dark_gray>编辑配方 - $recipeId"))
        player.openMenu<Chest>(title) {
            rows(5)
            map(*mapLayout.toTypedArray())

            set('#', decorativeItem())
            set('A', arrowItem())
            set('S', saveButton())
            set('T', toggleButton(RecipeType.SHAPED))

            onBuild { _, inv ->
                sessions[System.identityHashCode(inv)] = EditorSession(player, recipeId)
            }
        }
    }

    @SubscribeEvent
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val session = sessions[System.identityHashCode(event.view.topInventory)] ?: return
        if (session.player.uniqueId != player.uniqueId) return
        if (event.clickedInventory != event.view.topInventory) return

        val slot = event.rawSlot
        when (slot) {
            saveSlot -> {
                event.isCancelled = true
                saveRecipe(player, event.view.topInventory, session)
            }
            toggleSlot -> {
                event.isCancelled = true
                session.recipeType = when (session.recipeType) {
                    RecipeType.SHAPED -> RecipeType.SHAPELESS
                    RecipeType.SHAPELESS -> RecipeType.SHAPED
                }
                event.view.topInventory.setItem(toggleSlot, toggleButton(session.recipeType))
            }
            !in editableSlots() -> {
                event.isCancelled = true
            }
        }
    }

    @SubscribeEvent
    fun onClose(event: InventoryCloseEvent) {
        sessions.remove(System.identityHashCode(event.inventory))
    }

    private fun editableSlots(): Set<Int> = matSlots.toSet() + resultSlot + saveSlot + toggleSlot

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
        player.sendMessage(TextUtils.parse("&a配方 &e${session.recipeId} &a已保存并注册"))
        player.closeInventory()
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

    private fun saveButton(): ItemStack {
        val item = ItemStack(Material.LIME_CONCRETE)
        TextBridge.setDisplayName(item, TextUtils.parseItem("&a保存配方"))
        return item
    }

    private fun toggleButton(type: RecipeType): ItemStack {
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
