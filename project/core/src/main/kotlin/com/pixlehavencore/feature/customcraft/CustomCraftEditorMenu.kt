package com.pixlehavencore.feature.customcraft

import com.pixlehavencore.bridge.TextBridge
import com.pixlehavencore.util.TextUtils
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import java.util.concurrent.ConcurrentHashMap

object CustomCraftEditorMenu {

    private val sessions = ConcurrentHashMap<Int, EditorSession>()

    private val matSlots = intArrayOf(10, 11, 12, 19, 20, 21, 28, 29, 30)
    private val resultSlotIndex = 25
    private val saveSlotIndex = 40
    private val toggleSlotIndex = 41

    private val editableChars = setOf('0', '1', '2', '3', '4', '5', '6', '7', '8', 'R')

    private data class EditorSession(
        val player: Player,
        val recipeId: String,
        var recipeType: RecipeType = RecipeType.SHAPED
    )

    fun open(player: Player, recipeId: String, existingRecipe: CraftingRecipe? = null) {
        val typeLabel = if (existingRecipe != null) "编辑" else "创建"
        val title = TextBridge.toLegacy(TextBridge.fromMiniMessage("<dark_gray>${typeLabel}配方 - $recipeId"))
        player.openMenu<Chest>(title) {
            rows(5)
            handLocked(false)
            map(
                "#########",
                "#012#####",
                "#345##AR#",
                "#678#####",
                "####ST###"
            )

            val initialType = existingRecipe?.type ?: RecipeType.SHAPED

            set('#', decorativeItem())
            set('A', arrowItem())
            set('S', saveButton())
            set('T', toggleButton(initialType))

            onClick('S') { e ->
                val session = sessions[System.identityHashCode(e.inventory)] ?: return@onClick
                e.isCancelled = true
                saveRecipe(e.clicker, e.inventory, session)
            }

            onClick('T') { e ->
                val session = sessions[System.identityHashCode(e.inventory)] ?: return@onClick
                e.isCancelled = true
                session.recipeType = when (session.recipeType) {
                    RecipeType.SHAPED -> RecipeType.SHAPELESS
                    RecipeType.SHAPELESS -> RecipeType.SHAPED
                }
                e.inventory.setItem(toggleSlotIndex, toggleButton(session.recipeType))
            }

            onClick(lock = false) { e ->
                if (e.rawSlot >= e.view.topInventory.size) return@onClick
                val session = sessions[System.identityHashCode(e.inventory)] ?: return@onClick
                if (e.slot !in editableChars) {
                    e.isCancelled = true
                }
            }

            onBuild { _, inv ->
                sessions[System.identityHashCode(inv)] = EditorSession(player, recipeId, recipeType = initialType)

                existingRecipe?.materials?.forEach { ing ->
                    val slotIdx = ing.slot ?: return@forEach
                    if (slotIdx in matSlots.indices) {
                        val item = CustomCraftRecipeLoader.ingredientToItem(ing)
                        if (item != null) {
                            inv.setItem(matSlots[slotIdx], item)
                        }
                    }
                }
                existingRecipe?.let {
                    val resultItem = CustomCraftRecipeLoader.ingredientToItem(it.result)
                    if (resultItem != null) {
                        inv.setItem(resultSlotIndex, resultItem)
                    }
                }
            }

            onClose { event ->
                sessions.remove(System.identityHashCode(event.inventory))
                returnItems(event.player as Player, event.inventory)
            }
        }
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

        val resultItem = inv.getItem(resultSlotIndex)
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
        val key = CustomCraftService.recipeKey(session.recipeId)
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

    private fun returnItems(player: Player, inv: org.bukkit.inventory.Inventory) {
        val allSlots = matSlots.toList() + resultSlotIndex
        val items = allSlots.mapNotNull { slot ->
            val item = inv.getItem(slot)
            if (item != null && item.type != Material.AIR) item.clone() else null
        }
        if (items.isEmpty()) return
        val overflow = player.inventory.addItem(*items.toTypedArray())
        for (item in overflow.values) {
            player.world.dropItem(player.location, item)
        }
    }
}
