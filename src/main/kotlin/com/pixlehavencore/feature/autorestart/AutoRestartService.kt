package com.pixlehavencore.feature.autorestart

import com.pixlehavencore.util.broadcastColored
import org.bukkit.Bukkit
import taboolib.common.platform.function.onlinePlayers
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.warning

object AutoRestartService {

    private var autoRestartTask: Any? = null
    private var warningTasks = mutableListOf<Any?>()
    private var isRestarting = false

    fun init() {
        if (!AutoRestartSettings.enabled) {
            return
        }
        startAutoRestartTimer()
    }

    fun reload() {
        stopAllTasks()
        AutoRestartSettings.reload()
        init()
    }

    private fun startAutoRestartTimer() {
        if (!AutoRestartSettings.enabled) {
            return
        }

        stopAllTasks()

        val intervalTicks = AutoRestartSettings.autoRestartIntervalMinutes * 60 * 20L // 转换为ticks

        autoRestartTask = submit(delay = intervalTicks) {
            if (!AutoRestartSettings.enabled) return@submit
            performRestart()
        }

        // 设置警告通知
        AutoRestartSettings.restartWarningMinutes.forEach { minutes ->
            val warningTicks = intervalTicks - (minutes * 60 * 20L)
            if (warningTicks > 0) {
                val task = submit(delay = warningTicks) {
                    if (!AutoRestartSettings.enabled) return@submit
                    broadcastWarning(minutes)
                }
                warningTasks.add(task)
            }
        }
    }

    private fun broadcastWarning(minutes: Int) {
        val message = AutoRestartSettings.restartWarningMessage.replace("{time}", minutes.toString())
        broadcastColored(message)
    }

    fun performRestart() {
        if (isRestarting) return

        isRestarting = true
        stopAllTasks()

        // 广播重启消息
        broadcastColored(AutoRestartSettings.restartNowMessage)

        // 踢出所有玩家
        if (AutoRestartSettings.kickPlayersBeforeRestart) {
            onlinePlayers().forEach { player ->
                val bukkitPlayer = Bukkit.getPlayer(player.uniqueId)
                bukkitPlayer?.kickPlayer(AutoRestartSettings.kickMessage)
            }
        }

        // 延迟执行重启命令，给玩家踢出和消息显示时间
        submit(delay = 60L) { // 3秒后执行
            try {
                val command = AutoRestartSettings.restartCommand
                if (command == "stop") {
                    Bukkit.shutdown()
                } else {
                    // 执行自定义重启命令
                    val console = Bukkit.getServer().consoleSender
                    Bukkit.dispatchCommand(console, command)
                }
            } catch (e: Exception) {
                warning("执行重启命令失败: ${e.message}")
                // 备用方案：直接关闭服务器
                Bukkit.shutdown()
            }
        }
    }

    fun forceRestart(senderName: String? = null) {
        if (isRestarting) return

        stopAllTasks()

        // 广播强制重启消息
        val message = if (senderName != null) {
            "&c[服务器重启] &f管理员 &e$senderName &f强制重启服务器..."
        } else {
            AutoRestartSettings.restartForcedMessage
        }
        broadcastColored(message)

        // 立即执行重启
        submit(delay = 40L) { // 2秒后
            performRestart()
        }
    }

    fun cancelRestart(): Boolean {
        if (autoRestartTask == null) return false

        stopAllTasks()

        broadcastColored(AutoRestartSettings.restartCancelledMessage)
        return true
    }

    private fun stopAllTasks() {
        autoRestartTask = null
        warningTasks.clear()
    }

    fun getTimeUntilRestart(): Int? {
        // 这里可以返回距离下次重启的分钟数
        // 由于任务调度器的复杂性，暂时返回null表示未知
        return null
    }
}