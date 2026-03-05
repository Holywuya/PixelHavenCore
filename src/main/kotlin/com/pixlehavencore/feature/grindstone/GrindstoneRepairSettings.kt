package com.pixlehavencore.feature.grindstone

import org.bukkit.Material
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object GrindstoneRepairSettings {

    @Config("feature/grindstone-repair.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    var requireSneak: Boolean = false
        private set

    var permission: String = ""
        private set

    var restorePerItem: Int = 100
        private set

    var chance: Double = 1.0
        private set

    var messageSuccess: String = "&aRepaired +{amount} durability."
        private set

    var messageFailed: String = "&cRepair failed."
        private set

    var materialOverrides: Map<Material, Material> = emptyMap()
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("grindstoneRepair.enabled", true)
        requireSneak = config.getBoolean("grindstoneRepair.requireSneak", false)
        permission = config.getString("grindstoneRepair.permission") ?: ""
        restorePerItem = config.getInt("grindstoneRepair.restorePerItem", 100)
        chance = config.getDouble("grindstoneRepair.chance", 1.0).coerceIn(0.0, 1.0)
        messageSuccess = config.getString("grindstoneRepair.messages.success") ?: "&aRepaired +{amount} durability."
        messageFailed = config.getString("grindstoneRepair.messages.failed") ?: "&cRepair failed."
        materialOverrides = loadOverrides()
    }

    fun matchMaterial(itemType: Material, offhandType: Material): Boolean {
        val override = materialOverrides[itemType]
        if (override != null) {
            return override == offhandType
        }
        val name = itemType.name
        return when {
            name.contains("NETHERITE") -> offhandType == Material.NETHERITE_INGOT
            name.contains("DIAMOND") -> offhandType == Material.DIAMOND
            name.contains("GOLDEN") || name.contains("GOLD") -> offhandType == Material.GOLD_INGOT
            name.contains("IRON") -> offhandType == Material.IRON_INGOT
            name.contains("STONE") -> offhandType == Material.COBBLESTONE
            name.contains("WOODEN") -> offhandType.name.endsWith("_PLANKS")
            name.contains("LEATHER") -> offhandType == Material.LEATHER
            itemType == Material.ELYTRA -> offhandType == Material.PHANTOM_MEMBRANE
            itemType == Material.TRIDENT -> offhandType == Material.PRISMARINE_SHARD
            itemType == Material.SHIELD -> offhandType.name.endsWith("_PLANKS")
            itemType == Material.BOW || itemType == Material.CROSSBOW || itemType == Material.FISHING_ROD -> offhandType == Material.STRING
            itemType == Material.FLINT_AND_STEEL || itemType == Material.SHEARS -> offhandType == Material.IRON_INGOT
            else -> false
        }
    }

    private fun loadOverrides(): Map<Material, Material> {
        val section = config.getConfigurationSection("grindstoneRepair.materialOverrides") ?: return emptyMap()
        val map = LinkedHashMap<Material, Material>()
        section.getKeys(false).forEach { key ->
            val from = Material.matchMaterial(key.uppercase()) ?: return@forEach
            val targetName = section.getString(key)?.uppercase() ?: return@forEach
            val to = Material.matchMaterial(targetName) ?: return@forEach
            map[from] = to
        }
        return map
    }
}
