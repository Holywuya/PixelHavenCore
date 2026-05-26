package com.pixlehavencore.feature.title

import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.warning
import taboolib.module.configuration.Configuration
import java.io.File

object TitleDefinitionLoader {

    fun loadAll(): Map<String, TitleDefinition> {
        val titlesDir = File(getDataFolder(), "feature/title/titles")
        if (!titlesDir.exists()) {
            titlesDir.mkdirs()
            return emptyMap()
        }
        val files = titlesDir.listFiles { f -> f.isFile && f.extension.equals("yml", ignoreCase = true) }
            ?: return emptyMap()
        val result = mutableMapOf<String, TitleDefinition>()
        for (file in files) {
            runCatching {
                val config = Configuration.loadFromFile(file)
                val section = config.getConfigurationSection("titles") ?: return@runCatching
                for (key in section.getKeys(false)) {
                    val id = key.trim()
                    if (id.isBlank()) continue
                    if (result.containsKey(id)) {
                        warning("[Title] 重复的称号ID '$id'，位于 ${file.name}，已跳过。")
                        continue
                    }
                    result[id] = TitleDefinition(
                        id = id,
                        displayName = section.getString("$key.display_name") ?: id,
                        description = section.getStringList("$key.description"),
                        icon = section.getString("$key.icon") ?: "NAME_TAG",
                        category = section.getString("$key.category") ?: "default",
                        rarity = section.getString("$key.rarity") ?: "common",
                        permission = section.getString("$key.permission")?.trim().orEmpty(),
                        craftEngineDisplay = section.getString("$key.craftengine_display")?.trim()?.takeIf { it.isNotBlank() },
                        sourcePath = file.name,
                    )
                }
            }.onFailure { ex ->
                warning("[Title] 加载称号文件 ${file.name} 失败: ${ex.message}")
            }
        }
        return result
    }
}
