package com.pixlehavencore.feature.craftingbench

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object CraftingBenchSettings {

    @Config("feature/crafting-bench/config.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    var language: String = "zh_CN"
        private set

    var debug: Boolean = false
        private set

    var blockMappings: Map<String, BenchBlockMapping> = emptyMap()
        private set

    var benchTiers: Map<String, BenchTier> = emptyMap()
        private set

    var specializations: List<CraftingSpecialization> = emptyList()
        private set

    var queueSettings: QueueSettings = QueueSettings(false, true, "drop", 0.7, 3, emptyList())
        private set

    // GUI
    var guiBorderItem: org.bukkit.Material = org.bukkit.Material.GRAY_STAINED_GLASS_PANE
        private set
    var guiBorderAccent: org.bukkit.Material = org.bukkit.Material.BLACK_STAINED_GLASS_PANE
        private set
    var guiCategorySlots: List<Int> = listOf(7, 17, 26, 35, 44)
        private set
    var guiRecipeStartSlot: Int = 10
        private set
    var guiPageSize: Int = 28
        private set
    var guiPrevPageSlot: Int = 52
        private set
    var guiNextPageSlot: Int = 53
        private set
    var guiInfoSlot: Int = 50
        private set
    var guiQueueStartSlot: Int = 46
        private set
    var guiQueueMax: Int = 4
        private set

    fun init() = reload()

    fun reload() {
        config.reload()
        enabled = config.getBoolean("settings.enabled", true)
        language = config.getString("settings.language") ?: "zh_CN"
        debug = config.getBoolean("settings.debug", false)
        benchTiers = loadBenchTiers()
        blockMappings = loadBlockMappings()
        specializations = loadSpecializations()
        queueSettings = QueueSettings(
            allowOfflineCrafting = config.getBoolean("queue.allow_offline_crafting", false),
            autoClaimOnline = config.getBoolean("queue.auto_claim_online", true),
            fullInventoryAction = (config.getString("queue.full_inventory_action") ?: "drop").trim().lowercase(),
            cancelRefundRatio = config.getDouble("queue.cancel_refund_ratio", 0.7).coerceIn(0.0, 1.0),
            defaultMaxQueueSize = config.getInt("queue.default_max_queue_size", 3).coerceAtLeast(1),
            permissionLimits = loadQueuePermissionLimits(),
        )
        guiBorderItem = resolveMaterial(config.getString("gui.border_item"), guiBorderItem)
        guiBorderAccent = resolveMaterial(config.getString("gui.border_accent"), guiBorderAccent)
        guiCategorySlots = config.getIntegerList("gui.category_slots").ifEmpty { guiCategorySlots }
        guiRecipeStartSlot = config.getInt("gui.recipe_start_slot", 10)
        guiPageSize = config.getInt("gui.page_size", 28).coerceIn(1, 28)
        guiPrevPageSlot = config.getInt("gui.prev_page_slot", 52)
        guiNextPageSlot = config.getInt("gui.next_page_slot", 53)
        guiInfoSlot = config.getInt("gui.info_slot", 50)
        guiQueueStartSlot = config.getInt("gui.queue_start_slot", 46)
        guiQueueMax = config.getInt("gui.queue_max", 4).coerceIn(1, 4)
    }

    fun getTier(tierId: String): BenchTier? {
        return benchTiers[tierId.trim()]
    }

    fun getTierIds(): List<String> {
        return benchTiers.keys.toList()
    }

    fun getTierByBlockId(blockId: String): BenchTier? {
        val mapping = blockMappings[blockId.trim().lowercase()] ?: return null
        return getTier(mapping.tierId)
    }

    fun getPrimaryBlockIdByTier(tierId: String): String? {
        return blockMappings.values
            .filter { it.tierId.equals(tierId.trim(), ignoreCase = false) }
            .map { it.blockId }
            .sorted()
            .firstOrNull()
    }

    fun canUseTier(playerPermissions: (String) -> Boolean, tier: BenchTier): Boolean {
        return tier.usePermission.isBlank() || playerPermissions(tier.usePermission)
    }

    fun isTierAllowed(requiredTier: String, currentTier: BenchTier): Boolean {
        val target = getTier(requiredTier) ?: return false
        return currentTier.rank >= target.rank
    }

    fun resolveQueueLimit(playerPermissions: (String) -> Boolean): Int {
        val matched = queueSettings.permissionLimits
            .sortedWith(compareByDescending<QueuePermissionLimit> { it.priority }.thenBy { it.id })
            .firstOrNull { it.permission.isBlank() || playerPermissions(it.permission) }
        return matched?.maxQueueSize ?: queueSettings.defaultMaxQueueSize
    }

    private fun loadBlockMappings(): Map<String, BenchBlockMapping> {
        val section = config.getConfigurationSection("craftengine_blocks") ?: return emptyMap()
        return section.getKeys(false).associate { key ->
            val tierId = section.getString("$key.tier")?.trim().orEmpty()
            key.trim().lowercase() to BenchBlockMapping(key.trim().lowercase(), tierId)
        }.filterValues { it.tierId.isNotBlank() }
    }

    private fun loadBenchTiers(): Map<String, BenchTier> {
        val section = config.getConfigurationSection("bench_tiers") ?: return emptyMap()
        val keys = section.getKeys(false).toList()
        return keys.associateWith { key ->
            BenchTier(
                id = key,
                displayName = section.getString("$key.display_name") ?: key,
                speedModifier = section.getDouble("$key.speed_modifier", 1.0).coerceAtLeast(0.1),
                usePermission = section.getString("$key.use_permission")?.trim().orEmpty(),
                rank = keys.indexOf(key)
            )
        }
    }

    private fun loadSpecializations(): List<CraftingSpecialization> {
        val section = config.getConfigurationSection("specializations") ?: return emptyList()
        return section.getKeys(false).map { key ->
            CraftingSpecialization(
                id = key,
                permission = section.getString("$key.permission")?.trim().orEmpty(),
                timeReduction = section.getDouble("$key.time_reduction", 0.0).coerceIn(0.0, 0.95),
                appliesTo = section.getStringList("$key.applies_to").map { it.trim() }.filter { it.isNotBlank() }.toSet(),
            )
        }.filter { it.permission.isNotBlank() && it.appliesTo.isNotEmpty() }
    }

    private fun resolveMaterial(name: String?, fallback: org.bukkit.Material): org.bukkit.Material {
        return com.pixlehavencore.util.ItemUtils.matchMaterial(name, fallback) ?: fallback
    }

    private fun loadQueuePermissionLimits(): List<QueuePermissionLimit> {
        val section = config.getConfigurationSection("queue.permission_limits") ?: return emptyList()
        val keys = section.getKeys(false).toList()
        return keys.map { key ->
            QueuePermissionLimit(
                id = key,
                permission = section.getString("$key.permission")?.trim().orEmpty(),
                maxQueueSize = section.getInt("$key.max_queue_size", queueSettings.defaultMaxQueueSize).coerceAtLeast(1),
                priority = section.getInt("$key.priority", keys.size - keys.indexOf(key)),
            )
        }
    }
}
