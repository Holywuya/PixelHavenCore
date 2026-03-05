package com.pixlehavencore.feature.veinminer

import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePlayer
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand

@CommandHeader(name = "veinminer", aliases = ["vm"], permission = "veinminer.admin")
object VeinminerCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.sendMessage("PixleHavenCore: veinminer enabled=${VeinminerSettings.enabled}")
        }
    }

    @CommandBody
    val toggle = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            val newState = !VeinminerSettings.enabled
            VeinminerSettings.toggle(newState)
            if (newState) {
                val remaining = if (sender is taboolib.common.platform.ProxyPlayer) {
                    VeinminerLimitService.getRemaining(sender)
                } else {
                    0
                }
                VeinminerMessages.send(sender, VeinminerSettings.messageModeOn, mapOf("remaining" to remaining))
            } else {
                VeinminerMessages.send(sender, VeinminerSettings.messageModeOff)
            }
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            VeinminerSettings.reload()
            sender.msg("&a连锁挖掘配置已重载。")
        }
    }

    @CommandBody
    val limit = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            val player = sender.requirePlayer("Only players can use this command.") ?: return@execute
            val remaining = VeinminerLimitService.getRemaining(player)
            val limit = VeinminerLimitService.getLimitValue(player)
            VeinminerMessages.send(player, VeinminerSettings.messageLimitCommand, mapOf("remaining" to remaining, "limit" to limit))
        }
    }
}
