package com.pixlehavencore.feature.base.killme

import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePlayer
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand

@CommandHeader(name = "killme", permissionDefault = PermissionDefault.TRUE)
object KillmeCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!KillmeSettings.enabled) {
                sender.msg("<red>killme 功能已禁用。")
                return@execute
            }
            val player = sender.requirePlayer() ?: return@execute
            player.health = 0.0
            sender.msg(KillmeSettings.suicideMessage)
        }
    }
}
