package com.pixlehavencore.feature.security

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object SecuritySettings {

    @Config("feature/security.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    var adminPermission: String = "phcore.security.admin"
        private set

    var invTitle: String = "&8查看背包 - {player}"
        private set

    var ecTitle: String = "&8查看末影箱 - {player}"
        private set

    fun init() = reload()

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        adminPermission = config.getString("adminPermission") ?: "phcore.security.admin"
        invTitle = config.getString("titles.inventory") ?: "&8查看背包 - {player}"
        ecTitle = config.getString("titles.enderChest") ?: "&8查看末影箱 - {player}"
    }
}
