package com.pixlehavencore.feature.optimization.entityclearer

import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePermission
import com.pixlehavencore.util.requirePlayer
import org.bukkit.entity.Player
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand

@CommandHeader(name = "entityclearer", aliases = ["eclear"], permissionDefault = PermissionDefault.TRUE)
object EntityClearerCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.msg("&6=== 实体清理命令帮助 ===")
            sender.msg("&b/entityclearer reload &7- 重载实体清理配置")
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission("phcore.admin")) return@execute
            EntityClearerService.reload()
            sender.msg("&a实体清理配置已重载。")
        }
    }

}
