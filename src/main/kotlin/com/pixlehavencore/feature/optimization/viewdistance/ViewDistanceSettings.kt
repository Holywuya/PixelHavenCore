package com.pixlehavencore.feature.optimization.viewdistance

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object ViewDistanceSettings {

    @Config("feature/optimization/view-distance-controller.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = false
        private set

    var syncSimulationDistance: Boolean = true
        private set

    var defaultDistance: Int = 10
        private set

    var maxDistance: Int = 32
        private set

    var minDistance: Int = 2
        private set

    var savePlayerData: Boolean = true
        private set

    var displayOnJoin: Boolean = true
        private set

    var displayJoinMessage: String = "&7View distance set to &f{distance}&7."
        private set

    var afkEnabled: Boolean = true
        private set

    var afkOnJoin: Boolean = false
        private set

    var afkSeconds: Int = 60
        private set

    var afkDistance: Int = 2
        private set

    var spectatorsCanAfk: Boolean = true
        private set

    var dynamicEnabled: Boolean = false
        private set

    var dynamicIntervalTicks: Long = 1200L
        private set

    var dynamicMin: Int = 2
        private set

    var dynamicMax: Int = 32
        private set

    var dynamicMsptMap: Map<Int, Int> = emptyMap()
        private set

    var dynamicBypassPermission: String = "phcore.vdc.dynamic.bypass"
        private set

    var pingEnabled: Boolean = false
        private set

    var pingIntervalTicks: Long = 600L
        private set

    var pingMin: Int = 2
        private set

    var pingMax: Int = 32
        private set

    var pingMap: Map<Int, Int> = emptyMap()
        private set

    var pingTogglePermission: String = "phcore.vdc.ping.toggle"
        private set

    var bypassAfkPermission: String = "phcore.vdc.afk.bypass"
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", false)
        syncSimulationDistance = config.getBoolean("syncSimulationDistance", true)
        defaultDistance = clampDistance(config.getInt("defaultDistance", 10))
        maxDistance = clampDistance(config.getInt("maxDistance", 32))
        minDistance = clampDistance(config.getInt("minDistance", 2))
        savePlayerData = config.getBoolean("savePlayerData", true)
        displayOnJoin = config.getBoolean("displayOnJoin", true)
        displayJoinMessage = config.getString("displayJoinMessage") ?: "&7View distance set to &f{distance}&7."
        afkEnabled = config.getBoolean("afk.enabled", true)
        afkOnJoin = config.getBoolean("afk.afkOnJoin", false)
        afkSeconds = config.getInt("afk.seconds", 60).coerceAtLeast(5)
        afkDistance = clampDistance(config.getInt("afk.distance", 2))
        spectatorsCanAfk = config.getBoolean("afk.spectatorsCanAfk", true)
        dynamicEnabled = config.getBoolean("dynamic.enabled", false)
        dynamicIntervalTicks = config.getLong("dynamic.intervalTicks", 1200L).coerceAtLeast(20L)
        dynamicMin = clampDistance(config.getInt("dynamic.min", 2))
        dynamicMax = clampDistance(config.getInt("dynamic.max", 32))
        dynamicMsptMap = parseIntMap("dynamic.mspt")
        dynamicBypassPermission = config.getString("dynamic.bypassPermission") ?: "phcore.vdc.dynamic.bypass"
        pingEnabled = config.getBoolean("ping.enabled", false)
        pingIntervalTicks = config.getLong("ping.intervalTicks", 600L).coerceAtLeast(20L)
        pingMin = clampDistance(config.getInt("ping.min", 2))
        pingMax = clampDistance(config.getInt("ping.max", 32))
        pingMap = parseIntMap("ping.values")
        pingTogglePermission = config.getString("ping.togglePermission") ?: "phcore.vdc.ping.toggle"
        bypassAfkPermission = config.getString("afk.bypassPermission") ?: "phcore.vdc.afk.bypass"
    }

    fun clampDistance(value: Int): Int {
        return value.coerceIn(2, 32)
    }

    private fun parseIntMap(path: String): Map<Int, Int> {
        val section = config.getConfigurationSection(path) ?: return emptyMap()
        val map = LinkedHashMap<Int, Int>()
        section.getKeys(false).forEach { key ->
            val threshold = key.toIntOrNull() ?: return@forEach
            val reduce = section.getInt(key, 0)
            map[threshold] = reduce
        }
        return map.toSortedMap()
    }
}
