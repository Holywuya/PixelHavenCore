package com.pixlehavencore.feature.notification

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import java.util.concurrent.TimeUnit

object NotificationSettings {

    @Config("feature/notification.yml")
    lateinit var config: Configuration
        private set

    var enabled = true
        private set

    // 自动通知配置
    var autoNotificationsEnabled = true
        private set

    var autoNotificationsInterval = "5m"
        private set

    var autoNotificationMessages = listOf(
        "&6[服务器通知] &7欢迎来到我们的服务器！",
        "&6[服务器通知] &7请遵守服务器规则，祝您游戏愉快！",
        "&6[服务器通知] &7需要帮助？输入 &b/help &7查看指令列表"
    )
        private set

    // 管理员通知配置
    var adminNotificationsEnabled = true
        private set

    var adminNotificationPermission = "phcore.notify.admin"
        private set

    var adminNoPermissionMessage = "&c你没有权限发送服务器通知！"
        private set

    var adminNotificationFormat = "&c[管理员通知] &f{message}"
        private set

    var adminNotificationScope = "ALL"
        private set

    var adminNotificationRadius = 50
        private set

    // 特殊事件通知配置
    var playerJoinNotificationEnabled = true
        private set

    var playerJoinNotificationFormat = "&7欢迎 &b{player} &7加入服务器！当前在线玩家：&a{online}"
        private set

    var playerQuitNotificationEnabled = true
        private set

    var playerQuitNotificationFormat = "&7玩家 &b{player} &7离开了服务器"
        private set

    var serverRestartNotificationEnabled = true
        private set

    var serverRestartWarningMinutes = listOf(60, 30, 15, 5, 1)
        private set

    var serverRestartNotificationFormat = "&c[警告] &7服务器将在 &b{minutes} &7分钟后重启！"
        private set

    // 消息配置
    var messageAutoEnabled = "&a自动通知已启用"
        private set

    var messageAutoDisabled = "&c自动通知已禁用"
        private set

    var messageAutoStatus = "&7自动通知状态：&b{status}"
        private set

    var messageAdminSent = "&a通知已发送给所有玩家"
        private set

    var messageAdminSentWorld = "&a通知已发送给当前世界玩家"
        private set

    var messageAdminSentRadius = "&a通知已发送给半径 &b{radius} &a范围内的玩家"
        private set

    var messageInvalidTime = "&c无效的时间格式！请使用：数字+单位（如：30s, 5m, 1h, 1d）"
        private set

    var messageNoMessage = "&c请提供要发送的通知内容！"
        private set

    var messageReloadSuccess = "&a服务器通知模块配置已重载"
        private set

    fun setAutoNotificationsEnabled(value: Boolean) {
        autoNotificationsEnabled = value
        config["autoNotifications.enabled"] = value
        config.saveToFile()
    }

    fun setAutoNotificationsInterval(value: String) {
        autoNotificationsInterval = value
        config["autoNotifications.interval"] = value
        config.saveToFile()
    }

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        autoNotificationsEnabled = config.getBoolean("autoNotifications.enabled", true)
        autoNotificationsInterval = config.getString("autoNotifications.interval") ?: "5m"
        autoNotificationMessages = config.getStringList("autoNotifications.messages").ifEmpty {
            listOf(
                "&6[服务器通知] &7欢迎来到我们的服务器！",
                "&6[服务器通知] &7请遵守服务器规则，祝您游戏愉快！",
                "&6[服务器通知] &7需要帮助？输入 &b/help &7查看指令列表"
            )
        }
        adminNotificationsEnabled = config.getBoolean("adminNotifications.enabled", true)
        adminNotificationPermission = config.getString("adminNotifications.permission") ?: "phcore.notify.admin"
        adminNoPermissionMessage = config.getString("adminNotifications.noPermissionMessage") ?: "&c你没有权限发送服务器通知！"
        adminNotificationFormat = config.getString("adminNotifications.format") ?: "&c[管理员通知] &f{message}"
        adminNotificationScope = config.getString("adminNotifications.scope") ?: "ALL"
        adminNotificationRadius = config.getInt("adminNotifications.radius", 50).coerceAtLeast(0)
        playerJoinNotificationEnabled = config.getBoolean("eventNotifications.playerJoin.enabled", true)
        playerJoinNotificationFormat = config.getString("eventNotifications.playerJoin.format") ?: "&7欢迎 &b{player} &7加入服务器！当前在线玩家：&a{online}"
        playerQuitNotificationEnabled = config.getBoolean("eventNotifications.playerQuit.enabled", true)
        playerQuitNotificationFormat = config.getString("eventNotifications.playerQuit.format") ?: "&7玩家 &b{player} &7离开了服务器"
        serverRestartNotificationEnabled = config.getBoolean("eventNotifications.serverRestart.enabled", true)
        serverRestartWarningMinutes = config.getIntegerList("eventNotifications.serverRestart.warningMinutes").ifEmpty { listOf(60, 30, 15, 5, 1) }
        serverRestartNotificationFormat = config.getString("eventNotifications.serverRestart.format") ?: "&c[警告] &7服务器将在 &b{minutes} &7分钟后重启！"
        messageAutoEnabled = config.getString("messages.autoEnabled") ?: "&a自动通知已启用"
        messageAutoDisabled = config.getString("messages.autoDisabled") ?: "&c自动通知已禁用"
        messageAutoStatus = config.getString("messages.autoStatus") ?: "&7自动通知状态：&b{status}"
        messageAdminSent = config.getString("messages.adminSent") ?: "&a通知已发送给所有玩家"
        messageAdminSentWorld = config.getString("messages.adminSentWorld") ?: "&a通知已发送给当前世界玩家"
        messageAdminSentRadius = config.getString("messages.adminSentRadius") ?: "&a通知已发送给半径 &b{radius} &a范围内的玩家"
        messageInvalidTime = config.getString("messages.invalidTime") ?: "&c无效的时间格式！请使用：数字+单位（如：30s, 5m, 1h, 1d）"
        messageNoMessage = config.getString("messages.noMessage") ?: "&c请提供要发送的通知内容！"
        messageReloadSuccess = config.getString("messages.reloadSuccess") ?: "&a服务器通知模块配置已重载"
    }

    /**
     * 解析时间间隔字符串，返回毫秒数
     * 支持格式：30s (30秒), 5m (5分钟), 1h (1小时), 1d (1天)
     */
    fun parseTimeInterval(interval: String): Long? {
        val regex = Regex("^(\\d+)([smhd])$")
        val match = regex.matchEntire(interval.lowercase()) ?: return null

        val value = match.groupValues[1].toLong()
        val unit = match.groupValues[2]

        return when (unit) {
            "s" -> TimeUnit.SECONDS.toMillis(value)
            "m" -> TimeUnit.MINUTES.toMillis(value)
            "h" -> TimeUnit.HOURS.toMillis(value)
            "d" -> TimeUnit.DAYS.toMillis(value)
            else -> null
        }
    }

    /**
     * 获取自动通知间隔的毫秒数
     */
    fun getAutoNotificationIntervalMillis(): Long? {
        return parseTimeInterval(autoNotificationsInterval)
    }
}
