package com.pixlehavencore.feature.chat

import org.bukkit.Bukkit
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.event.server.ServerCommandEvent
import com.pixlehavencore.util.broadcastComponent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit

object SimpleChatServerCommandListener {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onServerCommand(event: ServerCommandEvent) {
        if (!SimpleChatSettings.enabled) {
            return
        }
        val command = event.command.trim()
        if (!command.lowercase().startsWith("say ")) {
            return
        }
        val message = command.substringAfter(' ', "")
        event.isCancelled = true

        val sender = event.sender
        val rendered = if (sender is ConsoleCommandSender) {
            SimpleChatService.formatSayFromConsole("CONSOLE", message)
        } else {
            SimpleChatService.formatSayFromConsole(sender.name, message)
        }
        submit {
            broadcastComponent(rendered)
            Bukkit.getConsoleSender().sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(rendered))
        }
    }
}
