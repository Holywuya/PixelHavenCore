package com.pixlehavencore.feature.realworld.foodcorrosion

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object FoodCorrosionSettings {

    @Config("feature/realworld/food-corrosion.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set
    var maxCorrosion: Int = 100
        private set
    var defaultRate: Int = 1
        private set
    var totalDays: Int = 14
        private set
    var loreFormat: String = "&7过期时间: {color}{days}d"
        private set
    var conversionMessage: String = "&e你的食物已经完全腐烂了！"
        private set
    var excludedItems: Set<String> = setOf(
        "ROTTEN_FLESH",
        "GOLDEN_APPLE",
        "ENCHANTED_GOLDEN_APPLE",
        "POTION",
        "SUSPICIOUS_STEW",
    )
        private set
    var itemRates: Map<String, Int> = emptyMap()
        private set

    fun init() = reload()

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        maxCorrosion = config.getInt("max-corrosion", 100).coerceIn(1, 10000)
        defaultRate = config.getInt("default-rate", 1).coerceIn(1, 100)
        totalDays = config.getInt("total-days", 14).coerceIn(1, 365)
        loreFormat = config.getString("lore-format") ?: "&7过期时间: {color}{days}d"
        conversionMessage = config.getString("conversion-message") ?: "&e你的食物已经完全腐烂了！"
        excludedItems = config.getStringList("excluded-items").toSet()
        itemRates = config.getConfigurationSection("item-rates")
            ?.getKeys(false)
            ?.associateWith { config.getInt("item-rates.$it", defaultRate) }
            ?: emptyMap()
    }
}
