package com.pixlehavencore.feature.craftingbench

import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import java.util.UUID

enum class AdminGuiPage {
    RECIPE_LIST,
    RECIPE_EDITOR,
    MATERIAL_LIST,
    REWARD_ITEMS,
    DELETE_CONFIRM,
}

class AdminGuiHolder(
    val ownerId: UUID,
    val page: AdminGuiPage,
    val context: MutableMap<String, Any> = mutableMapOf(),
) : InventoryHolder {
    lateinit var backingInventory: Inventory
    override fun getInventory(): Inventory = backingInventory
}

data class RecipeEditSession(
    var id: String,
    var displayName: String,
    var category: String,
    var requiredBenchTier: String,
    val materials: MutableList<MutableRecipeMaterial>,
    val results: MutableList<MutableRecipeResult>,
    var craftTimeSeconds: Double,
    var experience: Int,
    var unlockPermission: String,
    var originalRecipeId: String?,
)

data class MutableRecipeMaterial(
    var item: String,
    var amount: Int,
)

data class MutableRecipeResult(
    var item: String,
    var amount: Int,
)

data class PendingChatInput(
    val type: ChatInputType,
    val expireAt: Long,
    val callback: (String) -> Unit,
    // 主线程回调，用于在 submit {} 调度到主线程后执行 Inventory 打开操作
    val onSyncComplete: ((Player) -> Unit)? = null,
)

enum class ChatInputType {
    RECIPE_ID,
    RECIPE_DISPLAY_NAME,
    RECIPE_CATEGORY,
    RECIPE_CRAFT_TIME,
    RECIPE_EXPERIENCE,
    RECIPE_UNLOCK_PERM,
    RECIPE_REQUIRED_TIER,
}

sealed class DeleteTarget {
    data class Recipe(val recipeId: String) : DeleteTarget()
}
