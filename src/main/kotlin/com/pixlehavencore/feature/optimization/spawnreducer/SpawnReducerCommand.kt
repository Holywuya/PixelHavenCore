package com.pixlehavencore.feature.optimization.spawnreducer

import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePermission
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand

@CommandHeader(name = "spawnreducer", aliases = ["sreduce"], permissionDefault = PermissionDefault.TRUE)
object SpawnReducerCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.msg("&6=== 自然生成削减命令帮助 ===")
            sender.msg("&b/spawnreducer reload &7- 重载自然生成削减配置")
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission("phcore.admin")) return@execute
            SpawnReducerService.reload()
            sender.msg("&a自然生成削减配置已重载。")
        }
    }
}
