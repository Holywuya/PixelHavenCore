package com.pixlehavencore.feature.base

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object FirstJoinSettings {

    @Config("feature/base-command.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = false
        private set

    var centerX: Double = 0.0
        private set

    var centerZ: Double = 0.0
        private set

    var minRadius: Double = 50.0
        private set

    var maxRadius: Double = 500.0
        private set

    var safeLocationRetries: Int = 10
        private set

    var msgTeleported: String = "&a你被随机传送到 {x}, {y}, {z}"
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("first-join.enabled", false)
        centerX = config.getDouble("first-join.centerX", 0.0)
        centerZ = config.getDouble("first-join.centerZ", 0.0)
        minRadius = config.getDouble("first-join.minRadius", 50.0)
        maxRadius = config.getDouble("first-join.maxRadius", 500.0)
        safeLocationRetries = config.getInt("first-join.safeLocationRetries", 10)
        msgTeleported = config.getString("first-join.msgTeleported") ?: "&a你被随机传送到 {x}, {y}, {z}"
    }
}
