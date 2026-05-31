package com.pixlehavencore.feature.realworld.foodcorrosion

import com.pixlehavencore.util.TextUtils
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object FoodCorrosionSettings {

    @Config("feature/realworld/food-corrosion.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set
    var defaultDays: Int = 14
        private set
    var expiredItem: String = "ROTTEN_FLESH"
        private set
    var loreFormat: String = "<gray>过期时间: {color}{days}d"
        private set
    var conversionMessage: String = "<yellow>你的食物已经完全腐烂了！"
        private set
    var excludedItems: Set<String> = setOf(
        "ROTTEN_FLESH",
        "GOLDEN_APPLE",
        "ENCHANTED_GOLDEN_APPLE",
        "POTION",
        "SUSPICIOUS_STEW",
    )
        private set
    var itemDays: Map<String, Int> = emptyMap()
        private set

    fun init() = reload()

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        defaultDays = config.getInt("default-days", 14).coerceIn(1, 365)
        expiredItem = config.getString("expired-item") ?: "ROTTEN_FLESH"
        loreFormat = (config.getString("lore-format") ?: loreFormat).let { TextUtils.translateLegacy(it) }
        conversionMessage = (config.getString("conversion-message") ?: conversionMessage).let { TextUtils.translateLegacy(it) }
        excludedItems = config.getStringList("excluded-items").toSet()
        itemDays = config.getConfigurationSection("item-days")
            ?.getKeys(false)
            ?.associateWith { config.getInt("item-days.$it", defaultDays) }
            ?: emptyMap()
    }
}
