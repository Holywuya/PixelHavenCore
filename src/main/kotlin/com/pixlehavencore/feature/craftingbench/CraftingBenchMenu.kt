package com.pixlehavencore.feature.craftingbench

import com.pixlehavencore.util.ItemUtils
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import taboolib.module.chat.colored
import taboolib.platform.util.submit as submitOnEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CraftingBenchMenu {

    private const val PAGE_SIZE = 45
    private const val SLOT_DETAIL_RESULT = 13
    private const val SLOT_DETAIL_MATERIAL_START = 19
    private const val SLOT_DETAIL_MINUS = 47
    private const val SLOT_DETAIL_AMOUNT = 48
    private const val SLOT_DETAIL_CRAFT = 49
    private const val SLOT_DETAIL_PLUS = 50
    private const val SLOT_DETAIL_BACK = 53

    private val openViews = ConcurrentHashMap<UUID, CraftingBenchMenuHolder>()

    fun open(player: Player, tier: BenchTier, page: Int = 0) {
        val previews = CraftingBenchService.getAvailableRecipes(player, tier)
        val maxPage = if (previews.isEmpty()) 0 else (previews.size - 1) / PAGE_SIZE
        val currentPage = page.coerceIn(0, maxPage)
        val holder = CraftingBenchMenuHolder(
            tierId = tier.id,
            page = currentPage,
            mode = CraftingBenchMenuMode.LIST,
            recipeId = null,
            craftCount = 1,
        )
        val inventory = Bukkit.createInventory(holder, 54, Component.text("&8${tier.displayName} 制作台".colored()))
        holder.backingInventory = inventory
        renderInventory(inventory, player, holder, tier)
        player.openInventory(inventory)
        openViews[player.uniqueId] = holder
    }

    fun openRecipeDetail(player: Player, tier: BenchTier, page: Int, recipeId: String, craftCount: Int = 1) {
        val holder = CraftingBenchMenuHolder(
            tierId = tier.id,
            page = page,
            mode = CraftingBenchMenuMode.DETAIL,
            recipeId = recipeId,
            craftCount = craftCount.coerceAtLeast(1),
        )
        val inventory = Bukkit.createInventory(holder, 54, Component.text("&8${tier.displayName} 配方详情".colored()))
        holder.backingInventory = inventory
        renderInventory(inventory, player, holder, tier)
        player.openInventory(inventory)
        openViews[player.uniqueId] = holder
    }

    fun refreshOpenMenus() {
        openViews.entries.toList().forEach { (playerId, holder) ->
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

    private fun renderInventory(inventory: Inventory, player: Player, holder: CraftingBenchMenuHolder, tier: BenchTier) {
        inventory.clear()
        when (holder.mode) {
            CraftingBenchMenuMode.LIST -> renderListInventory(inventory, player, holder, tier)
            CraftingBenchMenuMode.DETAIL -> renderDetailInventory(inventory, player, holder, tier)
        }
    }

    private fun renderListInventory(inventory: Inventory, player: Player, holder: CraftingBenchMenuHolder, tier: BenchTier) {
        val previews = CraftingBenchService.getAvailableRecipes(player, tier)
        val maxPage = if (previews.isEmpty()) 0 else (previews.size - 1) / PAGE_SIZE
        val currentPage = holder.page.coerceIn(0, maxPage)
        val start = currentPage * PAGE_SIZE
        val end = (start + PAGE_SIZE).coerceAtMost(previews.size)
        previews.subList(start, end).forEachIndexed { index, preview ->
            inventory.setItem(index, createRecipeItem(preview))
        }

        val queue = CraftingBenchService.getPlayerTasks(player.uniqueId)
        queue.take(4).forEachIndexed { index, task ->
            inventory.setItem(45 + index, createQueueItem(task))
        }
        inventory.setItem(49, createInfoItem(player, tier, queue.size))
        if (currentPage > 0) {
            inventory.setItem(52, createNavItem(Material.ARROW, "&e上一页", currentPage - 1))
        }
        if (currentPage < maxPage) {
            inventory.setItem(53, createNavItem(Material.ARROW, "&e下一页", currentPage + 1))
        }
    }

    private fun renderDetailInventory(inventory: Inventory, player: Player, holder: CraftingBenchMenuHolder, tier: BenchTier) {
        val recipe = holder.recipeId?.let { CraftingBenchService.getRecipe(it) }
        if (recipe == null) {
            inventory.setItem(SLOT_DETAIL_BACK, createStaticItem(Material.BARRIER, "&c配方不存在", listOf("&7点击返回工作台主页")))
            return
        }
        val craftCount = holder.craftCount.coerceAtLeast(1)
        val statuses = CraftingBenchService.getRecipeMaterialStatuses(player, recipe, craftCount)
        val enoughMaterials = statuses.all { it.enough }
        val estimatedSeconds = CraftingBenchService.estimateCraftSeconds(player, tier, recipe, craftCount)

        inventory.setItem(SLOT_DETAIL_RESULT, createRecipeResultItem(recipe, craftCount, estimatedSeconds))
        statuses.forEachIndexed { index, status ->
            if (index >= 7) return@forEachIndexed
            inventory.setItem(SLOT_DETAIL_MATERIAL_START + index, createMaterialItem(status))
        }
        inventory.setItem(45, createInfoItem(player, tier, CraftingBenchService.getPlayerTasks(player.uniqueId).size))
        inventory.setItem(SLOT_DETAIL_MINUS, createStaticItem(Material.RED_STAINED_GLASS_PANE, "&c减少数量", listOf("&7每次减少 1")))
        inventory.setItem(SLOT_DETAIL_AMOUNT, createStaticItem(Material.PAPER, "&e制作数量: &f$craftCount", listOf("&7本次总产出: &f${recipe.results.sumOf { it.amount } * craftCount}")))
        inventory.setItem(SLOT_DETAIL_CRAFT, createCraftButton(enoughMaterials, craftCount, statuses.filter { it.warehouseWillUse > 0 }))
        inventory.setItem(SLOT_DETAIL_PLUS, createStaticItem(Material.LIME_STAINED_GLASS_PANE, "&a增加数量", listOf("&7每次增加 1")))
        inventory.setItem(SLOT_DETAIL_BACK, createStaticItem(Material.ARROW, "&e返回", listOf("&7返回工作台配方列表")))
    }

    private fun createRecipeItem(preview: RecipePreview): ItemStack {
        val firstResult = preview.recipe.results.firstOrNull()
        val base = firstResult?.let { ItemUtils.resolveSpec(it.item)?.clone() } ?: ItemStack(Material.CRAFTING_TABLE)
        base.amount = firstResult?.amount?.coerceAtLeast(1) ?: 1
        val meta = base.itemMeta ?: return base
        meta.displayName(Component.text("&a${preview.recipe.displayName}".colored()))
        meta.lore(listOf(
            Component.text("&7分类: &f${preview.recipe.category}".colored()),
            Component.text("&7耗时: &f${"%.1f".format(preview.estimatedSeconds)} 秒".colored()),
            Component.text("&7材料状态: ${(if (preview.enoughMaterials) "&a充足" else "&c不足")}".colored()),
            Component.text("&7点击查看配方详情".colored()),
        ))
        base.itemMeta = meta
        return base
    }

    private fun createRecipeResultItem(recipe: CraftingRecipe, craftCount: Int, estimatedSeconds: Double): ItemStack {
        val firstResult = recipe.results.firstOrNull()
        val base = firstResult?.let { ItemUtils.resolveSpec(it.item)?.clone() } ?: ItemStack(Material.CRAFTING_TABLE)
        base.amount = firstResult?.let { (it.amount * craftCount).coerceAtLeast(1) } ?: 1
        val meta = base.itemMeta ?: return base
        meta.displayName(Component.text("&6${recipe.displayName}".colored()))
        meta.lore(listOf(
            Component.text("&7分类: &f${recipe.category}".colored()),
            Component.text("&7工作台等级: &f${recipe.requiredBenchTier}".colored()),
            Component.text("&7制作数量: &f$craftCount".colored()),
            Component.text("&7耗时: &f${"%.1f".format(estimatedSeconds)} 秒".colored()),
        ))
        base.itemMeta = meta
        return base
    }

    private fun createMaterialItem(status: RecipeMaterialStatus): ItemStack {
        val base = ItemUtils.resolveSpec(status.material.item)?.clone() ?: ItemStack(Material.PAPER)
        base.amount = status.requiredAmount.coerceAtLeast(1)
        val meta = base.itemMeta ?: return base
        meta.displayName(Component.text(((if (status.enough) "&a" else "&c") + status.material.item).colored()))
        meta.lore(listOf(
            Component.text("&7需求: &f${status.requiredAmount}".colored()),
            Component.text("&7背包: &f${status.inventoryAmount}".colored()),
            Component.text("&7个人仓库: &f${status.warehouseAmount}".colored()),
            Component.text("&7本次从仓库提取: &f${status.warehouseWillUse}".colored()),
            Component.text("&7总计: ${(if (status.enough) "&a" else "&c") + status.totalAmount}".colored()),
        ))
        base.itemMeta = meta
        return base
    }

    private fun createCraftButton(canCraft: Boolean, craftCount: Int, warehouseMaterials: List<RecipeMaterialStatus>): ItemStack {
        val warehouseSummary = if (warehouseMaterials.isEmpty()) {
            listOf("&7本次不会从个人仓库提取材料")
        } else {
            warehouseMaterials.map { status -> "&7仓库提取 &f${status.material.item} x${status.warehouseWillUse}" }
        }
        return createStaticItem(
            material = if (canCraft) Material.LIME_STAINED_GLASS_PANE else Material.RED_STAINED_GLASS_PANE,
            title = if (canCraft) "&a开始制作" else "&c材料不足",
            lore = buildList {
                add(if (canCraft) "&7点击后制作 $craftCount 次" else "&7请补齐背包或个人仓库中的材料")
                addAll(warehouseSummary)
            }
        )
    }

    private fun createQueueItem(task: CraftingTask): ItemStack {
        return ItemStack(Material.CLOCK).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("&e队列任务 #${task.taskId}".colored()))
                lore(listOf(
                    Component.text("&7配方: &f${task.recipeId}".colored()),
                    Component.text("&7制作数量: &f${task.craftCount}".colored()),
                    Component.text("&7剩余: &f${task.remainingTicks / 20.0} 秒".colored())
                ))
            }
        }
    }

    private fun createInfoItem(player: Player, tier: BenchTier, queueSize: Int): ItemStack {
        val queueLimit = CraftingBenchSettings.resolveQueueLimit(player::hasPermission)
        return ItemStack(Material.BOOK).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("&6${tier.displayName}".colored()))
                lore(listOf(
                    Component.text("&7队列上限: &f${queueLimit}".colored()),
                    Component.text("&7当前队列: &f$queueSize".colored())
                ))
            }
        }
    }

    private fun createNavItem(material: Material, title: String, page: Int): ItemStack {
        return ItemStack(material).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text(title.colored()))
                lore(listOf(Component.text("&7目标页: &f$page".colored())))
            }
        }
    }

    private fun createStaticItem(material: Material, title: String, lore: List<String>): ItemStack {
        return ItemStack(material).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text(title.colored()))
                this.lore(lore.map { Component.text(it.colored()) })
            }
        }
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
) : InventoryHolder {
    lateinit var backingInventory: Inventory

    override fun getInventory(): Inventory {
        return backingInventory
    }
}
