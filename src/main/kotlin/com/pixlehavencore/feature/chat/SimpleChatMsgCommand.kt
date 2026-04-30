package com.pixlehavencore.feature.chat

import com.pixlehavencore.util.msg
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.suggestPlayers

@CommandHeader(name = "msg", aliases = ["message", "tell", "w"], permissionDefault = PermissionDefault.TRUE)
object SimpleChatMsgCommand {

    @CommandBody
    val main = mainCommand {
        dynamic(comment = "player") {
            suggestPlayers()
            dynamic(comment = "message") {
                execute<ProxyCommandSender> { sender, context, argument ->
                    val player = sender.cast<Player>() ?: run {
                        sender.msg("&c此命令仅玩家可用")
                        return@execute
                    }
                    val receiverName = context.getOrNull("player") ?: return@execute
                    val receiver = Bukkit.getPlayerExact(receiverName)
                    if (receiver == null || !receiver.isOnline) {
                        sender.msg(SimpleChatMessages.error("player_not_found", mapOf("{player}" to receiverName)))
                        return@execute
                    }
                    val message = argument.toString().trim()
                    val toSender = SimpleChatService.formatPrivateToSender(player, receiver, message)
                    val toReceiver = SimpleChatService.formatPrivateToReceiver(player, receiver, message)
                    player.sendMessage(toSender)
                    receiver.sendMessage(toReceiver)
                    SimpleChatState.lastMessageSender[player.uniqueId] = receiver.uniqueId
                    SimpleChatState.lastMessageSender[receiver.uniqueId] = player.uniqueId
                }
            }
        }
    }
}
