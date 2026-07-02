package com.pixlehavencore.feature.customcraft

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object CustomCraftSettings {

    @Config("feature/customcraft/config.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    var enableAutoDiscover: Boolean = true
        private set

    fun init() = reload()

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        enableAutoDiscover = config.getBoolean("enableAutoDiscover", true)
    }
}
