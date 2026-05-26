package com.pixlehavencore.feature.craftingbench

import com.pixlehavencore.bridge.TextBridge
import io.papermc.paper.event.player.AsyncChatEvent
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit

object AdminGuiListener {

    @SubscribeEvent
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val holder = AdminGuiMenu.getOpenHolder(player.uniqueId, event.view.topInventory) ?: return
        if (!AdminGuiService.hasAdminPermission(player)) {
            event.isCancelled = true
            player.sendMessage("§c你没有管理权限。")
            return
        }
        when (holder.page) {
            AdminGuiPage.RECIPE_LIST -> handleRecipeListClick(event, player, holder)
            AdminGuiPage.RECIPE_EDITOR -> handleRecipeEditorClick(event, player)
            AdminGuiPage.MATERIAL_LIST -> handleMaterialListClick(event, player)
            AdminGuiPage.REWARD_ITEMS -> handleRewardItemsClick(event, player)
            AdminGuiPage.DELETE_CONFIRM -> handleDeleteConfirmClick(event, player)
        }
    }

    @SubscribeEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val holder = AdminGuiMenu.getOpenHolder(player.uniqueId, event.inventory) ?: return
        when (holder.page) {
            AdminGuiPage.MATERIAL_LIST -> handleMaterialListClose(player, event.inventory)
            AdminGuiPage.REWARD_ITEMS -> handleRewardItemsClose(player, event.inventory)
            else -> {}
        }
        AdminGuiMenu.unregister(player.uniqueId)

        if (holder.page == AdminGuiPage.RECIPE_EDITOR) {
            // 自动保存：编辑器关闭时保存配方
            val session = AdminGuiService.getEditSession(player.uniqueId)
            if (session != null && session.id.isNotBlank()) {
                AdminGuiService.autoSaveRecipe(session)
            }
            // 回退机制：如果关闭编辑器时有 pending 聊天输入（如配方 ID 编辑），
            // 确保输入完成后 GUI 能重新打开（防止 AsyncChatEvent 中的 submit 调度失效）
            if (AdminGuiService.hasPendingInput(player.uniqueId)) {
                submit {
                    val p = org.bukkit.Bukkit.getPlayer(player.uniqueId)
                    if (p != null && p.isOnline && !AdminGuiService.hasPendingInput(player.uniqueId) && AdminGuiService.getEditSession(player.uniqueId) != null) {
                        AdminGuiMenu.openRecipeEditor(p)
                    }
                }
            }
        }
    }

    @SubscribeEvent
    fun onPlayerQuit(event: PlayerQuitEvent) {
        AdminGuiMenu.unregister(event.player.uniqueId)
        AdminGuiService.cleanupPlayer(event.player.uniqueId)
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onChatInput(event: AsyncChatEvent) {
        val player = event.player
        if (!AdminGuiService.hasPendingInput(player.uniqueId)) return
        event.isCancelled = true
        val message = TextBridge.toPlain(event.message())
        if (message.equals("cancel", ignoreCase = true)) {
            AdminGuiService.removePendingInput(player.uniqueId)
            player.sendMessage("§7已取消输入。")
            return
        }
        AdminGuiService.handleChatInput(player, message)
    }

    private fun handleRecipeListClick(event: InventoryClickEvent, player: Player, holder: AdminGuiHolder) {
        event.isCancelled = true
        val page = holder.context["page"] as? Int ?: 0
        val slot = event.rawSlot
        if (slot in 0 until 45) {
            val recipes = getRecipeIdsForPage(page)
            val recipeId = recipes.getOrNull(slot) ?: return
            when {
                event.isRightClick && event.isShiftClick -> AdminGuiMenu.openDeleteConfirm(player, DeleteTarget.Recipe(recipeId))
                event.isRightClick -> {
                    val session = AdminGuiService.copyRecipeSession(recipeId) ?: return
                    AdminGuiService.setEditSession(player.uniqueId, session)
                    AdminGuiMenu.openRecipeEditor(player, null, session)
                }
                else -> AdminGuiMenu.openRecipeEditor(player, recipeId)
            }
            return
        }
        when (slot) {
            45 -> if (page > 0) AdminGuiMenu.openRecipeList(player, page - 1)
            48 -> {
                AdminGuiService.removeEditSession(player.uniqueId)
                AdminGuiService.createEditSession(player.uniqueId, null)
                AdminGuiMenu.openRecipeEditor(player, null)
            }
            53 -> AdminGuiMenu.openRecipeList(player, page + 1)
        }
    }

    private fun handleRecipeEditorClick(event: InventoryClickEvent, player: Player) {
        event.isCancelled = true
        val session = AdminGuiService.getEditSession(player.uniqueId) ?: return
        val slot = event.rawSlot
        // 所有聊天输入槽位共享的 onSyncComplete 回调：输入完成后自动重新打开配方编辑器
        val reopenEditor: (Player) -> Unit = { p -> AdminGuiMenu.openRecipeEditor(p) }
        when (slot) {
            0 -> AdminGuiService.requestChatInput(player, ChatInputType.RECIPE_ID, callback = {
                val currentId = session.id
                session.id = it.trim()
                // 如果解锁权限使用标准前缀，同步更新为新 ID
                if (session.unlockPermission.startsWith("craft.recipe.") && currentId.isNotBlank()) {
                    session.unlockPermission = "craft.recipe.${session.id}"
                }
            }, onSyncComplete = reopenEditor)
            1 -> AdminGuiService.requestChatInput(player, ChatInputType.RECIPE_DISPLAY_NAME, callback = { session.displayName = it.trim() }, onSyncComplete = reopenEditor)
            2 -> AdminGuiService.requestChatInput(player, ChatInputType.RECIPE_CATEGORY, callback = { session.category = it.trim() }, onSyncComplete = reopenEditor)
            3 -> AdminGuiService.requestChatInput(player, ChatInputType.RECIPE_REQUIRED_TIER, callback = { session.requiredBenchTier = it.trim() }, onSyncComplete = reopenEditor)
            4 -> {
                AdminGuiService.autoSaveRecipe(session)
                AdminGuiMenu.openMaterialList(player)
            }
            5 -> {
                AdminGuiService.autoSaveRecipe(session)
                AdminGuiMenu.openRewardItems(player)
            }
            6 -> AdminGuiService.requestChatInput(player, ChatInputType.RECIPE_CRAFT_TIME, callback = {
                session.craftTimeSeconds = it.toDoubleOrNull()?.coerceAtLeast(0.1) ?: 5.0
            }, onSyncComplete = reopenEditor)
            7 -> AdminGuiService.requestChatInput(player, ChatInputType.RECIPE_EXPERIENCE, callback = {
                session.experience = it.toIntOrNull()?.coerceAtLeast(0) ?: 0
            }, onSyncComplete = reopenEditor)
            8 -> {
                // 开关切换：默认开启，权限为 craft.recipe.<recipeId>
                if (session.unlockPermission.isBlank()) {
                    session.unlockPermission = "craft.recipe.${session.id}"
                } else {
                    session.unlockPermission = ""
                }
                AdminGuiMenu.openRecipeEditor(player)
            }
            27 -> {
                AdminGuiService.autoSaveRecipe(session)
                AdminGuiMenu.openRecipeList(player)
            }
            35 -> AdminGuiMenu.openDeleteConfirm(player, DeleteTarget.Recipe(session.id.ifBlank { session.originalRecipeId ?: "" }))
        }
    }

    // 材料列表 GUI：slot 0-44 允许原生物品操作，底部功能区取消事件
    private fun handleMaterialListClick(event: InventoryClickEvent, player: Player) {
        val slot = event.rawSlot
        // 底部功能区
        if (slot in 45..53) {
            event.isCancelled = true
            if (slot == 45) {
                AdminGuiMenu.openRecipeEditor(player)
            }
            return
        }
        // 在 GUI 区域内（0-44）：允许放入/取出，不取消事件
        if (slot in 0..44) {
            if (event.cursor != null && !event.cursor.type.isAir) {
                return
            }
        }
    }

    // 奖励物品 GUI：slot 0-17 允许原生物品操作，底部功能区取消事件
    private fun handleRewardItemsClick(event: InventoryClickEvent, player: Player) {
        val slot = event.rawSlot
        // 底部功能区
        if (slot in 18..26) {
            event.isCancelled = true
            if (slot == 18) {
                AdminGuiMenu.openRecipeEditor(player)
            }
            return
        }
        // 在 GUI 区域内（0-17）：允许放入/取出，不取消事件
        if (slot in 0..17) {
            if (event.cursor != null && !event.cursor.type.isAir) {
                return
            }
        }
    }

    // 关闭材料列表 GUI 时：自动保存
    private fun handleMaterialListClose(player: Player, inventory: org.bukkit.inventory.Inventory) {
        val session = AdminGuiService.getEditSession(player.uniqueId) ?: return
        val newMaterials = mutableListOf<MutableRecipeMaterial>()
        for (slot in 0 until 45) {
            val item = inventory.getItem(slot) ?: continue
            if (item.type.isAir) continue
            val spec = AdminGuiService.deriveSpecFromItem(item)
            if (spec.isBlank()) continue
            newMaterials.add(MutableRecipeMaterial(spec, item.amount))
        }
        session.materials.clear()
        session.materials.addAll(newMaterials)
        AdminGuiService.saveRecipe(session) { success, msg ->
            player.sendMessage(if (success) "§a材料列表已保存" else "§c材料保存失败: $msg")
        }
    }

    // 关闭奖励物品 GUI 时：自动保存
    private fun handleRewardItemsClose(player: Player, inventory: org.bukkit.inventory.Inventory) {
        val session = AdminGuiService.getEditSession(player.uniqueId) ?: return
        val newResults = mutableListOf<MutableRecipeResult>()
        for (slot in 0 until 18) {
            val item = inventory.getItem(slot) ?: continue
            if (item.type.isAir) continue
            val spec = AdminGuiService.deriveSpecFromItem(item)
            if (spec.isBlank()) continue
            newResults.add(MutableRecipeResult(spec, item.amount))
        }
        session.results.clear()
        session.results.addAll(newResults)
        AdminGuiService.saveRecipe(session) { success, msg ->
            player.sendMessage(if (success) "§a奖励物品已保存" else "§c奖励物品保存失败: $msg")
        }
    }

    private fun handleDeleteConfirmClick(event: InventoryClickEvent, player: Player) {
        event.isCancelled = true
        val target = AdminGuiService.getDeleteTarget(player.uniqueId) ?: return
        when (event.rawSlot) {
            23 -> {
                when (target) {
                    is DeleteTarget.Recipe -> AdminGuiService.deleteRecipe(target.recipeId) { success, msg ->
                        player.sendMessage(if (success) "§a$msg" else "§c$msg")
                        AdminGuiService.removeDeleteTarget(player.uniqueId)
                        AdminGuiMenu.openRecipeList(player)
                    }
                }
            }
            26 -> {
                AdminGuiService.removeDeleteTarget(player.uniqueId)
                AdminGuiMenu.openRecipeList(player)
            }
        }
    }

    private fun getRecipeIdsForPage(page: Int): List<String> {
        val recipes = AdminGuiService.getAllRecipeIds()
        val start = page * 45
        if (start >= recipes.size) {
            return emptyList()
        }
        val end = (start + 45).coerceAtMost(recipes.size)
        return recipes.subList(start, end)
    }
}
