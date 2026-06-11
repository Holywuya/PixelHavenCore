package com.pixlehavencore.feature.flight

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

data class FlightGroup(
    val name: String,
    val permission: String,
    val dailySeconds: Int
)

object FlightSettings {

    @Config("feature/flight.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = false
        private set

    var enabledWorlds: Set<String> = emptySet()
        private set

    var dailyResetTime: String = "00:00"
        private set

    var displayMode: String = "actionbar"
        private set

    var groups: List<FlightGroup> = emptyList()
        private set

    // 消息
    var msgModuleDisabled: String = "&c飞行模块当前已禁用。"
        private set
    var msgFlightOn: String = "&a飞行已开启。"
        private set
    var msgFlightOff: String = "&e飞行已关闭。"
        private set
    var msgNoTime: String = "&c飞行时间已用尽，将在每日重置后恢复。"
        private set
    var msgActionBar: String = "&b飞行剩余: &f{time}"
        private set
    var msgCheckResult: String = "&7玩家 &f{player} &7剩余飞行时间: &b{time} &7(每日: &f{daily}&7秒, 永久额外: &f{permanent_bonus}&7秒)"
        private set
    var msgPlayerNotFound: String = "&c找不到在线玩家 &7{player}&c。"
        private set
    var msgInvalidTime: String = "&c无效时间格式。支持: 30m, 1h, 1h30m, 3600"
        private set
    var msgAdminSet: String = "&a已设置 &f{player} &a飞行时间为 &b{time}&a。"
        private set
    var msgAdminAdd: String = "&a已为 &f{player} &a增加 &b{time}&a。"
        private set
    var msgAdminReset: String = "&a已重置 &f{player} &a飞行时间。"
        private set
    var msgReloadSuccess: String = "&a飞行模块已重载。"
        private set
    var msgTimeExpired: String = "&c飞行时间已用尽！"
        private set
    var msgDailyReset: String = "&a每日飞行时间已重置。"
        private set
    var msgDominionBlocked: String = "&c当前领地禁止飞行。"

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", false)
        enabledWorlds = config.getStringList("enabled-worlds").toSet()
        dailyResetTime = config.getString("daily-reset-time") ?: "00:00"
        displayMode = config.getString("display.mode") ?: "actionbar"

        groups = parseGroups()

        msgModuleDisabled = config.getString("messages.module-disabled") ?: msgModuleDisabled
        msgFlightOn = config.getString("messages.flight-on") ?: msgFlightOn
        msgFlightOff = config.getString("messages.flight-off") ?: msgFlightOff
        msgNoTime = config.getString("messages.no-time") ?: msgNoTime
        msgActionBar = config.getString("messages.actionbar") ?: msgActionBar
        msgCheckResult = config.getString("messages.check-result") ?: msgCheckResult
        msgPlayerNotFound = config.getString("messages.player-not-found") ?: msgPlayerNotFound
        msgInvalidTime = config.getString("messages.invalid-time") ?: msgInvalidTime
        msgAdminSet = config.getString("messages.admin-set") ?: msgAdminSet
        msgAdminAdd = config.getString("messages.admin-add") ?: msgAdminAdd
        msgAdminReset = config.getString("messages.admin-reset") ?: msgAdminReset
        msgReloadSuccess = config.getString("messages.reload-success") ?: msgReloadSuccess
        msgTimeExpired = config.getString("messages.time-expired") ?: msgTimeExpired
        msgDailyReset = config.getString("messages.daily-reset") ?: msgDailyReset
        msgDominionBlocked = config.getString("messages.dominion-blocked") ?: msgDominionBlocked
    }

    private fun parseGroups(): List<FlightGroup> {
        val section = config.getConfigurationSection("groups") ?: return emptyList()
        return section.getKeys(false).mapNotNull { key ->
            val perm = section.getString("$key.permission") ?: return@mapNotNull null
            val seconds = section.getInt("$key.daily-seconds", 0)
            FlightGroup(key, perm, seconds.coerceAtLeast(0))
        }
    }
}
