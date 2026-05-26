package com.pixlehavencore.feature.craftingbench

import com.pixlehavencore.util.ItemUtils
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import taboolib.module.chat.colored
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.platform.util.PlayerSessionMap
import java.util.UUID

object AdminGuiMenu {

    private const val PAGE_SIZE = 45

    private val openAdminGuis = PlayerSessionMap<AdminGuiHolder>({ throw IllegalStateException() })

    fun openRecipeList(player: Player, page: Int = 0) {
        val recipes = AdminGuiService.getAllRecipeIds()
        val maxPage = if (recipes.isEmpty()) 0 else (recipes.size - 1) / PAGE_SIZE
        val currentPage = page.coerceIn(0, maxPage)
        val holder = AdminGuiHolder(
            ownerId = player.uniqueId,
            page = AdminGuiPage.RECIPE_LIST,
            context = mutableMapOf("page" to currentPage),
        )
        player.openMenu<Chest>("&8配方管理 - 第${currentPage + 1}页".colored()) {
            map(
                ".........",
                ".........",
                ".........",
                ".........",
                ".........",
                "#########"
            )
            set('#', ItemStack(Material.BLACK_STAINED_GLASS_PANE))
            onBuild { _, inv ->
                holder.backingInventory = inv
                openAdminGuis[player.uniqueId] = holder
                val start = currentPage * PAGE_SIZE
                val end = (start + PAGE_SIZE).coerceAtMost(recipes.size)
                for (i in start until end) {
                    val recipeId = recipes[i]
                    val recipe = CraftingBenchService.getRecipe(recipeId)
                    val display = recipe?.displayName ?: recipeId
                    inv.setItem(i - start, ItemUtils.staticItem(Material.PAPER, "&a$display", listOf(
                        "&7ID: &f$recipeId",
                        "&7分类: &f${recipe?.category ?: "-"}",
                        "&7左键: &e编辑",
                        "&7右键: &e复制",
                        "&7Shift+右键: &c删除",
                    )))
                }
                if (currentPage > 0) {
                    inv.setItem(45, ItemUtils.staticItem(Material.ARROW, "&e上一页", emptyList()))
                }
                inv.setItem(48, ItemUtils.staticItem(Material.LIME_STAINED_GLASS_PANE, "&a新建配方", listOf("&7点击创建新配方")))
                if (currentPage < maxPage) {
                    inv.setItem(53, ItemUtils.staticItem(Material.ARROW, "&e下一页", emptyList()))
                }
            }
        }
    }

    fun openRecipeEditor(player: Player, originalRecipeId: String? = null, session: RecipeEditSession? = null) {
        val editSession = session
            ?: AdminGuiService.getEditSession(player.uniqueId)
            ?: AdminGuiService.createEditSession(player.uniqueId, originalRecipeId)
        AdminGuiService.setEditSession(player.uniqueId, editSession)
        val holder = AdminGuiHolder(
            ownerId = player.uniqueId,
            page = AdminGuiPage.RECIPE_EDITOR,
            context = mutableMapOf("originalRecipeId" to (editSession.originalRecipeId ?: "")),
        )
        player.openMenu<Chest>("&8配方编辑".colored()) {
            map(
                "@@@@@@@@@",
                "|       |",
                "|       |",
                "#########"
            )
            set('#', ItemStack(Material.BLACK_STAINED_GLASS_PANE))
            set('|', ItemStack(Material.GRAY_STAINED_GLASS_PANE))
            onBuild { _, inv ->
                holder.backingInventory = inv
                openAdminGuis[player.uniqueId] = holder
                renderRecipeEditor(inv, editSession)
            }
        }
    }

    fun openMaterialList(player: Player) {
        val session = AdminGuiService.getEditSession(player.uniqueId)
        val holder = AdminGuiHolder(
            ownerId = player.uniqueId,
            page = AdminGuiPage.MATERIAL_LIST,
        )
        player.openMenu<Chest>("&8材料列表（直接放入物品）".colored()) {
            handLocked(false)
            map(
                ".........",
                ".........",
                ".........",
                ".........",
                ".........",
                "#B########"
            )
            set('#', ItemStack(Material.BLACK_STAINED_GLASS_PANE))
            onBuild { _, inv ->
                holder.backingInventory = inv
                openAdminGuis[player.uniqueId] = holder
                session?.materials?.forEachIndexed { index, mat ->
                    if (index >= PAGE_SIZE) return@forEachIndexed
                    val item = ItemUtils.resolveSpec(mat.item)?.clone() ?: ItemStack(Material.PAPER)
                    item.amount = mat.amount.coerceAtLeast(1).coerceAtMost(64)
                    inv.setItem(index, item)
                }
                inv.setItem(45, ItemUtils.staticItem(Material.ARROW, "&e返回", listOf("&7返回配方编辑")))
            }
        }
    }

