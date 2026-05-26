package com.pixlehavencore.feature.craftingbench

import com.pixlehavencore.util.ItemUtils
import com.pixlehavencore.util.TextUtils
import com.pixlehavencore.bridge.TextBridge
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import taboolib.platform.util.submit as submitOnEntity
import taboolib.platform.util.PlayerSessionMap
import java.util.UUID

object CraftingBenchMenu {

    private const val SLOT_DETAIL_RESULT = 22
    private const val SLOT_DETAIL_MATERIAL_START = 10
    internal const val SLOT_DETAIL_MINUS = 31
    internal const val SLOT_DETAIL_AMOUNT = 32
    internal const val SLOT_DETAIL_CRAFT = 34
    internal const val SLOT_DETAIL_PLUS = 33
    internal const val SLOT_DETAIL_BACK = 53

    private val openViews = PlayerSessionMap<CraftingBenchMenuHolder>({ throw IllegalStateException() })

    fun open(player: Player, tier: BenchTier, category: String? = null, page: Int = 0) {
        val allPreviews = CraftingBenchService.getAvailableRecipes(player, tier)
        val filtered = if (category != null) allPreviews.filter { it.recipe.category == category } else allPreviews
        val pageSize = CraftingBenchSettings.guiPageSize
        val maxPage = if (filtered.isEmpty()) 0 else (filtered.size - 1) / pageSize
        val currentPage = page.coerceIn(0, maxPage)
        val holder = CraftingBenchMenuHolder(
            tierId = tier.id,
            page = currentPage,
            mode = CraftingBenchMenuMode.LIST,
            recipeId = null,
            craftCount = 1,
            category = category,
        )
        val inventory = Bukkit.createInventory(holder, 54, TextUtils.parse("&8${tier.displayName} 制作台"))
        holder.backingInventory = inventory
        renderInventory(inventory, player, holder, tier)
        player.openInventory(inventory)
        openViews[player.uniqueId] = holder
    }

    fun openRecipeDetail(player: Player, tier: BenchTier, category: String?, page: Int, recipeId: String, craftCount: Int = 1) {
        val holder = CraftingBenchMenuHolder(
            tierId = tier.id,
            page = page,
            mode = CraftingBenchMenuMode.DETAIL,
            recipeId = recipeId,
            craftCount = craftCount.coerceAtLeast(1),
            category = category,
        )
        val inventory = Bukkit.createInventory(holder, 54, TextUtils.parse("&8${tier.displayName} 配方详情"))
        holder.backingInventory = inventory
        renderInventory(inventory, player, holder, tier)
        player.openInventory(inventory)
        openViews[player.uniqueId] = holder
    }

    fun refreshOpenMenus() {
        openViews.entries().toList().forEach { (playerId, holder) ->
            val player = Bukkit.getPlayer(playerId) ?: run {
                openViews.remove(playerId)
                return@forEach
            }
            player.submitOnEntity {
                val topInventory = player.openInventory.topInventory
                if (topInventory.holder !== holder) {
                    openViews.remove(playerId)
                    return@submitOnEntity
                }
                val tier = CraftingBenchSettings.getTier(holder.tierId) ?: run {
                    openViews.remove(playerId)
                    return@submitOnEntity
                }
                renderInventory(holder.backingInventory, player, holder, tier)
                player.updateInventory()
            }
        }
    }

    fun unregister(playerId: UUID) {
        openViews.remove(playerId)
    }

    private fun drawBorder(inventory: Inventory, accentRows: Set<Int>, sideRows: IntRange) {
        val accentItem = ItemStack(CraftingBenchSettings.guiBorderAccent)
        val sideItem = ItemStack(CraftingBenchSettings.guiBorderItem)
        for (slot in 0 until inventory.size) {
            val row = slot / 9
            val col = slot % 9
            when {
                row in accentRows -> inventory.setItem(slot, accentItem)
                row in sideRows && (col == 0 || col == 8) -> inventory.setItem(slot, sideItem)
            }
        }
    }

    private fun renderCategoryGrid(inventory: Inventory, selectedCategory: String?, categories: List<String>) {
        val slots = CraftingBenchSettings.guiCategorySlots
        if (slots.isEmpty()) return
        val allSelected = selectedCategory == null
        inventory.setItem(slots[0], ItemUtils.namedItem(
            if (allSelected) Material.LIME_STAINED_GLASS_PANE else Material.WHITE_STAINED_GLASS_PANE,
            if (allSelected) "&a全部" else "&7全部"
        ))
        categories.forEachIndexed { index, cat ->
            val slotIndex = index + 1
            if (slotIndex < slots.size) {
                val selected = selectedCategory == cat
                inventory.setItem(slots[slotIndex], ItemUtils.namedItem(
                    if (selected) Material.LIME_STAINED_GLASS_PANE else Material.WHITE_STAINED_GLASS_PANE,
                    if (selected) "&a$cat" else "&7$cat"
                ))
            }
        }
    }

