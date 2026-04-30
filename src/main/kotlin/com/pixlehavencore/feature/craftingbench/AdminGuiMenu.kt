package com.pixlehavencore.feature.craftingbench

import com.pixlehavencore.util.ItemUtils
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import taboolib.module.chat.colored
import taboolib.module.ui.buildMenu
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object AdminGuiMenu {

    private const val PAGE_SIZE = 45
    private const val SIX_ROWS = 6
    private const val FOUR_ROWS = 4
    private const val THREE_ROWS = 3

    private val openAdminGuis = ConcurrentHashMap<UUID, AdminGuiHolder>()

    fun openRecipeList(player: Player, page: Int = 0) {
        val recipes = AdminGuiService.getAllRecipeIds()
        val maxPage = if (recipes.isEmpty()) 0 else (recipes.size - 1) / PAGE_SIZE
        val currentPage = page.coerceIn(0, maxPage)
        openPage(
            player = player,
            page = AdminGuiPage.RECIPE_LIST,
            title = "&8配方管理 - 第${currentPage + 1}页",
            menuRows = SIX_ROWS,
            context = mutableMapOf("page" to currentPage),
        ) { inv, _ ->
            renderRecipeList(inv, recipes, currentPage, maxPage)
        }
    }

    fun openRecipeEditor(player: Player, originalRecipeId: String? = null, session: RecipeEditSession? = null) {
        val editSession = session
            ?: AdminGuiService.getEditSession(player.uniqueId)
            ?: AdminGuiService.createEditSession(player.uniqueId, originalRecipeId)
        AdminGuiService.setEditSession(player.uniqueId, editSession)
        openPage(
            player = player,
            page = AdminGuiPage.RECIPE_EDITOR,
            title = "&8配方编辑",
            menuRows = FOUR_ROWS,
            context = mutableMapOf("originalRecipeId" to (editSession.originalRecipeId ?: "")),
        ) { inv, _ ->
            renderRecipeEditor(inv, editSession)
        }
    }

    // 材料列表 GUI：6 行 54 格，slot 0-44 可放入区域，slot 45-53 功能按钮
    fun openMaterialList(player: Player) {
        val session = AdminGuiService.getEditSession(player.uniqueId)
        openPage(player, AdminGuiPage.MATERIAL_LIST, "&8材料列表（直接放入物品）", SIX_ROWS) { inv, _ ->
            // 从 session.materials 读取并放入格子
            session?.materials?.forEachIndexed { index, mat ->
                if (index >= PAGE_SIZE) return@forEachIndexed
                val item = ItemUtils.resolveSpec(mat.item)?.clone() ?: ItemStack(Material.PAPER)
                item.amount = mat.amount.coerceAtLeast(1).coerceAtMost(64)
                inv.setItem(index, item)
            }
            // 底部功能区
            inv.setItem(45, createStaticItem(Material.ARROW, "&e返回", listOf("&7返回配方编辑")))
            fillBorderRange(inv, 46..53)
        }
    }

    // 奖励物品 GUI：3 行 27 格，slot 0-17 可放入区域，slot 18-26 功能按钮
    fun openRewardItems(player: Player) {
        val session = AdminGuiService.getEditSession(player.uniqueId)
        openPage(player, AdminGuiPage.REWARD_ITEMS, "&8奖励物品（直接放入物品）", THREE_ROWS) { inv, _ ->
            // 从 session.results 读取并放入格子
            session?.results?.forEachIndexed { index, result ->
                if (index >= 18) return@forEachIndexed
                val item = ItemUtils.resolveSpec(result.item)?.clone() ?: ItemStack(Material.PAPER)
                item.amount = result.amount.coerceAtLeast(1).coerceAtMost(64)
                inv.setItem(index, item)
            }
            // 底部功能区
            inv.setItem(18, createStaticItem(Material.ARROW, "&e返回", listOf("&7返回配方编辑")))
            fillBorderRange(inv, 19..26)
        }
    }

    fun openDeleteConfirm(player: Player, target: DeleteTarget) {
        AdminGuiService.setDeleteTarget(player.uniqueId, target)
        openPage(player, AdminGuiPage.DELETE_CONFIRM, "&c确认删除", THREE_ROWS) { inv, _ ->
            renderDeleteConfirm(inv, target)
        }
    }

    fun unregister(playerId: UUID) {
        openAdminGuis.remove(playerId)
    }

    fun isOpen(playerId: UUID): Boolean {
        return openAdminGuis.containsKey(playerId)
    }

    fun isAdminGuiInventory(playerId: UUID, inventory: Inventory): Boolean {
        return openAdminGuis[playerId]?.backingInventory === inventory
    }

    fun getOpenHolder(playerId: UUID, inventory: Inventory): AdminGuiHolder? {
        val holder = openAdminGuis[playerId] ?: return null
        return holder.takeIf { it.backingInventory === inventory }
    }

    private fun openPage(
        player: Player,
        page: AdminGuiPage,
        title: String,
        menuRows: Int,
        context: MutableMap<String, Any> = mutableMapOf(),
        renderer: (Inventory, AdminGuiHolder) -> Unit,
    ) {
        val holder = AdminGuiHolder(
            ownerId = player.uniqueId,
            page = page,
            context = context,
        )
        val inv = buildMenu<Chest>(title.colored()) {
            rows(menuRows)
        }
        holder.backingInventory = inv
        renderer(inv, holder)
        openAdminGuis[player.uniqueId] = holder
        player.openMenu(inv)
    }

    private fun fillBorder(inv: Inventory, size: Int) {
        val glass = createStaticItem(Material.GRAY_STAINED_GLASS_PANE, "&7", emptyList())
        for (i in 0 until size) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, glass)
            }
        }
    }

    // 仅填充指定范围的空格子
    private fun fillBorderRange(inv: Inventory, range: IntRange) {
        val glass = createStaticItem(Material.GRAY_STAINED_GLASS_PANE, "&7", emptyList())
        for (i in range) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, glass)
            }
        }
    }

    private fun renderRecipeList(inv: Inventory, recipes: List<String>, page: Int, maxPage: Int) {
        val start = page * PAGE_SIZE
        val end = (start + PAGE_SIZE).coerceAtMost(recipes.size)
        for (i in start until end) {
            val recipeId = recipes[i]
            val recipe = CraftingBenchService.getRecipe(recipeId)
            val display = recipe?.displayName ?: recipeId
            val lore = listOf(
                "&7ID: &f$recipeId",
                "&7分类: &f${recipe?.category ?: "-"}",
                "&7左键: &e编辑",
                "&7右键: &e复制",
                "&7Shift+右键: &c删除",
            )
            inv.setItem(i - start, createStaticItem(Material.PAPER, "&a$display", lore))
        }
        if (page > 0) {
            inv.setItem(45, createStaticItem(Material.ARROW, "&e上一页", emptyList()))
        }
        inv.setItem(48, createStaticItem(Material.LIME_STAINED_GLASS_PANE, "&a新建配方", listOf("&7点击创建新配方")))
        if (page < maxPage) {
            inv.setItem(53, createStaticItem(Material.ARROW, "&e下一页", emptyList()))
        }
        fillBorder(inv, 54)
    }

    private fun renderRecipeEditor(inv: Inventory, session: RecipeEditSession) {
        inv.setItem(0, createStaticItem(Material.PAPER, "&eID: &f${session.id}", listOf("&7点击修改")))
        inv.setItem(1, createStaticItem(Material.NAME_TAG, "&e名称: &f${session.displayName}", listOf("&7点击修改")))
        inv.setItem(2, createStaticItem(Material.BOOK, "&e分类: &f${session.category}", listOf("&7点击修改")))
        inv.setItem(3, createStaticItem(Material.ANVIL, "&e所需等级: &f${session.requiredBenchTier}", listOf("&7点击修改")))
        inv.setItem(4, createStaticItem(Material.CHEST, "&e材料列表 &f(${session.materials.size}个)", listOf("&7点击编辑材料")))
        inv.setItem(5, createStaticItem(Material.DIAMOND, "&e奖励物品 &f(${session.results.size}个)", listOf("&7点击编辑奖励物品")))
        inv.setItem(6, createStaticItem(Material.CLOCK, "&e制作时间: &f${session.craftTimeSeconds}s", listOf("&7点击修改")))
        inv.setItem(7, createStaticItem(Material.EXPERIENCE_BOTTLE, "&e经验: &f${session.experience}", listOf("&7点击修改")))
        inv.setItem(8, createStaticItem(
            if (session.unlockPermission.isBlank()) Material.RED_STAINED_GLASS_PANE else Material.LIME_STAINED_GLASS_PANE,
            "&e解锁权限: ${if (session.unlockPermission.isBlank()) "&c关闭" else "&a开启"}",
            listOf(
                if (session.unlockPermission.isBlank()) "&7当前: 无需权限，所有人可用"
                else "&7当前: &f${session.unlockPermission}",
                "&7点击切换开关",
                "&7开启后权限默认为: &fcraft.recipe.${session.id.ifBlank { "<配方ID>" }}"
            )
        ))
        inv.setItem(27, createStaticItem(Material.ARROW, "&e返回", emptyList()))
        inv.setItem(35, createStaticItem(Material.RED_STAINED_GLASS_PANE, "&c删除", listOf("&7删除此配方")))
        fillBorder(inv, 36)
    }

    private fun renderDeleteConfirm(inv: Inventory, target: DeleteTarget) {
        val name = when (target) {
            is DeleteTarget.Recipe -> "配方 '${target.recipeId}'"
        }
        inv.setItem(13, createStaticItem(Material.BARRIER, "&c确定删除 $name？", listOf("&7此操作不可撤销")))
        inv.setItem(23, createStaticItem(Material.RED_STAINED_GLASS_PANE, "&c确认删除", emptyList()))
        inv.setItem(26, createStaticItem(Material.GRAY_STAINED_GLASS_PANE, "&7取消", emptyList()))
        fillBorder(inv, 27)
    }

    fun createStaticItem(material: Material, title: String, lore: List<String>): ItemStack {
        return ItemStack(material).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text(title.colored()))
                this.lore(lore.map { Component.text(it.colored()) })
            }
        }
    }
}
