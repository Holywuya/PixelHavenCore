package com.pixlehavencore.feature.base.firstjoin

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object FirstJoinSettings {

    @Config("feature/base/firstjoin.yml")
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

    var chunkLoadTimeoutSeconds: Double = 5.0
        private set

    var msgTeleported: String = "&a你被随机传送到 {x}, {y}, {z}"
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", false)
        centerX = config.getDouble("centerX", 0.0)
        centerZ = config.getDouble("centerZ", 0.0)
        minRadius = config.getDouble("minRadius", 50.0)
        maxRadius = config.getDouble("maxRadius", 500.0)
        safeLocationRetries = config.getInt("safeLocationRetries", 10)
        chunkLoadTimeoutSeconds = config.getDouble("chunkLoadTimeoutSeconds", 5.0).coerceAtLeast(1.0)
        msgTeleported = config.getString("msgTeleported") ?: "&a你被随机传送到 {x}, {y}, {z}"
    }
}
