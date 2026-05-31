package com.pixlehavencore.util

import com.pixlehavencore.bridge.TextBridge
import net.kyori.adventure.text.Component

object TextUtils {

    /**
     * 解析含 & 颜色码的文本为 Component，委托 TextBridge 处理。
     */
    fun parse(text: String): Component = TextBridge.fromAmpersand(text)

    /**
     * 解析 MiniMessage 标签文本为 Component。
     */
    fun parseMiniMessage(text: String): Component = TextBridge.fromMiniMessage(text)

    fun parseLore(lines: List<String>): List<Component> = lines.map(::parse)

    /**
     * 解析物品显示名称，显式禁用斜体。
     */
    fun parseItem(text: String): Component =
        TextBridge.fromAmpersand(text).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)

    /**
     * 解析物品 Lore，显式禁用斜体。
     */
    fun parseItemLore(lines: List<String>): List<Component> = lines.map(::parseItem)

    /**
     * 将 & 和 § 颜色码翻译为 MiniMessage 标签。
     * 提取自 SimpleChatComponentParser.translateLegacyFormattingToMiniMessage()。
     */
    fun translateLegacy(input: String): String {
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
