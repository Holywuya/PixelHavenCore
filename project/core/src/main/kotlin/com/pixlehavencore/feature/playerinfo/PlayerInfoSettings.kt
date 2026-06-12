package com.pixlehavencore.feature.playerinfo

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object PlayerInfoSettings {

    @Config("feature/playerinfo.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    var dashboardTitle: String = "&8玩家信息 - {player}"
        private set

    var invTitle: String = "&8查看背包 - {player}"
        private set

    var ecTitle: String = "&8查看末影箱 - {player}"
        private set

    fun init() = reload()

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        dashboardTitle = config.getString("titles.dashboard") ?: "&8玩家信息 - {player}"
        invTitle = config.getString("titles.inventory") ?: "&8查看背包 - {player}"
        ecTitle = config.getString("titles.enderChest") ?: "&8查看末影箱 - {player}"
    }
}
