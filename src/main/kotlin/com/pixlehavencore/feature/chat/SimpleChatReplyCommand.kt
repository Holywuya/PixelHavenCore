package com.pixlehavencore.feature.chat

import com.pixlehavencore.util.msg
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand

@CommandHeader(name = "reply", aliases = ["r"], permissionDefault = PermissionDefault.TRUE)
object SimpleChatReplyCommand {

    @CommandBody
    val main = mainCommand {
        dynamic(comment = "message") {
            execute<ProxyCommandSender> { sender, _, argument ->
                val player = sender.cast<Player>() ?: run {
                    sender.msg("&c此命令仅玩家可用")
                    return@execute
                }
                val message = argument.toString().trim()
                val receiverId = SimpleChatState.lastMessageSender[player.uniqueId]
                if (receiverId == null) {
                    sender.msg(SimpleChatMessages.error("no_recent_message"))
                    return@execute
                }
                val receiver = Bukkit.getPlayer(receiverId)
                if (receiver == null || !receiver.isOnline) {
                    sender.msg(SimpleChatMessages.error("player_not_found", mapOf("{player}" to "最近私聊的玩家")))
                    return@execute
                }

                val toSender = SimpleChatService.formatPrivateToSender(player, receiver, message)
                val toReceiver = SimpleChatService.formatPrivateToReceiver(player, receiver, message)
                player.sendMessage(toSender)
                receiver.sendMessage(toReceiver)

                SimpleChatState.lastMessageSender.remove(player.uniqueId)
                SimpleChatState.lastMessageSender.remove(receiver.uniqueId)
            }
        }
    }
}
