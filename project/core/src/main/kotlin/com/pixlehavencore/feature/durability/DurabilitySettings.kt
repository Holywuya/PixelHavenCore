package com.pixlehavencore.feature.durability

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object DurabilitySettings {

    @Config("feature/durability.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    var loreFormat: String = "&7耐久: &a{current}&7/&c{max}"
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        loreFormat = config.getString("loreFormat") ?: "&7耐久: &a{current}&7/&c{max}"
    }
}
