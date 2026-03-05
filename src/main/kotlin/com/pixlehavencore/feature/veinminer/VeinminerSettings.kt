package com.pixlehavencore.feature.veinminer

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

    var allowedTools: Set<Material> = emptySet()
        private set

    var treeBlocks: Set<Material> = emptySet()
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

    var messageMoneyNotEnough: String = "&cNot enough money. Need {cost}, balance {balance}."
        private set

    var messageMoneyFailed: String = "&cCharge failed."
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
        allowedBlocks = parseMaterials(config.getStringList("allowedBlocks"))
        allowedTools = parseMaterials(config.getStringList("allowedTools"))
        treeBlocks = parseMaterials(config.getStringList("treeBlocks"))
        limitEnabled = config.getBoolean("limit.enabled", true)
        limitResetHour = config.getInt("limit.resetHour", 0).coerceIn(0, 23)
        limitResetMinute = config.getInt("limit.resetMinute", 0).coerceIn(0, 59)
        messageLimitCommand = config.getString("messages.limitCommand") ?: "&aRemaining veinminer: &f{remaining} &7/ &f{limit}"
        messageLimitDenied = config.getString("messages.limitDenied") ?: "&cYou have no remaining veinminer quota."
        messageLimitRemaining = config.getString("messages.limitRemaining") ?: "&7Remaining: &f{remaining}"
        messageModeOn = config.getString("messages.modeOn") ?: "&aVeinminer enabled. Remaining: &f{remaining}"
        messageModeOff = config.getString("messages.modeOff") ?: "&cVeinminer disabled."
        messageMoneyNotEnough = config.getString("messages.moneyNotEnough") ?: "&cNot enough money. Need {cost}, balance {balance}."
        messageMoneyFailed = config.getString("messages.moneyFailed") ?: "&cCharge failed."
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
        if (allowedBlocks.isEmpty()) {
            return true
        }
        if (allowedBlocks.contains(material)) {
            return true
        }
        return treeBlocks.contains(material)
    }

    fun isToolAllowed(material: Material): Boolean {
        return allowedTools.isEmpty() || allowedTools.contains(material)
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
            VeinminerGroup("default", "", 0, 64, 0.0)
        )
        val list = ArrayList<VeinminerGroup>()
        section.getKeys(false).forEach { key ->
            val node = section.getConfigurationSection(key) ?: return@forEach
            val permission = node.getString("permission") ?: ""
            val priority = node.getInt("priority", 0)
            val limit = node.getInt("limit", 64)
            val pricePerBlock = node.getDouble("pricePerBlock", 0.0)
            list.add(VeinminerGroup(key, permission, priority, limit, pricePerBlock))
        }
        if (list.isEmpty()) {
            list.add(VeinminerGroup("default", "", 0, 64, 0.0))
        }
        return list.sortedWith(compareByDescending<VeinminerGroup> { it.priority }.thenBy { it.id })
    }

    private fun parseMaterials(values: List<String>): Set<Material> {
        if (values.isEmpty()) {
            return emptySet()
        }
        if (values.any { it.trim() == "*" }) {
            return emptySet()
        }
        val materials = LinkedHashSet<Material>()
        values.forEach { raw ->
            val name = raw.trim()
            if (name.isEmpty()) {
                return@forEach
            }
            val normalized = if (name.contains(":")) name.substringAfter(":") else name
            val material = Material.matchMaterial(normalized.uppercase())
            if (material != null) {
                materials.add(material)
            }
        }
        return materials
    }
}

data class VeinminerGroup(
    val id: String,
    val permission: String,
    val priority: Int,
    val limit: Int,
    val pricePerBlock: Double,
)
