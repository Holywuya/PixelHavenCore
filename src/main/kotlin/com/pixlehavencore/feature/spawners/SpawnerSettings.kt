package com.pixlehavencore.feature.spawners

import com.pixlehavencore.util.ArimFolderUtils
import taboolib.common.platform.function.getDataFolder
import taboolib.library.configuration.ConfigurationSection
import taboolib.common.platform.function.warning
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

object SpawnerSettings {

    @Config("feature/spawners/spawners.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    var spawners: List<SpawnerDefinition> = emptyList()
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        spawners = if (enabled) loadSpawners() else emptyList()
    }

    private fun loadSpawners(): List<SpawnerDefinition> {
        val folder = File(getDataFolder(), "feature/spawners/spawners")
        folder.mkdirs()
        if (!folder.exists()) {
            return emptyList()
        }

        val loaded = mutableListOf<SpawnerDefinition>()
        ArimFolderUtils.walkYaml(
            folder,
            filter = {
                isFile && (extension.equals("yml", true) || extension.equals("yaml", true))
            }
        ) {
            val sourcePath = file?.relativeTo(folder)?.invariantSeparatorsPath ?: return@walkYaml
            loaded += parseSpawnerFile(sourcePath, this)
        }
        return loaded.sortedWith(
            compareByDescending<SpawnerDefinition> { it.priority }
                .thenBy { it.spawnerId.lowercase() }
                .thenBy { it.sourcePath }
        )
    }

    private fun parseSpawnerFile(sourcePath: String, config: Configuration): List<SpawnerDefinition> {
        val sectionKeys = config.getKeys(false)
            .filter { key -> config.getConfigurationSection(key) != null }
        if (sectionKeys.isEmpty()) {
            warning("[Spawners] [$sourcePath] 未找到顶层刷怪 section，已跳过。")
            return emptyList()
        }
        return sectionKeys.mapNotNull { sectionName ->
            val section = config.getConfigurationSection(sectionName) ?: return@mapNotNull null
            parseSpawner(sourcePath, sectionName, section)
        }
    }

    private fun parseSpawner(
        sourcePath: String,
        sectionName: String,
        config: ConfigurationSection,
    ): SpawnerDefinition? {
        return runCatching {
            val spawnerId = config.getString("SpawnerId")?.trim().takeUnless { it.isNullOrBlank() }
                ?: sectionName.trim().ifBlank { File(sourcePath).nameWithoutExtension.ifBlank { sourcePath } }
            val enabled = config.getBoolean("Enable", true)
            val types = readStringList(config, "Type")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            if (types.isEmpty()) {
                warning("[Spawners] [$sourcePath/$sectionName] Type 为空，已跳过。")
                return null
            }

            val worlds = readStringList(config, "Worlds")
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() && it != "*" }
                .toSet()
            val levels = readLevels(config, sourcePath, sectionName)
            SpawnerDefinition(
                sourcePath = sourcePath,
                spawnerId = spawnerId,
                enabled = enabled,
                types = types,
                chance = config.getDouble("Chance", 1.0).coerceIn(0.0, 1.0),
                levels = levels,
                worlds = worlds,
                intervalSeconds = config.getLong("Interval", 20L).coerceAtLeast(1L),
                spawnAmount = config.getInt("SpawnAmount", 1).coerceAtLeast(1),
                distance = config.getInt("Distance", 24).coerceAtLeast(0),
                maxAmount = config.getInt("MaxAmount", 0).coerceAtLeast(0),
                delayTicks = config.getLong("Delay", 600L).coerceAtLeast(0L),
                priority = config.getInt("Priority", 0),
                removeWhenFarAway = config.getBoolean("RemoveWhenFarAway", true),
                trackingTag = buildTrackingTag(sourcePath, spawnerId)
            )
        }.onFailure { ex ->
            warning("[Spawners] [$sourcePath/$sectionName] 读取配置失败: ${ex.message}")
        }.getOrNull()
    }

    private fun readLevels(config: ConfigurationSection, sourcePath: String, sectionName: String): List<SpawnerLevelDefinition> {
        val rawLevel = config.get("Level")
        if (rawLevel is Number) {
            return listOf(SpawnerLevelDefinition(rawLevel.toInt().coerceAtLeast(1), 1.0))
        }
        val levelList = readStringList(config, "Level")
        if (levelList.isEmpty()) {
            return listOf(SpawnerLevelDefinition(1, 1.0))
        }
        val parsed = levelList.mapNotNull { entry ->
            val parts = entry.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (parts.size < 2) {
                warning("[Spawners] [$sourcePath/$sectionName] Level 条目格式无效: $entry")
                return@mapNotNull null
            }
            val level = parts[0].toIntOrNull()?.coerceAtLeast(1)
            val weight = parts[1].toDoubleOrNull()?.coerceAtLeast(0.0)
            if (level == null || weight == null || weight <= 0.0) {
                warning("[Spawners] [$sourcePath/$sectionName] Level 条目格式无效: $entry")
                return@mapNotNull null
            }
            SpawnerLevelDefinition(level, weight)
        }
        return if (parsed.isEmpty()) {
            listOf(SpawnerLevelDefinition(1, 1.0))
        } else {
            parsed
        }
    }

    private fun readStringList(config: ConfigurationSection, path: String): List<String> {
        val list = config.getStringList(path)
        if (list.isNotEmpty()) {
            return list
        }
        val single = config.getString(path)?.trim()
        return if (single.isNullOrBlank()) emptyList() else listOf(single)
    }

    private fun buildTrackingTag(sourcePath: String, spawnerId: String): String {
        val bytes = "$sourcePath#$spawnerId".toByteArray(StandardCharsets.UTF_8)
        return "phcore_spawner_${UUID.nameUUIDFromBytes(bytes).toString().replace("-", "")}"
    }
}

data class SpawnerDefinition(
    val sourcePath: String,
    val spawnerId: String,
    val enabled: Boolean,
    val types: List<String>,
    val chance: Double,
    val levels: List<SpawnerLevelDefinition>,
    val worlds: Set<String>,
    val intervalSeconds: Long,
    val spawnAmount: Int,
    val distance: Int,
    val maxAmount: Int,
    val delayTicks: Long,
    val priority: Int,
    val removeWhenFarAway: Boolean,
    val trackingTag: String,
) {

    val intervalTicks: Long
        get() = (intervalSeconds * 20L).coerceAtLeast(20L)

    fun matchesWorld(worldName: String): Boolean {
        return worlds.isEmpty() || worlds.contains(worldName.lowercase())
    }

    fun rollLevel(): Int {
        if (levels.isEmpty()) {
            return 1
        }
        if (levels.size == 1) {
            return levels.first().level
        }
        val totalWeight = levels.sumOf { it.weight }
        if (totalWeight <= 0.0) {
            return levels.first().level
        }
        var cursor = ThreadLocalRandom.current().nextDouble(totalWeight)
        levels.forEach { entry ->
            cursor -= entry.weight
            if (cursor <= 0.0) {
                return entry.level
            }
        }
        return levels.last().level
    }
}

data class SpawnerLevelDefinition(
    val level: Int,
    val weight: Double,
)
