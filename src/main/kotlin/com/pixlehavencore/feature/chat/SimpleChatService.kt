package com.pixlehavencore.feature.chat

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning

object SimpleChatService {

    private const val BANNER = "SimpleChat 模块已启用。"

    fun init() {
        SimpleChatSettings.init()
        SimpleChatMessages.init()
        SimpleChatPlaceholderService.reload()
        printBanner()

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null && !SimpleChatSettings.ignorePlaceholderApi) {
            warning("[SimpleChat] PlaceholderAPI 未找到，建议安装。")
        }

        SimpleChatRedisService.initAsync()
        info("[SimpleChat] 模块已启用。")
    }

    fun reload() {
        SimpleChatRedisService.shutdown()
        init()
        info("[SimpleChat] 配置已重载。")
    }

    fun shutdown() {
        SimpleChatRedisService.shutdown()
    }

    private fun printBanner() {
        info("[SimpleChat] $BANNER")
    }

    fun formatChatMessage(player: Player, rawMessage: String): net.kyori.adventure.text.Component {
        val prefix = SimpleChatComponentParser.parse(player, SimpleChatSettings.format)
        val name = SimpleChatComponentParser.parse(player, SimpleChatSettings.nameFormat)
        val separator = SimpleChatComponentParser.parseRaw(SimpleChatSettings.messageSeparator)
        val content = SimpleChatMessageProcessor.process(rawMessage)
        return prefix.append(net.kyori.adventure.text.Component.text(" ")).append(name).append(separator).append(content)
    }

    fun formatPrivateToSender(sender: Player, receiver: Player, message: String): net.kyori.adventure.text.Component {
        val format = SimpleChatPlaceholderService.applyPrivate(sender, receiver, SimpleChatSettings.privateSenderFormat)
        val prefix = SimpleChatComponentParser.parse(sender, format)
        return prefix.append(SimpleChatMessageProcessor.process(message))
    }

    fun formatPrivateToReceiver(sender: Player, receiver: Player, message: String): net.kyori.adventure.text.Component {
        val format = SimpleChatPlaceholderService.applyPrivate(sender, receiver, SimpleChatSettings.privateReceiverFormat)
        val prefix = SimpleChatComponentParser.parse(receiver, format)
        return prefix.append(SimpleChatMessageProcessor.process(message))
    }

    fun formatSayFromPlayer(sender: Player, message: String): net.kyori.adventure.text.Component {
        val format = SimpleChatPlaceholderService.applySay(sender, SimpleChatSettings.sayFormat)
        val prefix = SimpleChatComponentParser.parse(sender, format)
        return prefix.append(SimpleChatMessageProcessor.process(message))
    }

    fun formatSayFromConsole(senderName: String, message: String): net.kyori.adventure.text.Component {
        val prefixText = SimpleChatSettings.sayConsoleFormat.replace("%sender_name%", senderName)
        val prefix = SimpleChatComponentParser.parseRaw(prefixText)
        return prefix.append(SimpleChatMessageProcessor.process(message))
    }
}
