package com.pixlehavencore.feature.customcraft

enum class RecipeType { SHAPED, SHAPELESS }

data class CraftingRecipe(
    val id: String,
    val type: RecipeType,
    val materials: List<RecipeIngredient>,
    val result: RecipeIngredient
)

data class RecipeIngredient(
    val spec: String? = null,
    val json: String? = null,
    val amount: Int = 1,
    val slot: Int? = null
)