    private fun renderInventory(inventory: Inventory, player: Player, holder: CraftingBenchMenuHolder, tier: BenchTier) {
        inventory.clear()
        when (holder.mode) {
            CraftingBenchMenuMode.LIST -> renderListInventory(inventory, player, holder, tier)
            CraftingBenchMenuMode.DETAIL -> renderDetailInventory(inventory, player, holder, tier)
        }
    }

    private fun renderListInventory(inventory: Inventory, player: Player, holder: CraftingBenchMenuHolder, tier: BenchTier) {
        val allPreviews = CraftingBenchService.getAvailableRecipes(player, tier)
        val categories = allPreviews.map { it.recipe.category }.distinct().sorted()
        val filtered = if (holder.category != null) allPreviews.filter { it.recipe.category == holder.category } else allPreviews
        val pageSize = CraftingBenchSettings.guiPageSize
        val maxPage = if (filtered.isEmpty()) 0 else (filtered.size - 1) / pageSize
        val currentPage = holder.page.coerceIn(0, maxPage)

        drawBorder(inventory, setOf(0, 5), 1..4)
        renderCategoryGrid(inventory, holder.category, categories)

        val start = currentPage * pageSize
        val end = (start + pageSize).coerceAtMost(filtered.size)
        val recipeStartSlot = CraftingBenchSettings.guiRecipeStartSlot
        filtered.subList(start, end).forEachIndexed { index, preview ->
            inventory.setItem(recipeStartSlot + index, createRecipeItem(preview))
        }

        val queue = CraftingBenchService.getPlayerTasks(player.uniqueId)
        val queueStart = CraftingBenchSettings.guiQueueStartSlot
        val queueMax = CraftingBenchSettings.guiQueueMax
        queue.take(queueMax).forEachIndexed { index, task ->
            inventory.setItem(queueStart + index, createQueueItem(task))
        }
        inventory.setItem(CraftingBenchSettings.guiInfoSlot, createInfoItem(player, tier, queue.size))

        if (currentPage > 0) {
            inventory.setItem(CraftingBenchSettings.guiPrevPageSlot, ItemUtils.namedItem(Material.ARROW, "&e上一页"))
        }
        if (currentPage < maxPage) {
            inventory.setItem(CraftingBenchSettings.guiNextPageSlot, ItemUtils.namedItem(Material.ARROW, "&e下一页"))
        }
    }

    private fun renderDetailInventory(inventory: Inventory, player: Player, holder: CraftingBenchMenuHolder, tier: BenchTier) {
        val recipe = holder.recipeId?.let { CraftingBenchService.getRecipe(it) }
        if (recipe == null) {
            inventory.setItem(SLOT_DETAIL_BACK, ItemUtils.staticItem(Material.BARRIER, "&c配方不存在", listOf("&7点击返回工作台主页")))
            return
        }
        val craftCount = holder.craftCount.coerceAtLeast(1)
        val statuses = CraftingBenchService.getRecipeMaterialStatuses(player, recipe, craftCount)
        val enoughMaterials = statuses.all { it.enough }
        val estimatedSeconds = CraftingBenchService.estimateCraftSeconds(player, tier, recipe, craftCount)

        drawBorder(inventory, setOf(0, 3, 5), 1..2)

        inventory.setItem(SLOT_DETAIL_RESULT, createRecipeResultItem(recipe, craftCount, estimatedSeconds))
        statuses.forEachIndexed { index, status ->
            if (index >= 7) return@forEachIndexed
            inventory.setItem(SLOT_DETAIL_MATERIAL_START + index, createMaterialItem(status))
        }
        inventory.setItem(CraftingBenchSettings.guiInfoSlot, createInfoItem(player, tier, CraftingBenchService.getPlayerTasks(player.uniqueId).size))
        inventory.setItem(SLOT_DETAIL_MINUS, ItemUtils.staticItem(Material.RED_STAINED_GLASS_PANE, "&c减少数量", listOf("&7每次减少 1")))
        inventory.setItem(SLOT_DETAIL_AMOUNT, ItemUtils.staticItem(Material.PAPER, "&e制作数量: &f$craftCount", listOf("&7本次总产出: &f${recipe.results.sumOf { it.amount } * craftCount}")))
        inventory.setItem(SLOT_DETAIL_CRAFT, createCraftButton(enoughMaterials, craftCount, statuses.filter { it.warehouseWillUse > 0 }))
        inventory.setItem(SLOT_DETAIL_PLUS, ItemUtils.staticItem(Material.LIME_STAINED_GLASS_PANE, "&a增加数量", listOf("&7每次增加 1")))
        inventory.setItem(SLOT_DETAIL_BACK, ItemUtils.staticItem(Material.ARROW, "&e返回", listOf("&7返回工作台配方列表")))
    }

