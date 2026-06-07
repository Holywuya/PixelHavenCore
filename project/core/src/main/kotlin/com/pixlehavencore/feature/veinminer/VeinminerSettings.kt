package com.pixlehavencore.feature.veinminer

import com.pixlehavencore.util.ItemUtils
import org.bukkit.Material
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object VeinminerSettings {

    @Config("feature/veinminer.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    var maxChain: Int = 64
        private set

    var searchRadius: Int = 1
        private set

    var mustSneak: Boolean = false
        private set

    var cooldownTicks: Int = 20
        private set

    var needCorrectTool: Boolean = true
        private set

    var permissionRestricted: Boolean = false
        private set

    var mergeItemDrops: Boolean = false
        private set

    var durabilityDecrease: Boolean = true
        private set

    var allowedBlocks: Set<Material> = emptySet()
        private set

    var allowedBlockPatterns: List<String> = emptyList()
        private set

    var allowedTools: Set<Material> = emptySet()
        private set

    var allowedToolPatterns: List<String> = emptyList()
        private set

    var treeBlocks: Set<Material> = emptySet()
        private set

    var treeBlockPatterns: List<String> = emptyList()
        private set

    var limitEnabled: Boolean = true
        private set

    var limitResetHour: Int = 0
        private set

    var limitResetMinute: Int = 0
        private set

    var messageLimitCommand: String = "&aRemaining veinminer: &f{remaining} &7/ &f{limit}"
        private set

    var messageLimitDenied: String = "&cYou have no remaining veinminer quota."
        private set

    var messageLimitRemaining: String = "&7Remaining: &f{remaining}"
        private set

    var messageModeOn: String = "&aVeinminer enabled. Remaining: &f{remaining}"
        private set

    var messageModeOff: String = "&cVeinminer disabled."
        private set

    var groups: List<VeinminerGroup> = emptyList()
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        maxChain = config.getInt("maxChain", 64)
        searchRadius = config.getInt("searchRadius", 1)
        mustSneak = config.getBoolean("mustSneak", false)
        cooldownTicks = config.getInt("cooldown", 20)
        needCorrectTool = config.getBoolean("needCorrectTool", true)
        permissionRestricted = config.getBoolean("permissionRestricted", false)
        mergeItemDrops = config.getBoolean("mergeItemDrops", false)
        durabilityDecrease = config.getBoolean("durabilityDecrease", true)
        val allowedBlocksParse = parseMaterials(config.getStringList("allowedBlocks"))
        allowedBlocks = allowedBlocksParse.materials
        allowedBlockPatterns = allowedBlocksParse.patterns

        val allowedToolsParse = parseMaterials(config.getStringList("allowedTools"))
        allowedTools = allowedToolsParse.materials
        allowedToolPatterns = allowedToolsParse.patterns

        val treeBlocksParse = parseMaterials(config.getStringList("treeBlocks"))
        treeBlocks = treeBlocksParse.materials
        treeBlockPatterns = treeBlocksParse.patterns
        limitEnabled = config.getBoolean("limit.enabled", true)
        limitResetHour = config.getInt("limit.resetHour", 0).coerceIn(0, 23)
        limitResetMinute = config.getInt("limit.resetMinute", 0).coerceIn(0, 59)
        messageLimitCommand = config.getString("messages.limitCommand") ?: "&aRemaining veinminer: &f{remaining} &7/ &f{limit}"
        messageLimitDenied = config.getString("messages.limitDenied") ?: "&cYou have no remaining veinminer quota."
        messageLimitRemaining = config.getString("messages.limitRemaining") ?: "&7Remaining: &f{remaining}"
        messageModeOn = config.getString("messages.modeOn") ?: "&aVeinminer enabled. Remaining: &f{remaining}"
        messageModeOff = config.getString("messages.modeOff") ?: "&cVeinminer disabled."
        groups = loadGroups()
        VeinminerLimitService.updateResetSchedule()
    }

    fun toggle(value: Boolean) {
        enabled = value
        config["enabled"] = value
        config.saveToFile()
    }

    fun cooldownMillis(): Long {
        return cooldownTicks.toLong() * 50L
    }

    fun isBlockAllowed(material: Material): Boolean {
        if (allowedBlocks.isEmpty() && allowedBlockPatterns.isEmpty() && treeBlocks.isEmpty() && treeBlockPatterns.isEmpty()) {
            return true
        }
        if (allowedBlocks.contains(material) || treeBlocks.contains(material)) {
            return true
        }
        val name = material.name.lowercase()
        return allowedBlockPatterns.any { matchesPattern(name, it) } || treeBlockPatterns.any { matchesPattern(name, it) }
    }

    fun isToolAllowed(material: Material): Boolean {
        if (allowedTools.isEmpty() && allowedToolPatterns.isEmpty()) return true
        if (allowedTools.contains(material)) return true
        val name = material.name.lowercase()
        return allowedToolPatterns.any { matchesPattern(name, it) }
    }

    fun getMatchedPattern(material: Material): String? {
        val name = material.name.lowercase()
        allowedBlockPatterns.firstOrNull { matchesPattern(name, it) }?.let { return it }
        treeBlockPatterns.firstOrNull { matchesPattern(name, it) }?.let { return it }
        return null
    }

    fun matchesMaterialPattern(material: Material, pattern: String): Boolean {
        val regex = pattern.replace("*", ".*").toRegex()
        return regex.matches(material.name.lowercase())
    }

    private fun matchesPattern(name: String, pattern: String): Boolean {
        val regex = pattern.replace("*", ".*").toRegex()
        return regex.matches(name)
    }

    fun getOreType(material: Material): String? {
        val name = material.name.lowercase()
        if (!name.endsWith("_ore")) {
            return null
        }
        val base = name.removeSuffix("_ore")
        if (base.startsWith("deepslate_")) {
            return base.removePrefix("deepslate_")
        }
        return base
    }

    private fun loadGroups(): List<VeinminerGroup> {
        val section = config.getConfigurationSection("groups") ?: return listOf(
            VeinminerGroup("default", "", 0, 64)
        )
        val list = ArrayList<VeinminerGroup>()
        section.getKeys(false).forEach { key ->
            val node = section.getConfigurationSection(key) ?: return@forEach
            val permission = node.getString("permission") ?: ""
            val priority = node.getInt("priority", 0)
            val limit = node.getInt("limit", 64)
            list.add(VeinminerGroup(key, permission, priority, limit))
        }
        if (list.isEmpty()) {
            list.add(VeinminerGroup("default", "", 0, 64))
        }
        return list.sortedWith(compareByDescending<VeinminerGroup> { it.priority }.thenBy { it.id })
    }

    private data class ParsedMaterials(val materials: Set<Material>, val patterns: List<String>)

    private fun parseMaterials(values: List<String>): ParsedMaterials {
        if (values.isEmpty()) {
            return ParsedMaterials(emptySet(), emptyList())
        }
        val materials = LinkedHashSet<Material>()
        val patterns = ArrayList<String>()
        values.forEach { raw ->
            val name = raw.trim()
            if (name.isEmpty()) {
                return@forEach
            }
            if (name == "*") {
                patterns.add("*")
                return@forEach
            }
            if (name.contains("*")) {
                patterns.add(name.lowercase())
                return@forEach
            }
            val normalized = if (name.contains(":")) name.substringAfter(":") else name
            val material = ItemUtils.matchMaterial(normalized)
            if (material != null) {
                materials.add(material)
            }
        }
        return ParsedMaterials(materials, patterns)
    }
}

data class VeinminerGroup(
    val id: String,
    val permission: String,
    val priority: Int,
    val limit: Int,
)
