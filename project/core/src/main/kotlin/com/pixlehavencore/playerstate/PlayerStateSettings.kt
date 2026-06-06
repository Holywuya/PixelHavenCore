package com.pixlehavencore.playerstate

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object PlayerStateSettings {

    @Config("feature/player-state.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
    }
}
