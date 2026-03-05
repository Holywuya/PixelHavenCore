package com.pixlehavencore.feature.veinminer

import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.function.adaptCommandSender
import taboolib.module.chat.colored

object VeinminerMessages {

    fun format(template: String, placeholders: Map<String, Any> = emptyMap()): String {
        var message = template
        placeholders.forEach { (key, value) ->
            message = message.replace("{$key}", value.toString())
        }
        return message.colored()
    }

    fun send(sender: Any, template: String, placeholders: Map<String, Any> = emptyMap()) {
        if (template.isBlank()) {
            return
        }
        val proxy = if (sender is ProxyCommandSender) sender else adaptCommandSender(sender)
        proxy.sendMessage(format(template, placeholders))
    }
}
