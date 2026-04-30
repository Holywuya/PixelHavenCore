package com.pixlehavencore.feature.spawners

import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePermission
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand

@CommandHeader(name = "spawners", aliases = ["spawner"], permissionDefault = PermissionDefault.TRUE)
object SpawnerCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.msg("&6=== 刷怪模块帮助 ===")
            sender.msg("&b/spawners reload &7- 重载刷怪配置")
            sender.msg("&7当前状态：&f${if (SpawnerService.isEnabled()) "已启用" else "未启用"}")
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission("phcore.spawners.admin")) {
                return@execute
            }
            SpawnerService.reload()
            if (SpawnerService.isEnabled()) {
                sender.msg("&a刷怪配置已重载。")
            } else {
                sender.msg("&e刷怪配置已重载，但当前没有启用任何刷怪器。")
            }
        }
    }
}
