package com.pixlehavencore.feature.chat

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import com.pixlehavencore.util.TextUtils

object SimpleChatMessageProcessor {

    private val urlPattern = Regex("(https?://)?([\\w-]+\\.)+[\\w-]+(/[\\w\\- ./?%&=]*)?")

    fun process(message: String): Component {
        if (!SimpleChatSettings.linkDetectionEnabled && !SimpleChatSettings.numberDetectionEnabled) {
            return TextUtils.parse(message)
        }

        var result = Component.empty()
        var cursor = 0

        val numberPattern = Regex("\\b\\d{${SimpleChatSettings.numberMinLength},${SimpleChatSettings.numberMaxLength}}\\b")
        val matches = mutableListOf<Match>()

        if (SimpleChatSettings.linkDetectionEnabled) {
            urlPattern.findAll(message).forEach { m -> matches += Match("url", m.range.first, m.range.last + 1, m.value) }
        }
        if (SimpleChatSettings.numberDetectionEnabled) {
            numberPattern.findAll(message).forEach { m -> matches += Match("num", m.range.first, m.range.last + 1, m.value) }
        }

        if (matches.isEmpty()) {
            return TextUtils.parse(message)
        }

        matches.sortBy { it.start }
        matches.forEach { m ->
            if (m.start < cursor) return@forEach
            if (cursor < m.start) {
                result = result.append(TextUtils.parse(message.substring(cursor, m.start)))
            }
            result = result.append(
                when (m.type) {
                    "url" -> urlComponent(m.value)
                    else -> numberComponent(m.value)
                }
            )
            cursor = m.end
        }

        if (cursor < message.length) {
            result = result.append(TextUtils.parse(message.substring(cursor)))
        }
        return result
    }

    private fun urlComponent(url: String): Component {
        val full = if (url.startsWith("http://", true) || url.startsWith("https://", true)) url else "https://$url"
        val text = SimpleChatMessages.button("link.text")
        val hover = SimpleChatMessages.button("link.hover", mapOf("{url}" to full))
        return SimpleChatComponentParser.parseRaw(text)
            .hoverEvent(HoverEvent.showText(SimpleChatComponentParser.parseRaw(hover)))
            .clickEvent(ClickEvent.openUrl(full))
    }

    private fun numberComponent(number: String): Component {
        val text = SimpleChatMessages.button("num.text", mapOf("{number}" to number))
        val hover = SimpleChatMessages.button("num.hover", mapOf("{number}" to number))
        return SimpleChatComponentParser.parseRaw(text)
            .hoverEvent(HoverEvent.showText(SimpleChatComponentParser.parseRaw(hover)))
            .clickEvent(ClickEvent.copyToClipboard(number))
    }

    private data class Match(
        val type: String,
        val start: Int,
        val end: Int,
        val value: String
    )
}
