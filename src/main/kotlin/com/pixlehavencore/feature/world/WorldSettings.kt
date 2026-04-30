package com.pixlehavencore.feature.world

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object WorldSettings {

    @Config("feature/world.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    var preloadWorlds: List<String> = emptyList()
        private set

    var defaultWorld: String = "world"
        private set

    var allowUnlistedTeleport: Boolean = false
        private set

    var loadMissingWorlds: Boolean = false
        private set

    var adminPermission: String = "phcore.world.admin"
        private set

    var teleportSelfPermission: String = "phcore.world.teleport.self"
        private set

    var teleportOtherPermission: String = "phcore.world.teleport.other"
        private set

    var messageReloadSuccess: String = "&a世界模块配置已重载。"
        private set

    var messageWorldMissing: String = "&c世界未加载或未配置：&f{world}"
        private set

    var messageTeleportSelf: String = "&a已传送到世界 &f{world}"
        private set

    var messageTeleportOther: String = "&a已将 &f{player} &a传送到世界 &f{world}"
        private set

    var messageModuleDisabled: String = "&c世界模块未启用。"
        private set

    var messagePlayerOffline: String = "&c目标玩家不在线。"
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)

        val configuredWorlds = config.getStringList("worlds.preload")
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
        defaultWorld = config.getString("worlds.default")?.trim().orEmpty().ifBlank {
            configuredWorlds.firstOrNull() ?: "world"
        }
        preloadWorlds = if (configuredWorlds.isNotEmpty()) {
            configuredWorlds
        } else {
            listOf(defaultWorld)
        }
        allowUnlistedTeleport = config.getBoolean("worlds.allowUnlistedTeleport", false)
        loadMissingWorlds = config.getBoolean("worlds.loadMissing", false)
        adminPermission = config.getString("permissions.admin") ?: "phcore.world.admin"
        teleportSelfPermission = config.getString("permissions.teleportSelf") ?: "phcore.world.teleport.self"
        teleportOtherPermission = config.getString("permissions.teleportOther") ?: "phcore.world.teleport.other"
        messageReloadSuccess = config.getString("messages.reloadSuccess") ?: "&a世界模块配置已重载。"
        messageWorldMissing = config.getString("messages.worldMissing") ?: "&c世界未加载或未配置：&f{world}"
        messageTeleportSelf = config.getString("messages.teleportSelf") ?: "&a已传送到世界 &f{world}"
        messageTeleportOther = config.getString("messages.teleportOther") ?: "&a已将 &f{player} &a传送到世界 &f{world}"
        messageModuleDisabled = config.getString("messages.moduleDisabled") ?: "&c世界模块未启用。"
        messagePlayerOffline = config.getString("messages.playerOffline") ?: "&c目标玩家不在线。"
    }

    fun resolveWorldName(input: String): String? {
        val normalized = input.trim()
        if (normalized.isEmpty()) {
            return null
        }
        if (allowUnlistedTeleport) {
            return normalized
        }
        return allWorldNames().firstOrNull { it.equals(normalized, ignoreCase = true) }
    }

    fun isConfiguredWorld(worldName: String): Boolean {
        return allWorldNames().any { it.equals(worldName.trim(), ignoreCase = true) }
    }

    fun shouldLoadOnDemand(worldName: String): Boolean {
        return loadMissingWorlds || isConfiguredWorld(worldName)
    }

    fun allWorldNames(): List<String> {
        return (preloadWorlds + defaultWorld)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
}
