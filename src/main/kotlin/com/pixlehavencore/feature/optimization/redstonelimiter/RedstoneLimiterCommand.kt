package com.pixlehavencore.feature.optimization.redstonelimiter

import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePermission
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand

@CommandHeader(name = "redstonelimiter", aliases = ["rlimiter"], permissionDefault = PermissionDefault.TRUE)
object RedstoneLimiterCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.msg("&a/redstonelimiter reload &7- 重载红石限制配置")
            sender.msg("&a/redstonelimiter stats &7- 查看运行统计")
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission("phcore.redstonelimiter.admin")) return@execute
            RedstoneLimiterService.reload()
            sender.msg("&a红石限制配置已重载。")
        }
    }

    @CommandBody
    val stats = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission("phcore.redstonelimiter.admin")) return@execute
            val stats = RedstoneLimiterService.getStats()
            sender.msg("&e[红石限制] &7运行统计:")
            sender.msg("&7  累计阻断次数: &f${stats.totalBlocked}")
            sender.msg("&7  当前追踪点数: &f${stats.currentTracked}")
            sender.msg("&7  生效世界列表: &f${stats.enabledWorlds.joinToString(", ")}")
        }
    }
}
