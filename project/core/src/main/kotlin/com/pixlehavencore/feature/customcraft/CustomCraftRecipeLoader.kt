package com.pixlehavencore.feature.customcraft

import com.pixlehavencore.util.ItemUtils
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.warning
import taboolib.module.configuration.Configuration
import top.maplex.arim.tools.gson.GsonUtils
import java.io.File

object CustomCraftRecipeLoader {

    private val recipesDir: File
        get() = File(getDataFolder(), "feature/customcraft/recipes")

    fun loadAll(): List<CraftingRecipe> {
        if (!recipesDir.exists()) recipesDir.mkdirs()
        val files = recipesDir.listFiles { f -> f.extension == "yml" } ?: emptyArray()
        return files.mapNotNull { loadFromFile(it) }
    }

    fun load(id: String): CraftingRecipe? {
        val file = File(recipesDir, "$id.yml")
        if (!file.exists()) return null
        return loadFromFile(file)
    }

    private fun loadFromFile(file: File): CraftingRecipe? {
        return runCatching {
            val config = Configuration.loadFromFile(file)
            val id = config.getString("id") ?: file.nameWithoutExtension
            val type = when (config.getString("type")?.lowercase()) {
                "shapeless" -> RecipeType.SHAPELESS
                else -> RecipeType.SHAPED
            }

            @Suppress("UNCHECKED_CAST")
            val materials = config.getMapList("materials").mapNotNull { map ->
                parseIngredient(map as Map<String, Any>)
            }

            @Suppress("UNCHECKED_CAST")
            val resultMap = config.getConfigurationSection("result")?.getValues(false)?.mapValues { it.value as Any } ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            val result = parseIngredient(resultMap as Map<String, Any>) ?: return null

            CraftingRecipe(id = id, type = type, materials = materials, result = result)
        }.onFailure { ex ->
            warning("[CustomCraft] 加载配方失败 ${file.name}: ${ex.message}")
        }.getOrNull()
    }

    fun saveToFile(recipe: CraftingRecipe) {
        val file = File(recipesDir, "${recipe.id}.yml")
        recipesDir.mkdirs()
        val config = Configuration.empty()
        config["id"] = recipe.id
        config["type"] = recipe.type.name.lowercase()

        val materialsList = recipe.materials.map { ingredientToMap(it) }
        config["materials"] = materialsList

        config["result"] = ingredientToMap(recipe.result)

        config.saveToFile(file)
    }

    private fun parseIngredient(map: Map<String, Any>): RecipeIngredient? {
        val spec = map["spec"] as? String
        val json = map["json"] as? String
        if (spec == null && json == null) return null
        val amount = (map["amount"] as? Number)?.toInt() ?: 1
        val slot = (map["slot"] as? Number)?.toInt()
        return RecipeIngredient(spec = spec, json = json, amount = amount, slot = slot)
    }

    private fun ingredientToMap(ingredient: RecipeIngredient): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        ingredient.spec?.let { map["spec"] = it }
        ingredient.json?.let { map["json"] = it }
        map["amount"] = ingredient.amount
        ingredient.slot?.let { map["slot"] = it }
        return map
    }

    fun itemToIngredient(item: ItemStack): RecipeIngredient {
        val libId = ItemUtils.getNamespacedItemId(item)
        if (libId != null) {
            return RecipeIngredient(spec = libId, amount = item.amount)
        }
        val spec = getMaterialSpec(item)
        if (spec != null) {
            return RecipeIngredient(spec = spec, amount = item.amount)
        }
        val json = GsonUtils.toJson(item)
        return RecipeIngredient(json = json, amount = item.amount)
    }

    private fun getMaterialSpec(item: ItemStack): String? {
        if (item.itemMeta != null && (item.itemMeta.hasDisplayName() || item.itemMeta.hasLore() || item.itemMeta.hasEnchants())) {
            return null
        }
        return item.type.name
    }

    fun ingredientToItem(ingredient: RecipeIngredient): ItemStack? {
        if (!ingredient.spec.isNullOrBlank()) {
            return ItemUtils.resolveSpec(ingredient.spec)?.apply { amount = ingredient.amount }
        }
        if (!ingredient.json.isNullOrBlank()) {
            val item = GsonUtils.fromJson(ingredient.json, ItemStack::class.java)
            item?.amount = ingredient.amount
            return item
        }
        return null
    }
}
