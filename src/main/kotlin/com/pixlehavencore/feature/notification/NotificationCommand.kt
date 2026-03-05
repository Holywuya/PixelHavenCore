package com.pixlehavencore.feature.notification

import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePlayer
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand

@CommandHeader(name = "notification", aliases = ["notify", "servernotify"], permission = "phcore.notify.admin")
object NotificationCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.msg("&6=== 服务器通知命令帮助 ===")
            sender.msg("&b/notify send <消息> &7- 发送管理员通知")
            sender.msg("&b/notify auto on &7- 启用自动通知")
            sender.msg("&b/notify auto off &7- 禁用自动通知")
            sender.msg("&b/notify auto status &7- 查看自动通知状态")
            sender.msg("&b/notify auto interval <时间> &7- 设置自动通知间隔")
            sender.msg("&b/notify reload &7- 重载配置")
            sender.msg("&b/notify test &7- 发送测试通知")
            sender.msg("&7时间格式: 30s(30秒), 5m(5分钟), 1h(1小时), 1d(1天)")
        }
    }

    @CommandBody
    val send = subCommand {
        execute<ProxyCommandSender> { sender, _, argument ->
            if (!NotificationSettings.enabled || !NotificationSettings.adminNotificationsEnabled) {
                sender.msg("&c服务器通知功能已禁用")
                return@execute
            }

            val player = sender.requirePlayer() ?: return@execute

            val raw = argument.toString().trim()
            val stripped = if (raw.startsWith("send ", ignoreCase = true)) {
                raw.substring(5).trim()
            } else {
                raw
            }
            val message = if (stripped.equals("send", ignoreCase = true)) "" else stripped
            if (message.isBlank()) {
                sender.msg(NotificationSettings.messageNoMessage)
                return@execute
            }

            NotificationService.sendAdminNotification(player.cast(), message)
            sender.msg("&a通知已发送！")
        }
    }

    @CommandBody
    val auto = subCommand {
        literal("on") {
            execute<ProxyCommandSender> { sender, _, _ ->
                if (!NotificationSettings.enabled) {
                    sender.msg("&c服务器通知功能已禁用")
                    return@execute
                }

                NotificationSettings.setAutoNotificationsEnabled(true)
                NotificationService.startAutoNotifications()
                sender.msg(NotificationSettings.messageAutoEnabled)
            }
        }

        literal("off") {
            execute<ProxyCommandSender> { sender, _, _ ->
                NotificationSettings.setAutoNotificationsEnabled(false)
                NotificationService.stopAutoNotifications()
                sender.msg(NotificationSettings.messageAutoDisabled)
            }
        }

        literal("status") {
            execute<ProxyCommandSender> { sender, _, _ ->
                val status = if (NotificationSettings.autoNotificationsEnabled) "启用" else "禁用"
                sender.msg(NotificationSettings.messageAutoStatus.replace("{status}", status))
            }
        }

        literal("interval") {
            execute<ProxyCommandSender> { sender, _, argument ->
                val interval = argument.toString()
                val parsedInterval = NotificationSettings.parseTimeInterval(interval)
                if (parsedInterval == null) {
                    sender.msg(NotificationSettings.messageInvalidTime)
                    return@execute
                }

                NotificationSettings.setAutoNotificationsInterval(interval)
                NotificationService.startAutoNotifications()
                sender.msg("&a自动通知间隔已设置为: &b$interval")
            }
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            NotificationService.reload()
            sender.msg(NotificationSettings.messageReloadSuccess)
        }
    }

    @CommandBody
    val test = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            val player = sender.requirePlayer() ?: return@execute
            NotificationService.sendAdminNotification(player.cast(), "&e这是一条测试通知消息")
            sender.msg("&a测试通知已发送！")
        }
    }
}
