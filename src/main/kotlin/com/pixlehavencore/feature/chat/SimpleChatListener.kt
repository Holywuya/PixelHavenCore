package com.pixlehavencore.feature.chat

import com.pixlehavencore.util.broadcastComponent
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import taboolib.common.platform.ProxyPlayer
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.onlinePlayers
import taboolib.platform.util.submit as submitOnEntity

object SimpleChatListener {

    @SubscribeEvent
    fun onPlayerChat(event: AsyncChatEvent) {
        if (event.isCancelled) return
        if (!SimpleChatSettings.enabled || !SimpleChatSettings.chatFormatEnabled) {
            return
        }

        val sender = event.player
        val raw = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.message())

        event.isCancelled = true
        sender.submitOnEntity {
            val mentionTargets = linkedSetOf<ProxyPlayer>()
            val processed = if (SimpleChatSettings.atEnabled && SimpleChatSettings.chatMentionEnabled) {
                processAtMentions(raw, mentionTargets)
            } else {
                raw
            }

            val rendered = SimpleChatService.formatChatMessage(sender, processed)
            val serialized = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(rendered)

            // Folia: broadcastComponent 内部已正确使用 submitOnEntity 调度，可直接调用
            broadcastComponent(rendered)
            // Folia: 控制台消息通过全局区域调度器发送
            taboolib.common.platform.function.submit {
                Bukkit.getConsoleSender().sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(rendered))
            }

            if (mentionTargets.isNotEmpty()) {
                mentionTargets.forEach { target ->
                    playAtSound(target)
                }
            }

            SimpleChatRedisService.publishIfEnabled(sender.name, serialized)
        }
    }

    private fun processAtMentions(message: String, mentionedPlayers: MutableSet<ProxyPlayer>): String {
        val regex = Regex("@([\\w\\u4e00-\\u9fa5]+)")
        var result = message
        regex.findAll(message).forEach { match ->
            val atText = match.groupValues.getOrNull(1) ?: return@forEach
            onlinePlayers().firstOrNull { p -> atText.startsWith(p.name, ignoreCase = true) }?.let { target ->
                mentionedPlayers += target
                val formatted = SimpleChatSettings.atFormat
                    .replace("%player_name%", target.name)
                result = result.replace("@$atText", formatted + " " + atText.removePrefix(target.name))
            }
        }
        return result
    }

    private fun playAtSound(player: ProxyPlayer) {
        if (!SimpleChatSettings.atSoundEnabled) {
            return
        }
        // Folia: 直接通过 ProxyPlayer 获取 Bukkit Player 并在实体区域线程上执行
        val bukkit = player.cast<org.bukkit.entity.Player>() ?: return
        bukkit.submitOnEntity {
            val now = System.currentTimeMillis()
            val cooldown = SimpleChatSettings.atSoundCooldownSeconds * 1000L
            val last = SimpleChatState.atSoundCooldowns[bukkit.uniqueId] ?: 0L
            if (cooldown > 0L && now - last < cooldown) {
                return@submitOnEntity
            }
            SimpleChatState.atSoundCooldowns[bukkit.uniqueId] = now

            val sound = RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.SOUND_EVENT)
                .get(NamespacedKey.minecraft(SimpleChatSettings.atSoundType.lowercase()))
                ?: return@submitOnEntity
            bukkit.playSound(bukkit.location, sound, 1f, 1f)
        }
    }

    fun handleCrossServerMessage(component: Component, senderServerId: String) {
        broadcastComponent(component)
        if (SimpleChatSettings.redisLogOtherServers) {
            val plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component)
            // Folia: 控制台消息通过全局区域调度器发送
            taboolib.common.platform.function.submit {
                Bukkit.getConsoleSender().sendMessage("[Redis Chat Channel][$senderServerId] $plain")
            }
        }
    }
}
