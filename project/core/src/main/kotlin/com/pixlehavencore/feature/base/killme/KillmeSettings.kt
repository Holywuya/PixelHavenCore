package com.pixlehavencore.feature.base.killme

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object KillmeSettings {

    @Config("feature/base/killme.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    var suicideMessage: String = "&c你已自杀。"
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        suicideMessage = config.getString("suicideMessage") ?: "&c你已自杀。"
    }
}
