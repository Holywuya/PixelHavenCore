package com.pixlehavencore.feature.autorestart

import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePermission
import com.pixlehavencore.util.requirePlayer
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand

// ---------------------------------------------------------------------------
// /autorestart — 自动重启模块主命令
// 权限: phcore.autorestart.admin
// ---------------------------------------------------------------------------
@CommandHeader(
    name = "autorestart",
    aliases = ["ar", "restart"],
    permission = "phcore.autorestart.admin"
)
object AutoRestartCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!AutoRestartSettings.enabled) {
                sender.msg("&c自动重启模块当前已禁用。")
                return@execute
            }
            sender.msg("&6=== 自动重启模块帮助 ===")
            sender.msg("&e/ar now &7- 立即重启服务器")
            sender.msg("&e/ar cancel &7- 取消下次自动重启")
            sender.msg("&e/ar reload &7- 重载配置")
            sender.msg("&e/ar status &7- 查看重启状态")
        }
    }

    @CommandBody
    val now = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission("phcore.autorestart.admin")) return@execute
            if (!AutoRestartSettings.enabled) {
                sender.msg("&c自动重启模块当前已禁用。")
                return@execute
            }

            val senderName = sender.requirePlayer()?.name ?: "控制台"
            AutoRestartService.forceRestart(senderName)
            sender.msg("&a已发起服务器重启...")
        }
    }

    @CommandBody
    val cancel = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission("phcore.autorestart.admin")) return@execute
            if (!AutoRestartSettings.enabled) {
                sender.msg("&c自动重启模块当前已禁用。")
                return@execute
            }

            if (AutoRestartService.cancelRestart()) {
                sender.msg("&a已取消下次自动重启。")
            } else {
                sender.msg("&c当前没有计划的自动重启。")
            }
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission("phcore.autorestart.admin")) return@execute
            AutoRestartService.reload()
            sender.msg("&a自动重启模块配置已重载。")
        }
    }

    @CommandBody
    val status = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!AutoRestartSettings.enabled) {
                sender.msg("&c自动重启模块当前已禁用。")
                return@execute
            }

            sender.msg("&6=== 自动重启状态 ===")
            sender.msg("&e启用状态: &a${AutoRestartSettings.enabled}")
            sender.msg("&e重启间隔: &b${AutoRestartSettings.autoRestartIntervalMinutes} &f分钟")
            sender.msg("&e警告时间: &b${AutoRestartSettings.restartWarningMinutes.joinToString(", ")} &f分钟")
            sender.msg("&e重启命令: &b${AutoRestartSettings.restartCommand}")
            sender.msg("&e踢出玩家: &b${AutoRestartSettings.kickPlayersBeforeRestart}")
        }
    }
}