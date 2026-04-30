package com.pixlehavencore.feature.chat

import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePermission
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand

@CommandHeader(name = "chat", aliases = ["simplechat", "sc"], permissionDefault = PermissionDefault.TRUE)
object SimpleChatCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.msg("&6=== SimpleChat 命令帮助 ===")
            sender.msg("&b/chat reload &7- 重载聊天配置")
            sender.msg("&b/msg <玩家> <消息> &7- 发送私聊")
            sender.msg("&b/reply <消息> &7- 回复最近私聊")
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission(SimpleChatSettings.chatAdminPermission)) return@execute
            SimpleChatService.reload()
            sender.msg(SimpleChatMessages.get("command.reload.success"))
        }
    }

    @CommandBody
    val help = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.msg(SimpleChatMessages.get("command.help.usage"))
            sender.msg(SimpleChatMessages.get("command.help.commands"))
        }
    }

    @CommandBody
    val about = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.msg("&7&m--------------------")
            sender.msg("&dSimpleChat &7- &f简约、高效。")
            sender.msg("&7模块: &fPixleHavenCore")
            sender.msg("&7移植来源: &fMoeLuoYu/SimpleChat")
            sender.msg("&7&m--------------------")
        }
    }
}
