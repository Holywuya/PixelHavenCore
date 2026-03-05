package com.pixlehavencore.feature.autorestart

import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.info
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import java.io.File

object AutoRestartSettings {

    @Config("feature/auto-restart.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    // 自动重启间隔（分钟）
    var autoRestartIntervalMinutes: Int = 360  // 6小时
        private set

    // 重启前通知时间（分钟）
    var restartWarningMinutes: List<Int> = listOf(60, 30, 15, 5, 1)
        private set

    // 重启通知消息
    var restartWarningMessage: String = "&c[服务器重启] &f服务器将在 &e{time} &f分钟后自动重启！"
        private set

    var restartNowMessage: String = "&c[服务器重启] &f服务器正在重启..."
        private set

    var restartCancelledMessage: String = "&c[服务器重启] &f自动重启已被取消！"
        private set

    var restartForcedMessage: String = "&c[服务器重启] &f管理员强制重启服务器..."
        private set

    // 强制重启命令
    var restartCommand: String = "stop"
        private set

    // 是否在重启前踢出所有玩家
    var kickPlayersBeforeRestart: Boolean = true
        private set

    var kickMessage: String = "&c服务器正在重启，请稍后再来！"

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        autoRestartIntervalMinutes = config.getInt("auto-restart.interval-minutes", 360)
        restartWarningMinutes = config.getStringList("auto-restart.warning-minutes").map { it.toInt() }
        restartWarningMessage = config.getString("messages.warning") ?: "&c[服务器重启] &f服务器将在 &e{time} &f分钟后自动重启！"
        restartNowMessage = config.getString("messages.restarting") ?: "&c[服务器重启] &f服务器正在重启..."
        restartCancelledMessage = config.getString("messages.cancelled") ?: "&c[服务器重启] &f自动重启已被取消！"
        restartForcedMessage = config.getString("messages.forced") ?: "&c[服务器重启] &f管理员强制重启服务器..."
        restartCommand = config.getString("restart.command", "stop") ?: "stop"
        kickPlayersBeforeRestart = config.getBoolean("restart.kick-players", true)
        kickMessage = config.getString("messages.kick") ?: "&c服务器正在重启，请稍后再来！"
    }
}