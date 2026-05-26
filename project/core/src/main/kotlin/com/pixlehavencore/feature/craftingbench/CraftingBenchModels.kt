package com.pixlehavencore.feature.craftingbench

import java.util.UUID

data class BenchBlockMapping(
    val blockId: String,
    val tierId: String,
)

data class BenchTier(
    val id: String,
    val displayName: String,
    val speedModifier: Double,
    val usePermission: String,
    val rank: Int,
)

data class CraftingSpecialization(
    val id: String,
    val permission: String,
    val timeReduction: Double,
    val appliesTo: Set<String>,
)

data class QueueSettings(
    val allowOfflineCrafting: Boolean,
    val autoClaimOnline: Boolean,
    val fullInventoryAction: String,
    val cancelRefundRatio: Double,
    val defaultMaxQueueSize: Int,
    val permissionLimits: List<QueuePermissionLimit>,
)

data class QueuePermissionLimit(
    val id: String,
    val permission: String,
    val maxQueueSize: Int,
    val priority: Int,
)

data class RecipeMaterial(
    val item: String,
    val amount: Int,
)

data class RecipeResult(
    val item: String,
    val amount: Int,
)

data class CraftingRecipe(
    val id: String,
    val displayName: String,
    val category: String,
    val requiredBenchTier: String,
    val materials: List<RecipeMaterial>,
    val results: List<RecipeResult>,
    val craftTimeSeconds: Double,
    val experience: Int,
    val unlockPermission: String,
    val sourcePath: String,
)

data class CraftingTask(
    val taskId: Long,
    val owner: UUID,
    val recipeId: String,
    val craftCount: Int,
    val totalTicks: Long,
    var remainingTicks: Long,
    val submittedAt: Long,
)

data class RecipePreview(
    val recipe: CraftingRecipe,
    val canCraft: Boolean,
    val enoughMaterials: Boolean,
    val estimatedSeconds: Double,
)

data class RecipeMaterialStatus(
    val material: RecipeMaterial,
    val inventoryAmount: Int,
    val warehouseAmount: Int,
    val requiredAmount: Int,
    val warehouseWillUse: Int,
) {
    val totalAmount: Int
        get() = inventoryAmount + warehouseAmount

    val enough: Boolean
        get() = totalAmount >= requiredAmount
}

data class ClaimEntry(
    val owner: UUID,
    val itemSpec: String,
    val amount: Int,
    val sourceTaskId: Long,
)
