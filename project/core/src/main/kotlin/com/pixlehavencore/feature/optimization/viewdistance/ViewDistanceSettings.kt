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

    var displayOnJoin: Boolean = true
        private set

    var displayJoinMessage: String = "&7View distance set to &f{distance}&7."
        private set

    var afkEnterMessage: String = "&7你已进入 AFK 状态。"
        private set

    var afkExitMessage: String = "&a你已退出 AFK 状态。"
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

    var dynamicBypassPermission: String = "phcore.viewdistance.dynamic.bypass"
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

    var bypassAfkPermission: String = "phcore.viewdistance.afk.bypass"
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
        displayOnJoin = config.getBoolean("displayOnJoin", true)
        displayJoinMessage = config.getString("displayJoinMessage") ?: "&7View distance set to &f{distance}&7."
        afkEnterMessage = config.getString("afk.enterMessage") ?: "&7你已进入 AFK 状态。"
        afkExitMessage = config.getString("afk.exitMessage") ?: "&a你已退出 AFK 状态。"
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
        dynamicBypassPermission = config.getString("dynamic.bypassPermission") ?: "phcore.viewdistance.dynamic.bypass"
        pingEnabled = config.getBoolean("ping.enabled", false)
        pingIntervalTicks = config.getLong("ping.intervalTicks", 600L).coerceAtLeast(20L)
        pingMin = clampDistance(config.getInt("ping.min", 2))
        pingMax = clampDistance(config.getInt("ping.max", 32))
        pingMap = parseIntMap("ping.values")
        bypassAfkPermission = config.getString("afk.bypassPermission") ?: "phcore.viewdistance.afk.bypass"
    }

    fun clampDistance(value: Int): Int {
        return value.coerceIn(minDistance, maxDistance)
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
