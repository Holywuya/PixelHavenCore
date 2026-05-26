package com.pixlehavencore.feature.vanish

import com.pixlehavencore.PixleHavenSettings
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object VanishSettings {

    @Config("feature/vanish.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    // 消息
    var msgVanishOn: String = "&8[隐身] &7你已进入隐身模式。"
        private set
    var msgVanishOff: String = "&8[隐身] &7你已退出隐身模式。"
        private set
    var msgAdminNotifyOn: String = "&8[隐身] &7{player} &f已隐身。"
        private set
    var msgAdminNotifyOff: String = "&8[隐身] &7{player} &f已现身。"
        private set
    var msgNoPermission: String = "&c你没有权限执行该指令。"
        private set
    var msgPlayerNotFound: String = "&c找不到玩家 &7{player}&c。"
        private set
    var msgShowPlayer: String = "&8[隐身] &7{player} &f现在对你可见。"
        private set
    var msgShowAll: String = "&8[隐身] &7已显示所有普通隐身玩家。"
        private set
    var msgNoVanishedPlayers: String = "&7当前没有隐身玩家。"
        private set

    // 伪造消息
    var fakeJoinSendFakeQuit: Boolean = true
        private set
    var fakeQuitFormat: String = "&e{player} &7离开了游戏"
        private set
    var fakeQuitSilent: Boolean = true
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)

        msgVanishOn = config.getString("messages.vanishOn") ?: "&8[隐身] &7你已进入隐身模式。"
        msgVanishOff = config.getString("messages.vanishOff") ?: "&8[隐身] &7你已退出隐身模式。"
        msgAdminNotifyOn = config.getString("messages.adminNotifyOn") ?: "&8[隐身] &7{player} &f已隐身。"
        msgAdminNotifyOff = config.getString("messages.adminNotifyOff") ?: "&8[隐身] &7{player} &f已现身。"
        msgNoPermission = config.getString("messages.noPermission") ?: "&c你没有权限执行该指令。"
        msgPlayerNotFound = config.getString("messages.playerNotFound") ?: "&c找不到玩家 &7{player}&c。"
        msgShowPlayer = config.getString("messages.showPlayer") ?: "&8[隐身] &7{player} &f现在对你可见。"
        msgShowAll = config.getString("messages.showAll") ?: "&8[隐身] &7已显示所有普通隐身玩家。"
        msgNoVanishedPlayers = config.getString("messages.noVanishedPlayers") ?: "&7当前没有隐身玩家。"

        fakeJoinSendFakeQuit = config.getBoolean("fakeMessages.joinSendFakeQuit", true)
        fakeQuitFormat = config.getString("fakeMessages.fakeQuitFormat") ?: "&e{player} &7离开了游戏"
        fakeQuitSilent = config.getBoolean("fakeMessages.quitSilent", true)
    }
}
