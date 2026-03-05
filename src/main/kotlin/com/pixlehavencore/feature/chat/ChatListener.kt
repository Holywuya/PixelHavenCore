package com.pixlehavencore.feature.chat

import org.bukkit.event.player.AsyncPlayerChatEvent
import taboolib.common.platform.ProxyPlayer
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.onlinePlayers
import taboolib.common.platform.function.submit
import taboolib.module.chat.colored
import taboolib.platform.compat.replacePlaceholder
import java.util.Locale
import java.util.regex.Matcher
import java.util.concurrent.ConcurrentHashMap

object ChatListener {

    @SubscribeEvent
    fun onChat(event: AsyncPlayerChatEvent) {
        if (!ChatSettings.enabled) {
            return
        }
        val player = event.player
        val rawMessage = event.message
        val mentionResult = if (ChatSettings.mentionEnabled) {
            findMentions(rawMessage)
        } else {
            MentionResult.empty()
        }
        var messageWithMentions = applyMentions(rawMessage, mentionResult, ChatSettings.mentionFormat)
        val allMentioned = ChatSettings.mentionAllEnabled && containsAllMention(rawMessage)
        if (allMentioned) {
            messageWithMentions = applyAllMention(messageWithMentions, ChatSettings.mentionAllFormat)
        }
        if (allMentioned && !player.hasPermission(ChatSettings.mentionAllPermission)) {
            player.sendMessage(ChatSettings.mentionAllNoPermissionMessage.colored())
            event.isCancelled = true
            return
        }
        val message = if (ChatSettings.allowColor && player.hasPermission(ChatSettings.colorPermission)) {
            messageWithMentions.colored()
        } else {
            messageWithMentions
        }
        var format = ChatSettings.format
            .replace("{player}", player.name)
            .replace("{displayname}", player.displayName)
            .replace("{world}", player.world.name)
        if (ChatSettings.usePlaceholder) {
            format = format.replacePlaceholder(player)
        }
        val formatted = format.colored().replace("{message}", message)
        event.format = formatted.replace("%", "%%")
        if (allMentioned) {
            if (!checkAllCooldown(player.uniqueId.toString())) {
                val remain = getAllCooldownRemaining(player.uniqueId.toString())
                player.sendMessage(ChatSettings.mentionAllCooldownMessage.replace("{seconds}", remain.toString()).colored())
                event.isCancelled = true
                return
            }
            setAllCooldown(player.uniqueId.toString())
            notifyAllMention(player.name, message)
        }
        notifyMentions(player.name, message, mentionResult)
    }

    private fun findMentions(message: String): MentionResult {
        val matcher = MENTION_PATTERN.matcher(message)
        if (!matcher.find()) {
            return MentionResult.empty()
        }
        val targets = LinkedHashMap<String, ProxyPlayer>()
        val replacements = LinkedHashMap<String, String>()
        val online = onlinePlayers()
        matcher.reset()
        while (matcher.find()) {
            val raw = matcher.group(1)
            val resolved = resolveMention(raw, online) ?: continue
            targets.putIfAbsent(resolved.name.lowercase(Locale.getDefault()), resolved)
            if (!raw.equals(resolved.name, true)) {
                replacements[raw.lowercase(Locale.getDefault())] = resolved.name
            }
        }
        return MentionResult(targets.values.toList(), replacements)
    }