    private fun createRecipeItem(preview: RecipePreview): ItemStack {
        val firstResult = preview.recipe.results.firstOrNull()
        val base = firstResult?.let { ItemUtils.resolveSpec(it.item)?.clone() } ?: ItemStack(Material.CRAFTING_TABLE)
        base.amount = firstResult?.amount?.coerceAtLeast(1) ?: 1
        TextBridge.setDisplayName(base, TextUtils.parseItem("&a${preview.recipe.displayName}"))
        TextBridge.setLore(base, listOf(
            TextUtils.parseItem("&8&m─────────────────────"),
            TextUtils.parseItem("&7分类: &f${preview.recipe.category}"),
            TextUtils.parseItem("&7耗时: &f${"%.1f".format(preview.estimatedSeconds)} 秒"),
            TextUtils.parseItem("&7材料状态: ${(if (preview.enoughMaterials) "&a充足" else "&c不足")}"),
            TextUtils.parseItem(""),
            TextUtils.parseItem("&7点击查看详情"),
        ))
        return base
    }

    private fun createRecipeResultItem(recipe: CraftingRecipe, craftCount: Int, estimatedSeconds: Double): ItemStack {
        val firstResult = recipe.results.firstOrNull()
        val base = firstResult?.let { ItemUtils.resolveSpec(it.item)?.clone() } ?: ItemStack(Material.CRAFTING_TABLE)
        base.amount = firstResult?.let { (it.amount * craftCount).coerceAtLeast(1) } ?: 1
        TextBridge.setDisplayName(base, TextUtils.parseItem("&6${recipe.displayName}"))
        TextBridge.setLore(base, listOf(
            TextUtils.parseItem("&8&m─────────────────────"),
            TextUtils.parseItem("&7分类: &f${recipe.category}"),
            TextUtils.parseItem("&7工作台等级: &f${recipe.requiredBenchTier}"),
            TextUtils.parseItem("&7制作数量: &f$craftCount"),
            TextUtils.parseItem("&7耗时: &f${"%.1f".format(estimatedSeconds)} 秒"),
        ))
        return base
    }

    private fun createMaterialItem(status: RecipeMaterialStatus): ItemStack {
        val base = ItemUtils.resolveSpec(status.material.item)?.clone() ?: ItemStack(Material.PAPER)
        base.amount = status.requiredAmount.coerceAtLeast(1)
        TextBridge.setDisplayName(base, TextUtils.parseItem("${if (status.enough) "&a" else "&c"}${status.material.item}"))
        TextBridge.setLore(base, listOf(
            TextUtils.parseItem("&7需求: &f${status.requiredAmount}"),
            TextUtils.parseItem("&7背包: &f${status.inventoryAmount}"),
            TextUtils.parseItem("&7个人仓库: &f${status.warehouseAmount}"),
            TextUtils.parseItem("&7本次从仓库提取: &f${status.warehouseWillUse}"),
            TextUtils.parseItem("&7总计: ${if (status.enough) "&a" else "&c"}${status.totalAmount}"),
        ))
        return base
    }

    private fun createCraftButton(canCraft: Boolean, craftCount: Int, warehouseMaterials: List<RecipeMaterialStatus>): ItemStack {
        val warehouseSummary = if (warehouseMaterials.isEmpty()) {
            listOf("&7本次不会从个人仓库提取材料")
        } else {
            warehouseMaterials.map { status -> "&7仓库提取 &f${status.material.item} x${status.warehouseWillUse}" }
        }
        return ItemUtils.staticItem(
            material = if (canCraft) Material.LIME_STAINED_GLASS_PANE else Material.RED_STAINED_GLASS_PANE,
            title = if (canCraft) "&a开始制作" else "&c材料不足",
            lore = buildList {
                add(if (canCraft) "&7点击后制作 $craftCount 次" else "&7请补齐背包或个人仓库中的材料")
                addAll(warehouseSummary)
            }
        )
    }

    private fun createQueueItem(task: CraftingTask): ItemStack {
        val item = ItemStack(Material.CLOCK)
        TextBridge.setDisplayName(item, TextUtils.parseItem("&e队列任务 #${task.taskId}"))
        TextBridge.setLore(item, listOf(
            TextUtils.parseItem("&7配方: &f${task.recipeId}"),
            TextUtils.parseItem("&7制作数量: &f${task.craftCount}"),
            TextUtils.parseItem("&7剩余: &f${task.remainingTicks / 20.0} 秒"),
        ))
        return item
    }

    private fun createInfoItem(player: Player, tier: BenchTier, queueSize: Int): ItemStack {
        val queueLimit = CraftingBenchSettings.resolveQueueLimit(player::hasPermission)
        val item = ItemStack(Material.BOOK)
        TextBridge.setDisplayName(item, TextUtils.parseItem("&6${tier.displayName}"))
        TextBridge.setLore(item, listOf(
            TextUtils.parseItem("&7队列上限: &f${queueLimit}"),
            TextUtils.parseItem("&7当前队列: &f$queueSize"),
        ))
        return item
    }

}

enum class CraftingBenchMenuMode {
    LIST,
    DETAIL,
}

class CraftingBenchMenuHolder(
    val tierId: String,
    val page: Int,
    val mode: CraftingBenchMenuMode,
    val recipeId: String?,
    val craftCount: Int,
    val category: String? = null,
) : InventoryHolder {
    lateinit var backingInventory: Inventory

    override fun getInventory(): Inventory {
        return backingInventory
    }
}