    fun openRewardItems(player: Player) {
        val session = AdminGuiService.getEditSession(player.uniqueId)
        val holder = AdminGuiHolder(
            ownerId = player.uniqueId,
            page = AdminGuiPage.REWARD_ITEMS,
        )
        player.openMenu<Chest>("&8奖励物品（直接放入物品）".colored()) {
            handLocked(false)
            map(
                ".........",
                ".........",
                "#B########"
            )
            set('#', ItemStack(Material.BLACK_STAINED_GLASS_PANE))
            onBuild { _, inv ->
                holder.backingInventory = inv
                openAdminGuis[player.uniqueId] = holder
                session?.results?.forEachIndexed { index, result ->
                    if (index >= 18) return@forEachIndexed
                    val item = ItemUtils.resolveSpec(result.item)?.clone() ?: ItemStack(Material.PAPER)
                    item.amount = result.amount.coerceAtLeast(1).coerceAtMost(64)
                    inv.setItem(index, item)
                }
                inv.setItem(18, ItemUtils.staticItem(Material.ARROW, "&e返回", listOf("&7返回配方编辑")))
            }
        }
    }

    fun openDeleteConfirm(player: Player, target: DeleteTarget) {
        AdminGuiService.setDeleteTarget(player.uniqueId, target)
        val holder = AdminGuiHolder(
            ownerId = player.uniqueId,
            page = AdminGuiPage.DELETE_CONFIRM,
        )
        player.openMenu<Chest>("&c确认删除".colored()) {
            map(
                "#########",
                "|       |",
                "#########"
            )
            set('#', ItemStack(Material.BLACK_STAINED_GLASS_PANE))
            set('|', ItemStack(Material.GRAY_STAINED_GLASS_PANE))
            onBuild { _, inv ->
                holder.backingInventory = inv
                openAdminGuis[player.uniqueId] = holder
                renderDeleteConfirm(inv, target)
            }
        }
    }

    fun unregister(playerId: UUID) {
        openAdminGuis.remove(playerId)
    }

    fun isOpen(playerId: UUID): Boolean {
        return openAdminGuis[playerId] != null
    }

    fun isAdminGuiInventory(playerId: UUID, inventory: Inventory): Boolean {
        return openAdminGuis[playerId]?.backingInventory === inventory
    }

    fun getOpenHolder(playerId: UUID, inventory: Inventory): AdminGuiHolder? {
        val holder = openAdminGuis[playerId] ?: return null
        return holder.takeIf { it.backingInventory === inventory }
    }

    private fun renderRecipeEditor(inv: Inventory, session: RecipeEditSession) {
        inv.setItem(0, ItemUtils.staticItem(Material.PAPER, "&eID: &f${session.id}", listOf("&7点击修改")))
        inv.setItem(1, ItemUtils.staticItem(Material.NAME_TAG, "&e名称: &f${session.displayName}", listOf("&7点击修改")))
        inv.setItem(2, ItemUtils.staticItem(Material.BOOK, "&e分类: &f${session.category}", listOf("&7点击修改")))
        inv.setItem(3, ItemUtils.staticItem(Material.ANVIL, "&e所需等级: &f${session.requiredBenchTier}", listOf("&7点击修改")))
        inv.setItem(4, ItemUtils.staticItem(Material.CHEST, "&e材料列表 &f(${session.materials.size}个)", listOf("&7点击编辑材料")))
        inv.setItem(5, ItemUtils.staticItem(Material.DIAMOND, "&e奖励物品 &f(${session.results.size}个)", listOf("&7点击编辑奖励物品")))
        inv.setItem(6, ItemUtils.staticItem(Material.CLOCK, "&e制作时间: &f${session.craftTimeSeconds}s", listOf("&7点击修改")))
        inv.setItem(7, ItemUtils.staticItem(Material.EXPERIENCE_BOTTLE, "&e经验: &f${session.experience}", listOf("&7点击修改")))
        inv.setItem(8, ItemUtils.staticItem(
            if (session.unlockPermission.isBlank()) Material.RED_STAINED_GLASS_PANE else Material.LIME_STAINED_GLASS_PANE,
            "&e解锁权限: ${if (session.unlockPermission.isBlank()) "&c关闭" else "&a开启"}",
            listOf(
                if (session.unlockPermission.isBlank()) "&7当前: 无需权限，所有人可用"
                else "&7当前: &f${session.unlockPermission}",
                "&7点击切换开关",
                "&7开启后权限默认为: &fcraft.recipe.${session.id.ifBlank { "<配方ID>" }}"
            )
        ))
        inv.setItem(27, ItemUtils.staticItem(Material.ARROW, "&e返回", emptyList()))
        inv.setItem(35, ItemUtils.staticItem(Material.RED_STAINED_GLASS_PANE, "&c删除", listOf("&7删除此配方")))
    }

    private fun renderDeleteConfirm(inv: Inventory, target: DeleteTarget) {
        val name = when (target) {
            is DeleteTarget.Recipe -> "配方 '${target.recipeId}'"
        }
        inv.setItem(13, ItemUtils.staticItem(Material.BARRIER, "&c确定删除 $name？", listOf("&7此操作不可撤销")))
        inv.setItem(23, ItemUtils.staticItem(Material.RED_STAINED_GLASS_PANE, "&c确认删除", emptyList()))
        inv.setItem(26, ItemUtils.staticItem(Material.GRAY_STAINED_GLASS_PANE, "&7取消", emptyList()))
}
    }
