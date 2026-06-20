package com.pixlehavencore.feature.grindstone

import com.pixlehavencore.util.ItemUtils
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import taboolib.library.configuration.ConfigurationSection
import kotlin.math.roundToInt

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

    var restorePercent: Double = 0.0
        private set

    var chance: Double = 1.0
        private set

    var messageSuccess: String = "&a修复了 +{amount} 耐久度。"
        private set

    var messageFailed: String = "&c修复失败。"
        private set

    private var repairRules: List<RepairRule> = emptyList()

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("grindstoneRepair.enabled", true)
        requireSneak = config.getBoolean("grindstoneRepair.require-sneak", false)
        permission = config.getString("grindstoneRepair.permission") ?: ""
        restorePerItem = config.getInt("grindstoneRepair.restore-per-item", 100).coerceAtLeast(0)
        restorePercent = config.getDouble("grindstoneRepair.restore-percent", 0.0).coerceAtLeast(0.0)
        chance = config.getDouble("grindstoneRepair.chance", 1.0).coerceIn(0.0, 1.0)
        messageSuccess = config.getString("grindstoneRepair.messages.message-success") ?: "&a修复了 +{amount} 耐久度。"
        messageFailed = config.getString("grindstoneRepair.messages.message-failed") ?: "&c修复失败。"
        repairRules = loadRules()
    }

    fun matchRule(mainItem: ItemStack, offhandItem: ItemStack): RepairRule? {
        return repairRules.firstOrNull { rule ->
            matchesSpec(rule.main, mainItem) && rule.materials.any { material -> matchesSpec(material.spec, offhandItem) }
        } ?: run {
            if (matchDefaultMaterial(mainItem, offhandItem)) {
                RepairRule(
                    main = mainItem.type.name,
                    materials = listOf(MaterialRule(spec = offhandItem.type.name, amount = 1)),
                    restore = RestoreRule(flat = restorePerItem, percent = restorePercent)
                )
            } else {
                null
            }
        }
    }

    fun resolveMatchedMaterial(rule: RepairRule, offhandItem: ItemStack): MaterialRule? {
        return rule.materials.firstOrNull { material -> matchesSpec(material.spec, offhandItem) }
    }

    fun calculateRestoreAmount(mainItem: ItemStack, rule: RepairRule): Int {
        val maxDurability = mainItem.type.maxDurability.toInt().coerceAtLeast(0)
        if (maxDurability <= 0) {
            return 0
        }
        val percentValue = (maxDurability * rule.restore.percent).roundToInt()
        return maxOf(rule.restore.flat, percentValue, 0)
    }

    private fun loadRules(): List<RepairRule> {
        val section = config.getConfigurationSection("grindstoneRepair.rules")
        if (section != null) {
            return section.getKeys(false).mapNotNull { key ->
                val node = section.getConfigurationSection(key) ?: return@mapNotNull null
                val main = node.getString("main")?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val materials = loadMaterials(node)
                if (materials.isEmpty()) {
                    return@mapNotNull null
                }
                val flat = node.getInt("restore", restorePerItem).coerceAtLeast(0)
                val percent = node.getDouble("restore-percent", restorePercent).coerceAtLeast(0.0)
                RepairRule(main = main, materials = materials, restore = RestoreRule(flat = flat, percent = percent))
            }
        }

        return emptyList()
    }

    private fun loadMaterials(node: ConfigurationSection): List<MaterialRule> {
        val materialsSection = node.getConfigurationSection("materials")
        if (materialsSection != null) {
            return materialsSection.getKeys(false).mapNotNull { key ->
                val materialNode = materialsSection.getConfigurationSection(key) ?: return@mapNotNull null
                val spec = materialNode.getString("spec")?.trim()?.takeIf { it.isNotBlank() }
                    ?: materialNode.getString("id")?.trim()?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val amount = materialNode.getInt("amount", 1).coerceAtLeast(1)
                MaterialRule(spec = spec, amount = amount)
            }
        }

        val list = node.getStringList("materials")
        if (list.isNotEmpty()) {
            return list.mapNotNull { entry ->
                val spec = entry.trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                MaterialRule(spec = spec, amount = 1)
            }
        }

        val single = node.getString("material")?.trim()?.takeIf { it.isNotBlank() }
        if (single != null) {
            val amount = node.getInt("material-amount", 1).coerceAtLeast(1)
            return listOf(MaterialRule(spec = single, amount = amount))
        }
        return emptyList()
    }

    private fun matchesSpec(spec: String, item: ItemStack): Boolean {
        if (item.type == Material.AIR) {
            return false
        }
        if (ItemUtils.looksLikeLibrarySpec(spec)) {
            return ItemUtils.matchesSpec(spec, item)
        }
        val material = ItemUtils.matchMaterial(spec) ?: return false
        return item.type == material
    }

    private fun matchDefaultMaterial(mainItem: ItemStack, offhandItem: ItemStack): Boolean {
        val itemType = mainItem.type
        val offhandType = offhandItem.type
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

    data class RepairRule(
        val main: String,
        val materials: List<MaterialRule>,
        val restore: RestoreRule
    )

    data class MaterialRule(
        val spec: String,
        val amount: Int
    )

    data class RestoreRule(
        val flat: Int,
        val percent: Double
    )
}
