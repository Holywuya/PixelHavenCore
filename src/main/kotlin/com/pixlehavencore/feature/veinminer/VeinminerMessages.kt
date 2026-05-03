package com.pixlehavencore.feature.veinminer

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.function.adaptCommandSender
import com.pixlehavencore.util.TextUtils

object VeinminerMessages {

    fun format(template: String, placeholders: Map<String, Any> = emptyMap()): Component {
        var message = template
        placeholders.forEach { (key, value) ->
            message = message.replace("{$key}", value.toString())
        }
        return TextUtils.parse(message)
    }

    fun send(sender: Any, template: String, placeholders: Map<String, Any> = emptyMap()) {
        if (template.isBlank()) {
            return
        }
        val proxy = if (sender is ProxyCommandSender) sender else adaptCommandSender(sender)
        proxy.sendMessage(LegacyComponentSerializer.legacySection().serialize(format(template, placeholders)))
    }
}
