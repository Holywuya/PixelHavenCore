package com.pixlehavencore.feature.chat

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object SimpleChatMessages {

    @Config("feature/chat/chat-messages.yml")
    private lateinit var config: Configuration

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
    }

    fun get(path: String, replacements: Map<String, String> = emptyMap()): String {
        var text = config.getString(path) ?: path
        replacements.forEach { (key, value) ->
            text = text.replace(key, value)
        }
        return text
    }

    fun button(path: String, replacements: Map<String, String> = emptyMap()): String {
        return get("buttons.$path", replacements)
    }

    fun error(path: String, replacements: Map<String, String> = emptyMap()): String {
        return get("errors.$path", replacements)
    }
}
