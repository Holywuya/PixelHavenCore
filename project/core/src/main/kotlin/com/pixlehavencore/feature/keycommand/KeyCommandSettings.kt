package com.pixlehavencore.feature.keycommand

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object KeyCommandSettings {

    @Config("feature/key-command.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    var commandF: String = ""
        private set

    var commandShiftF: String = ""
        private set

    var commandCtrlF: String = ""
        private set

    var commandAltF: String = ""
        private set

    var cooldownMillis: Long = 500L
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        commandF = config.getString("f")?.trim().orEmpty()
        commandShiftF = config.getString("shiftF")?.trim().orEmpty()
        commandCtrlF = config.getString("ctrlF")?.trim().orEmpty()
        commandAltF = config.getString("altF")?.trim().orEmpty()
        cooldownMillis = config.getLong("cooldownMillis", 500L).coerceAtLeast(0L)
    }
}
