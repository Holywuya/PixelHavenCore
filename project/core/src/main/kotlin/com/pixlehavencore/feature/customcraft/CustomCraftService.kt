package com.pixlehavencore.feature.customcraft

import com.pixlehavencore.util.ItemUtils
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.RecipeChoice
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.ShapelessRecipe
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning

object CustomCraftService {

    private val recipes = mutableMapOf<String, CraftingRecipe>()
    private val registeredKeys = mutableListOf<NamespacedKey>()

    fun init() {
        CustomCraftSettings.init()
        if (!CustomCraftSettings.enabled) return
        loadAllRecipes()
    }

    fun reload() {
        unregisterAll()
        CustomCraftSettings.reload()
        loadAllRecipes()
    }

    fun stop() {
        unregisterAll()
    }

    fun getRecipe(id: String): CraftingRecipe? = recipes[id]

    fun getAllRecipes(): List<CraftingRecipe> = recipes.values.toList()

    fun getRegisteredKeys(): List<NamespacedKey> = registeredKeys.toList()

    @SubscribeEvent
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!CustomCraftSettings.enableAutoDiscover) return
        registeredKeys.forEach { key ->
            runCatching { event.player.discoverRecipe(key) }
        }
    }

    private fun loadAllRecipes() {
        recipes.clear()
        val loaded = CustomCraftRecipeLoader.loadAll()
        loaded.forEach { recipe ->
            recipes[recipe.id] = recipe
            registerBukkitRecipe(recipe)
        }
        info("[CustomCraft] 已加载 ${recipes.size} 个配方")
    }

    fun loadAndRegister(id: String): Boolean {
        val recipe = CustomCraftRecipeLoader.load(id) ?: return false
        unregisterRecipe(recipe.id)
        recipes[recipe.id] = recipe
        registerBukkitRecipe(recipe)
        return true
    }

    fun saveAndRegister(recipe: CraftingRecipe) {
        CustomCraftRecipeLoader.saveToFile(recipe)
        unregisterRecipe(recipe.id)
        recipes[recipe.id] = recipe
        registerBukkitRecipe(recipe)
    }

    fun deleteRecipe(id: String): Boolean {
        val existing = recipes.remove(id) ?: return false
        unregisterRecipe(id)
        CustomCraftRecipeLoader.deleteFile(id)
        return true
    }

    fun recipeKey(id: String): NamespacedKey {
        val filtered = id.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it in "_-./" }
        val key = filtered.ifEmpty {
            id.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
        }
        return NamespacedKey("phcore", key)
    }

    private fun unregisterRecipe(id: String) {
        val key = recipeKey(id)
        Bukkit.removeRecipe(key)
        registeredKeys.remove(key)
    }

    private fun unregisterAll() {
        registeredKeys.forEach { Bukkit.removeRecipe(it) }
        registeredKeys.clear()
        recipes.clear()
    }

    private fun registerBukkitRecipe(recipe: CraftingRecipe) {
        val key = recipeKey(recipe.id)
        val resultItem = CustomCraftRecipeLoader.ingredientToItem(recipe.result) ?: return

        runCatching {
            when (recipe.type) {
                RecipeType.SHAPED -> {
                    val shaped = ShapedRecipe(key, resultItem)
                    val shape = buildShape(recipe.materials)
                    shaped.shape(shape[0], shape[1], shape[2])
                    for ((index, ing) in recipe.materials.withIndex()) {
                        val char = ('a' + index.coerceAtMost(25))
                        val item = CustomCraftRecipeLoader.ingredientToItem(ing)
                        if (item != null) {
                            shaped.setIngredient(char, RecipeChoice.ExactChoice(item))
                        }
                    }
                    Bukkit.addRecipe(shaped)
                }
                RecipeType.SHAPELESS -> {
                    val shapeless = ShapelessRecipe(key, resultItem)
                    recipe.materials.forEach { ing ->
                        val item = CustomCraftRecipeLoader.ingredientToItem(ing)
                        if (item != null) {
                            shapeless.addIngredient(ing.amount, item)
                        }
                    }
                    Bukkit.addRecipe(shapeless)
                }
            }
            registeredKeys.add(key)
        }.onFailure { ex ->
            warning("[CustomCraft] 注册配方失败 ${recipe.id}: ${ex.message}")
        }
    }

    private fun buildShape(materials: List<RecipeIngredient>): Array<String> {
        val grid = CharArray(9) { ' ' }
        materials.filter { it.slot != null && it.slot in 0..8 }.forEach { ing ->
            grid[ing.slot!!] = ('a' + (ing.slot ?: 0))
        }
        return arrayOf(
            String(charArrayOf(grid[0], grid[1], grid[2])),
            String(charArrayOf(grid[3], grid[4], grid[5])),
            String(charArrayOf(grid[6], grid[7], grid[8]))
        )
    }
}
