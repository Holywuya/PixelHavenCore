package com.pixlehavencore.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

object TextUtils {

    private val miniMessage = MiniMessage.miniMessage()

    /**
     * 解析含 MiniMessage 标签和/或 & 颜色码的文本为 Component。
     * 先将 &/§ 码翻译为 MiniMessage 标签，再用 MiniMessage 解析。
     * MiniMessage 解析失败时降级为 LegacyComponentSerializer。
     */
    fun parse(text: String): Component {
        if (text.isEmpty()) return Component.empty()
        val translated = translateLegacy(text)
        return runCatching {
            miniMessage.deserialize(translated)
        }.getOrElse {
            LegacyComponentSerializer.legacyAmpersand().deserialize(text)
        }
    }

    fun parseLore(lines: List<String>): List<Component> = lines.map(::parse)

    /**
     * 将 & 和 § 颜色码翻译为 MiniMessage 标签。
     * 提取自 SimpleChatComponentParser.translateLegacyFormattingToMiniMessage()。
     */
    internal fun translateLegacy(input: String): String {
        val builder = StringBuilder(input.length + 16)
        var index = 0
        while (index < input.length) {
            val current = input[index]
            if ((current == '&' || current == '§') && index + 1 < input.length) {
                val code = input[index + 1].lowercaseChar()
                val tag = when (code) {
                    '0' -> "<black>"
                    '1' -> "<dark_blue>"
                    '2' -> "<dark_green>"
                    '3' -> "<dark_aqua>"
                    '4' -> "<dark_red>"
                    '5' -> "<dark_purple>"
                    '6' -> "<gold>"
                    '7' -> "<gray>"
                    '8' -> "<dark_gray>"
                    '9' -> "<blue>"
                    'a' -> "<green>"
                    'b' -> "<aqua>"
                    'c' -> "<red>"
                    'd' -> "<light_purple>"
                    'e' -> "<yellow>"
                    'f' -> "<white>"
                    'k' -> "<obfuscated>"
                    'l' -> "<bold>"
                    'm' -> "<strikethrough>"
                    'n' -> "<underlined>"
                    'o' -> "<italic>"
                    'r' -> "<reset>"
                    else -> null
                }
                if (tag != null) {
                    builder.append(tag)
                    index += 2
                    continue
                }
            }
            builder.append(current)
            index++
        }
        return builder.toString()
    }
}
