package com.pixlehavencore.feature.craftingbench

import com.pixlehavencore.util.ArimFolderUtils
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import taboolib.module.configuration.Configuration
import java.io.File

object CraftingBenchRecipeLoader {

    fun loadAll(): Map<String, CraftingRecipe> {
        val folder = File(getDataFolder(), "feature/crafting-bench/recipes")
        folder.mkdirs()
        if (!folder.exists()) {
            return emptyMap()
        }
        val recipes = linkedMapOf<String, CraftingRecipe>()
        ArimFolderUtils.walkYaml(folder, filter = {
            isFile && (extension.equals("yml", true) || extension.equals("yaml", true))
        }) {
            val sourcePath = file?.relativeTo(folder)?.invariantSeparatorsPath ?: return@walkYaml
            val recipe = parse(sourcePath, this) ?: return@walkYaml
            recipes[recipe.id] = recipe
        }
        return recipes
    }

    private fun parse(sourcePath: String, config: Configuration): CraftingRecipe? {
        return runCatching {
            val recipeId = (config.getString("id") ?: File(sourcePath).nameWithoutExtension).trim()
            if (recipeId.isBlank()) {
                warning("[CraftingBench] [$sourcePath] id 为空，已跳过。")
                return null
            }
            val materials = config.getMapList("materials").mapNotNull { raw ->
                val item = raw["item"]?.toString()?.trim().orEmpty()
                val amount = raw["amount"]?.toString()?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                if (item.isBlank()) null else RecipeMaterial(item, amount)
            }
            if (materials.isEmpty()) {
                warning("[CraftingBench] [$sourcePath] materials 为空，已跳过。")
                return null
            }
            val results = parseResults(config, sourcePath)
            if (results.isEmpty()) {
                warning("[CraftingBench] [$sourcePath] results 为空，已跳过。")
                return null
            }
            CraftingRecipe(
                id = recipeId,
                displayName = config.getString("display_name") ?: recipeId,
                category = config.getString("category")?.trim().orEmpty(),
                requiredBenchTier = config.getString("required_bench_tier")?.trim().orEmpty(),
                materials = materials,
                results = results,
                craftTimeSeconds = config.getDouble("craft_time_seconds", 5.0).coerceAtLeast(0.1),
                experience = config.getInt("experience", 0).coerceAtLeast(0),
                unlockPermission = config.getString("unlock_permission")?.trim().orEmpty(),
                sourcePath = sourcePath,
            )
        }.onFailure { ex ->
            warning("[CraftingBench] [$sourcePath] 读取配方失败: ${ex.message}")
        }.getOrNull()
    }

    // 优先解析新格式 results 列表，兼容旧 result 单对象格式
    private fun parseResults(config: Configuration, sourcePath: String): List<RecipeResult> {
        if (config.contains("results")) {
            val parsed = config.getMapList("results").mapNotNull { raw ->
                val item = raw["item"]?.toString()?.trim().orEmpty()
                val amount = raw["amount"]?.toString()?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                if (item.isBlank()) null else RecipeResult(item, amount)
            }
            // 同时存在 results 和 result 时，优先 results，记录提示
            if (config.contains("result")) {
                info("[CraftingBench] [$sourcePath] 同时包含 results 和 result，优先使用 results")
            }
            return parsed
        }
        // 兼容旧格式 result 单对象
        if (config.contains("result")) {
            val resultSection = config.getConfigurationSection("result")
            val resultItem = resultSection?.getString("item")?.trim().orEmpty()
            val resultAmount = resultSection?.getInt("amount", 1)?.coerceAtLeast(1) ?: 1
            if (resultItem.isNotBlank()) {
                migrateResultToResults(config, sourcePath, resultItem, resultAmount)
                return listOf(RecipeResult(resultItem, resultAmount))
            }
        }
        return emptyList()
    }

    // 旧格式迁移：将 result 单对象改为 results 列表格式，异步写入
    private fun migrateResultToResults(config: Configuration, sourcePath: String, item: String, amount: Int) {
        submitAsync {
            runCatching {
                config["result"] = null
                config["results"] = listOf(mapOf("item" to item, "amount" to amount))
                val currentVersion = config.getInt("version", 1)
                config["version"] = currentVersion + 1
                config.saveToFile(File(getDataFolder(), "feature/crafting-bench/recipes/$sourcePath"))
                info("[CraftingBench] [$sourcePath] 已迁移 result → results 格式")
            }.onFailure { ex ->
                warning("[CraftingBench] [$sourcePath] 迁移写入失败: ${ex.message}，配方已加载但文件未更新")
            }
        }
    }
}
