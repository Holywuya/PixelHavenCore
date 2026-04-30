package com.pixlehavencore.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

object TextUtils {

    private val ampSerializer = LegacyComponentSerializer.legacyAmpersand()

    fun component(text: String): Component {
        return ampSerializer.deserialize(text)
    }

    fun components(lines: List<String>): List<Component> {
        return lines.map(::component)
    }
}
