package com.pixlehavencore.feature.notification

import com.pixlehavencore.util.broadcastColored
import taboolib.module.chat.colored
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.info
import taboolib.common.platform.function.onlinePlayers
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.warning
import kotlin.random.Random

object NotificationService {

    private var autoNotificationTask: Any? = null
    private var isRunning = false

    fun init() {
        if (!NotificationSettings.enabled) {
            return
        }

        startAutoNotifications()

        if (NotificationSettings.autoNotificationsEnabled) {
            info("服务器通知模块已启用 - 自动通知间隔: ${NotificationSettings.autoNotificationsInterval}")
        }
    }

    fun reload() {
        stopAutoNotifications()
        NotificationSettings.reload()
        init()
    }

    fun startAutoNotifications() {
        if (!NotificationSettings.enabled || !NotificationSettings.autoNotificationsEnabled) {
            return
        }

        stopAutoNotifications()

        val intervalMillis = NotificationSettings.getAutoNotificationIntervalMillis()
        if (intervalMillis == null) {
            warning("无效的自动通知间隔格式: ${NotificationSettings.autoNotificationsInterval}")
            return
        }

        isRunning = true
        autoNotificationTask = submit(delay = intervalMillis / 50, period = intervalMillis / 50) {
            if (!isRunning) return@submit

            try {
                sendRandomAutoNotification()
            } catch (e: Exception) {
                warning("发送自动通知时发生错误: ${e.message}")
            }
        }
    }

    fun stopAutoNotifications() {
        isRunning = false
        autoNotificationTask = null
    }

    /**
     * 发送随机自动通知
     */
    private fun sendRandomAutoNotification() {
        if (!NotificationSettings.enabled || !NotificationSettings.autoNotificationsEnabled) {
            return
        }

        val messages = NotificationSettings.autoNotificationMessages
        if (messages.isEmpty()) {
            warning("自动通知消息列表为空")
            return
        }

        val randomMessage = messages[Random.nextInt(messages.size)]
        broadcastMessage(randomMessage)
    }

    /**
     * 发送管理员通知
     */
    fun sendAdminNotification(sender: Player, message: String) {
        if (!NotificationSettings.enabled || !NotificationSettings.adminNotificationsEnabled) {
            return
        }

        val formattedMessage = NotificationSettings.adminNotificationFormat
            .replace("{player}", sender.name)
            .replace("{message}", message)

        when (NotificationSettings.adminNotificationScope.uppercase()) {
            "ALL" -> {
                broadcastMessage(formattedMessage)
            }
            "WORLD" -> {
                val coloredMessage = formattedMessage.colored()
                sender.world.players.forEach { player -> player.sendMessage(coloredMessage) }
            }
            "RADIUS" -> {
                val center = sender.location
                val radius = NotificationSettings.adminNotificationRadius.toDouble()
                val radiusSquared = radius * radius
                val coloredMessage = formattedMessage.colored()
                sender.world.players.forEach { player ->
                    if (player.location.distanceSquared(center) <= radiusSquared) {
                        player.sendMessage(coloredMessage)
                    }
                }
            }
        }
    }

    /**
     * 发送服务器重启警告
     */
    fun sendRestartWarning(minutes: Int) {
        if (!NotificationSettings.enabled || !NotificationSettings.serverRestartNotificationEnabled) {
            return
        }

        val message = NotificationSettings.serverRestartNotificationFormat
            .replace("{minutes}", minutes.toString())

        broadcastMessage(message)
    }

    /**
     * 广播消息给所有玩家（委托给共享工具函数）
     */
    private fun broadcastMessage(message: String) = broadcastColored(message)

    @SubscribeEvent
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!NotificationSettings.enabled || !NotificationSettings.playerJoinNotificationEnabled) {
            return
        }

        val message = NotificationSettings.playerJoinNotificationFormat
            .replace("{player}", event.player.name)
            .replace("{online}", onlinePlayers().size.toString())

        broadcastMessage(message)
    }

    @SubscribeEvent
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (!NotificationSettings.enabled || !NotificationSettings.playerQuitNotificationEnabled) {
            return
        }

        val message = NotificationSettings.playerQuitNotificationFormat
            .replace("{player}", event.player.name)

        broadcastMessage(message)
    }
}