    private fun applyMentions(message: String, result: MentionResult, format: String): String {
        if (result.mentions.isEmpty()) {
            return message
        }
        val matcher = MENTION_PATTERN.matcher(message)
        val sb = StringBuffer()
        while (matcher.find()) {
            val name = matcher.group(1)
            val target = result.mentions.firstOrNull { it.name.equals(name, true) }
            if (target == null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)))
                continue
            }
            val resolvedName = result.replacements[name.lowercase(Locale.getDefault())] ?: target.name
            val replaced = format.replace("{player}", resolvedName).colored()
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replaced))
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    private fun notifyMentions(senderName: String, message: String, result: MentionResult) {
        if (result.mentions.isEmpty()) {
            return
        }
        submit {
            result.mentions.forEach { target ->
                if (target.name.equals(senderName, true)) {
                    return@forEach
                }
                if (ChatMentionStorage.isOptOut(target)) {
                    return@forEach
                }
                val notifyMessage = ChatSettings.mentionNotifyMessage
                    .replace("{sender}", senderName)
                    .replace("{message}", message)
                    .colored()
                val actionBar = ChatSettings.mentionActionBar
                    .replace("{sender}", senderName)
                    .colored()
                val title = ChatSettings.mentionTitle
                    .replace("{sender}", senderName)
                    .replace("{message}", message)
                    .colored()
                val subtitle = ChatSettings.mentionSubtitle
                    .replace("{sender}", senderName)
                    .replace("{message}", message)
                    .colored()
                if (ChatSettings.mentionNotifyMessage.isNotBlank()) {
                    target.sendMessage(notifyMessage)
                }
                if (ChatSettings.mentionActionBar.isNotBlank()) {
                    target.sendActionBar(actionBar)
                }
                if (ChatSettings.mentionTitleEnabled && ChatSettings.mentionTitle.isNotBlank()) {
                    target.sendTitle(title, subtitle, ChatSettings.mentionTitleFadeIn, ChatSettings.mentionTitleStay, ChatSettings.mentionTitleFadeOut)
                }
            }
        }
    }

    private fun resolveMention(raw: String, online: List<ProxyPlayer>): ProxyPlayer? {
        val direct = online.firstOrNull { it.name.equals(raw, true) }
        if (direct != null) {
            return direct
        }
        if (!ChatSettings.mentionFuzzyEnabled) {
            return null
        }
        if (raw.length < ChatSettings.mentionFuzzyMinLength) {
            return null
        }
        val lowered = raw.lowercase(Locale.getDefault())
        val candidates = online.filter { player ->
            val name = player.name.lowercase(Locale.getDefault())
            if (ChatSettings.mentionFuzzyPrefix) {
                name.startsWith(lowered)
            } else {
                name.contains(lowered)
            }
        }
        return candidates.minByOrNull { it.name.length }
    }

    private fun notifyAllMention(senderName: String, message: String) {
        submit {
            onlinePlayers().forEach { target ->
                if (target.name.equals(senderName, true)) {
                    return@forEach
                }
                if (ChatMentionStorage.isOptOut(target)) {
                    return@forEach
                }
                val notifyMessage = ChatSettings.mentionAllNotifyMessage
                    .replace("{sender}", senderName)
                    .replace("{message}", message)
                    .colored()
                val actionBar = ChatSettings.mentionAllActionBar
                    .replace("{sender}", senderName)
                    .colored()
                if (ChatSettings.mentionAllNotifyMessage.isNotBlank()) {
                    target.sendMessage(notifyMessage)
                }
                if (ChatSettings.mentionAllActionBar.isNotBlank()) {
                    target.sendActionBar(actionBar)
                }
            }
        }
    }

    private fun containsAllMention(message: String): Boolean {
        return ALL_MENTION_PATTERN.matcher(message).find()
    }

    private fun applyAllMention(message: String, format: String): String {
        val replaced = format.colored()
        return ALL_MENTION_PATTERN.matcher(message).replaceAll(Matcher.quoteReplacement(replaced))
    }

    private fun checkAllCooldown(key: String): Boolean {
        if (ChatSettings.mentionAllCooldownSeconds <= 0) {
            return true
        }
        val now = System.currentTimeMillis()
        val next = allCooldowns[key] ?: 0L
        return now >= next
    }

    private fun setAllCooldown(key: String) {
        if (ChatSettings.mentionAllCooldownSeconds <= 0) {
            return
        }
        val now = System.currentTimeMillis()
        allCooldowns[key] = now + ChatSettings.mentionAllCooldownSeconds * 1000L
    }

    private fun getAllCooldownRemaining(key: String): Long {
        val now = System.currentTimeMillis()
        val next = allCooldowns[key] ?: 0L
        val remain = (next - now) / 1000L
        return if (remain < 0) 0 else remain
    }

    private val MENTION_PATTERN = Regex("(?i)(?<!\\w)@([A-Za-z0-9_]{3,16})").toPattern()
    private val ALL_MENTION_PATTERN = Regex("(?i)(?<!\\w)@all(?!\\w)").toPattern()
    private val allCooldowns = ConcurrentHashMap<String, Long>()

    private data class MentionResult(
        val mentions: List<ProxyPlayer>,
        val replacements: Map<String, String>
    ) {
        companion object {
            fun empty() = MentionResult(emptyList(), emptyMap())
        }
    }
}
