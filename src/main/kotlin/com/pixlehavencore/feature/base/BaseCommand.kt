package com.pixlehavencore.feature.base

import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePlayer
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand

@CommandHeader(name = "killme", permissionDefault = PermissionDefault.TRUE)
object BaseCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!BaseCommandSettings.enabled) {
                sender.msg("&c基础模块已禁用。")
                return@execute
            }
            val player = sender.requirePlayer() ?: return@execute
            player.health = 0.0
            sender.msg(BaseCommandSettings.suicideMessage)
        }
    }
}
