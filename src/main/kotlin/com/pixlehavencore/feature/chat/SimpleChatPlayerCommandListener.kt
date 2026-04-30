package com.pixlehavencore.feature.chat

import org.bukkit.Bukkit
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import com.pixlehavencore.util.broadcastComponent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit

object SimpleChatPlayerCommandListener {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onPlayerCommandPreprocess(event: PlayerCommandPreprocessEvent) {
        if (!SimpleChatSettings.enabled) {
            return
        }

        val sender = event.player
        val input = event.message.trim()
        val lower = input.lowercase()

        if (lower.startsWith("/say ")) {
            val message = input.substringAfter(' ', "")
            event.isCancelled = true
            val rendered = SimpleChatService.formatSayFromPlayer(sender, message)
            submit {
                broadcastComponent(rendered)
                Bukkit.getConsoleSender().sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(rendered))
            }
            return
        }

        val privateRegex = Regex("^/(msg|tell|w)\\s+(\\S+)\\s+(.+)$", RegexOption.IGNORE_CASE)
        val privateMatch = privateRegex.find(input)
        if (privateMatch != null) {
            val receiverName = privateMatch.groupValues[2]
            val message = privateMatch.groupValues[3]
            val receiver = Bukkit.getPlayerExact(receiverName)
            if (receiver == null || !receiver.isOnline) {
                sender.sendMessage(SimpleChatMessages.error("player_not_found", mapOf("{player}" to receiverName)))
                event.isCancelled = true
                return
            }
            event.isCancelled = true
            sendPrivateMessage(sender, receiver, message)
            SimpleChatState.lastMessageSender[sender.uniqueId] = receiver.uniqueId
            SimpleChatState.lastMessageSender[receiver.uniqueId] = sender.uniqueId
            return
        }

        val replyRegex = Regex("^/(reply|r)\\s+(.+)$", RegexOption.IGNORE_CASE)
        val replyMatch = replyRegex.find(input)
        if (replyMatch != null) {
            event.isCancelled = true
            val message = replyMatch.groupValues[2]
            val last = SimpleChatState.lastMessageSender[sender.uniqueId]
            if (last == null) {
                sender.sendMessage(SimpleChatMessages.error("no_recent_message"))
                return
            }
            val receiver = Bukkit.getPlayer(last)
            if (receiver == null || !receiver.isOnline) {
                sender.sendMessage(SimpleChatMessages.error("player_not_found", mapOf("{player}" to "最近私聊的玩家")))
                return
            }
            sendPrivateMessage(sender, receiver, message)
            SimpleChatState.lastMessageSender.remove(sender.uniqueId)
            SimpleChatState.lastMessageSender.remove(receiver.uniqueId)
        }
    }

    private fun sendPrivateMessage(sender: org.bukkit.entity.Player, receiver: org.bukkit.entity.Player, message: String) {
        val toSender = SimpleChatService.formatPrivateToSender(sender, receiver, message)
        val toReceiver = SimpleChatService.formatPrivateToReceiver(sender, receiver, message)
        sender.sendMessage(toSender)
        receiver.sendMessage(toReceiver)
    }
}
