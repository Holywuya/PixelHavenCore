package com.pixlehavencore.feature.craftingbench

import com.pixlehavencore.util.BaikirutoItemsUtil
import com.pixlehavencore.util.CraftEngineItemsUtil
import com.pixlehavencore.util.ItemUtils
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import taboolib.platform.util.submit as submitOnEntity
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object AdminGuiService {

    private const val ADMIN_PERMISSION = "phcore.admin"
    private const val CHAT_INPUT_TIMEOUT_MS = 30_000L

    private val editSessions = ConcurrentHashMap<UUID, RecipeEditSession>()
    private val pendingChatInputs = ConcurrentHashMap<UUID, PendingChatInput>()
    private val deleteTargets = ConcurrentHashMap<UUID, DeleteTarget>()

    fun hasAdminPermission(player: Player): Boolean {
        return player.hasPermission(ADMIN_PERMISSION)
    }

    fun getEditSession(playerId: UUID): RecipeEditSession? = editSessions[playerId]

    fun createEditSession(playerId: UUID, originalRecipeId: String?): RecipeEditSession {
        val existing = originalRecipeId?.let { CraftingBenchService.getRecipe(it) }
        val session = if (existing != null) {
            RecipeEditSession(
                id = existing.id,
                displayName = existing.displayName,
                category = existing.category,
                requiredBenchTier = existing.requiredBenchTier,
                materials = existing.materials.map { MutableRecipeMaterial(it.item, it.amount) }.toMutableList(),
                results = existing.results.map { MutableRecipeResult(it.item, it.amount) }.toMutableList(),
                craftTimeSeconds = existing.craftTimeSeconds,
                experience = existing.experience,
                unlockPermission = existing.unlockPermission,
                originalRecipeId = originalRecipeId,
            )
        } else {
            RecipeEditSession(
                id = "",
                displayName = "",
                category = "",
                requiredBenchTier = CraftingBenchSettings.getTierIds().firstOrNull().orEmpty(),
                materials = mutableListOf(),
                results = mutableListOf(),
                craftTimeSeconds = 5.0,
                experience = 0,
                unlockPermission = "",
                originalRecipeId = null,
            )
        }
        editSessions[playerId] = session
        return session
    }

    fun removeEditSession(playerId: UUID) {
        editSessions.remove(playerId)
    }

    fun setEditSession(playerId: UUID, session: RecipeEditSession) {
        editSessions[playerId] = session
    }

    fun getDeleteTarget(playerId: UUID): DeleteTarget? = deleteTargets[playerId]

    fun setDeleteTarget(playerId: UUID, target: DeleteTarget) {
        deleteTargets[playerId] = target
    }

    fun removeDeleteTarget(playerId: UUID) {
        deleteTargets.remove(playerId)
    }

    fun validateRecipe(session: RecipeEditSession): List<String> {
        val errors = mutableListOf<String>()
        val sanitized = sanitizeRecipeId(session.id)
        if (session.id.isBlank()) {
            errors.add("配方ID不能为空")
        } else if (sanitized == null) {
            errors.add("配方ID包含非法字符")
        }
        if (session.id.isNotBlank() && session.originalRecipeId != session.id) {
            if (CraftingBenchService.getRecipe(session.id) != null) {
                errors.add("配方ID '${session.id}' 已存在")
            }
        }
        if (session.displayName.isBlank()) {
            errors.add("显示名称不能为空")
        }
        if (session.materials.isEmpty()) {
            errors.add("至少需要一个材料")
        }
        session.materials.forEachIndexed { index, mat ->
            if (mat.item.isBlank()) errors.add("材料#${index + 1} 物品规格为空")
            if (mat.amount <= 0) errors.add("材料#${index + 1} 数量必须大于0")
        }
        if (session.results.isEmpty()) {
            errors.add("至少需要一个产出物品")
        }
        session.results.forEachIndexed { index, result ->
            if (result.item.isBlank()) errors.add("产出#${index + 1} 物品规格为空")
            if (result.amount <= 0) errors.add("产出#${index + 1} 数量必须大于0")
        }
        if (session.craftTimeSeconds <= 0) {
            errors.add("制作时间必须大于0")
        }
        return errors
    }

    fun saveRecipe(session: RecipeEditSession, onComplete: (Boolean, String) -> Unit) {
        val errors = validateRecipe(session)
        if (errors.isNotEmpty()) {
            onComplete(false, errors.joinToString("\n"))
            return
        }
        val yamlContent = serializeRecipeYaml(session)
        val recipesFolder = File(getDataFolder(), "feature/crafting-bench/recipes")
        val targetFile = File(recipesFolder, "${session.id}.yml")
        val oldFile = if (session.originalRecipeId != null && session.originalRecipeId != session.id) {
            File(recipesFolder, "${session.originalRecipeId}.yml")
        } else null
        submitAsync {
            runCatching {
                if (oldFile != null && oldFile.exists()) {
                    oldFile.delete()
                }
                atomicWrite(targetFile, yamlContent)
                CraftingBenchService.reload()
            }.onSuccess {
                submit { onComplete(true, "配方 '${session.id}' 保存成功") }
            }.onFailure { ex ->
                warning("[CraftingBench] 保存配方失败: ${ex.message}")
                submit { onComplete(false, "保存失败: ${ex.message}") }
            }
        }
    }

    fun deleteRecipe(recipeId: String, onComplete: (Boolean, String) -> Unit) {
        if (hasActiveCraftingTasks(recipeId)) {
            onComplete(false, "该配方有进行中的制作任务，无法删除")
            return
        }
        val recipesFolder = File(getDataFolder(), "feature/crafting-bench/recipes")
        val targetFile = File(recipesFolder, "$recipeId.yml")
        submitAsync {
            runCatching {
                if (targetFile.exists()) targetFile.delete()
                CraftingBenchService.reload()
            }.onSuccess {
                submit { onComplete(true, "配方 '$recipeId' 已删除") }
            }.onFailure { ex ->
                warning("[CraftingBench] 删除配方失败: ${ex.message}")
                submit { onComplete(false, "删除失败: ${ex.message}") }
            }
        }
    }

    fun copyRecipeSession(sourceRecipeId: String): RecipeEditSession? {
        val source = CraftingBenchService.getRecipe(sourceRecipeId) ?: return null
        return RecipeEditSession(
            id = "${source.id}_copy",
            displayName = source.displayName,
            category = source.category,
            requiredBenchTier = source.requiredBenchTier,
            materials = source.materials.map { MutableRecipeMaterial(it.item, it.amount) }.toMutableList(),
            results = source.results.map { MutableRecipeResult(it.item, it.amount) }.toMutableList(),
            craftTimeSeconds = source.craftTimeSeconds,
            experience = source.experience,
            unlockPermission = source.unlockPermission,
            originalRecipeId = null,
        )
    }

    // 从 ItemStack 推导物品规格字符串，优先级 ce: → bai: → 原版材质名
    fun deriveSpecFromItem(item: ItemStack): String {
        if (item.type.isAir) return ""
        CraftEngineItemsUtil.getItemId(item)?.let { return "ce:$it" }
        BaikirutoItemsUtil.getItemId(item)?.let { return "bai:$it" }
        return item.type.name
    }

    fun hasPendingInput(playerId: UUID): Boolean = pendingChatInputs.containsKey(playerId)

    fun removePendingInput(playerId: UUID) {
        pendingChatInputs.remove(playerId)
    }

    fun handleChatInput(player: Player, message: String): Boolean {
        val pending = pendingChatInputs[player.uniqueId] ?: return false
        if (System.currentTimeMillis() > pending.expireAt) {
            pendingChatInputs.remove(player.uniqueId)
            return false
        }
        pendingChatInputs.remove(player.uniqueId)

        // 阶段1：数据回写在当前异步线程执行（纯内存操作，线程安全）
        pending.callback(message)

        // 阶段2：GUI 打开操作调度到玩家区域线程执行（AsyncChatEvent 回调线程不允许调用 openInventory）
        // Folia: 必须在玩家的区域线程上调用 openInventory，而非全局调度器
        if (pending.onSyncComplete != null) {
            val playerId = player.uniqueId
            val syncAction = pending.onSyncComplete
            player.submitOnEntity {
                val onlinePlayer = Bukkit.getPlayer(playerId)
                if (onlinePlayer == null || !onlinePlayer.isOnline) {
                    return@submitOnEntity
                }
                syncAction.invoke(onlinePlayer)
            }
        }
        return true
    }

    fun requestChatInput(
        player: Player,
        type: ChatInputType,
        callback: (String) -> Unit,
        onSyncComplete: ((Player) -> Unit)? = null,
    ) {
        pendingChatInputs[player.uniqueId] = PendingChatInput(
            type = type,
            expireAt = System.currentTimeMillis() + CHAT_INPUT_TIMEOUT_MS,
            callback = callback,
            onSyncComplete = onSyncComplete,
        )
        player.closeInventory()
        player.sendMessage("§e请在聊天中输入${typeDisplayName(type)}，30秒内有效。输入 'cancel' 取消。")
    }

    fun sanitizeRecipeId(raw: String): String? {
        val sanitized = raw.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_")
        if (sanitized.isBlank()) return null
        if (sanitized.contains("..") || sanitized.startsWith("/") || sanitized.startsWith("\\")) return null
        return sanitized
    }

    fun hasActiveCraftingTasks(recipeId: String): Boolean {
        return CraftingBenchService.hasActiveTasksForRecipe(recipeId)
    }

    fun getAllRecipeIds(): List<String> {
        return CraftingBenchService.getAllRecipeIds()
    }

    fun cleanupPlayer(playerId: UUID) {
        editSessions.remove(playerId)
        pendingChatInputs.remove(playerId)
        deleteTargets.remove(playerId)
    }

    fun autoSaveRecipe(session: RecipeEditSession) {
        if (session.id.isBlank()) return
        val errors = validateRecipe(session)
        if (errors.isNotEmpty()) return
        val yamlContent = serializeRecipeYaml(session)
        val recipesFolder = File(getDataFolder(), "feature/crafting-bench/recipes")
        val targetFile = File(recipesFolder, "${session.id}.yml")
        // 配方 ID 变更时清理旧文件
        if (session.originalRecipeId != null && session.originalRecipeId != session.id) {
            val oldFile = File(recipesFolder, "${session.originalRecipeId}.yml")
            if (oldFile.exists()) oldFile.delete()
            session.originalRecipeId = session.id
        }
        submitAsync {
            runCatching {
                atomicWrite(targetFile, yamlContent)
                CraftingBenchService.reload()
            }.onFailure { ex ->
                warning("[CraftingBench] 自动保存配方失败: ${ex.message}")
            }
        }
    }

    private fun serializeRecipeYaml(session: RecipeEditSession): String {
        val materialsBlock = session.materials.joinToString("\n") { mat ->
            "  - item: \"${mat.item}\"\n    amount: ${mat.amount}"
        }
        // 优先使用 result 单对象格式（旧格式），保持与期望格式一致
        val resultBlock = if (session.results.size == 1) {
            val r = session.results[0]
            "result:\n  item: \"${r.item}\"\n  amount: ${r.amount}"
        } else {
            // 多产出时使用 results 列表格式
            val resultsBlock = session.results.joinToString("\n") { result ->
                "  - item: \"${result.item}\"\n    amount: ${result.amount}"
            }
            "results:\n$resultsBlock"
        }
        // craft_time_seconds 转为整数（如果是整数值）
        val craftTimeStr = if (session.craftTimeSeconds == session.craftTimeSeconds.toLong().toDouble()) {
            session.craftTimeSeconds.toLong().toString()
        } else {
            session.craftTimeSeconds.toString()
        }
        return buildString {
            appendLine("id: \"${session.id}\"")
            appendLine("display_name: \"${session.displayName}\"")
            appendLine("category: \"${session.category}\"")
            appendLine("required_bench_tier: \"${session.requiredBenchTier}\"")
            appendLine()
            appendLine("materials:")
            appendLine(materialsBlock)
            appendLine()
            appendLine(resultBlock)
            appendLine()
            appendLine("craft_time_seconds: $craftTimeStr")
            appendLine("experience: ${session.experience}")
            appendLine("unlock_permission: \"${session.unlockPermission}\"")
        }
    }

    private fun atomicWrite(targetFile: File, content: String) {
        targetFile.parentFile?.mkdirs()
        val tmpFile = File(targetFile.parent, "${targetFile.name}.tmp")
        try {
            tmpFile.writeText(content, Charsets.UTF_8)
            if (targetFile.exists()) {
                targetFile.delete()
            }
            val renamed = tmpFile.renameTo(targetFile)
            if (!renamed) {
                throw IllegalStateException("重命名临时文件失败")
            }
        } catch (ex: Exception) {
            tmpFile.delete()
            throw ex
        }
    }

    private fun typeDisplayName(type: ChatInputType): String = when (type) {
        ChatInputType.RECIPE_ID -> "配方ID"
        ChatInputType.RECIPE_DISPLAY_NAME -> "显示名称"
        ChatInputType.RECIPE_CATEGORY -> "分类"
        ChatInputType.RECIPE_CRAFT_TIME -> "制作时间(秒)"
        ChatInputType.RECIPE_EXPERIENCE -> "经验值"
        ChatInputType.RECIPE_UNLOCK_PERM -> "解锁权限"
        ChatInputType.RECIPE_REQUIRED_TIER -> "所需工作台等级"
    }
}
