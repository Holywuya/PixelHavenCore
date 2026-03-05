package com.pixlehavencore.feature.chat

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object ChatSettings {

    @Config("feature/chat.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    var format: String = "&7[{world}] &f{player}: &r{message}"
        private set

    var allowColor: Boolean = false
        private set

    var colorPermission: String = "phcore.chat.color"
        private set

    var usePlaceholder: Boolean = true
        private set

    var mentionEnabled: Boolean = true
        private set

    var mentionFormat: String = "&b@{player}&r"
        private set

    var mentionNotifyMessage: String = "&eYou were mentioned by {sender}: {message}"
        private set

    var mentionActionBar: String = "&eMentioned by {sender}"
        private set

    var mentionTitleEnabled: Boolean = false
        private set

    var mentionTitle: String = "&e你被 {sender} 提及"
        private set

    var mentionSubtitle: String = "{message}"
        private set

    var mentionTitleFadeIn: Int = 10
        private set

    var mentionTitleStay: Int = 40
        private set

    var mentionTitleFadeOut: Int = 10
        private set

    var mentionFuzzyEnabled: Boolean = true
        private set

    var mentionFuzzyMinLength: Int = 3
        private set

    var mentionFuzzyPrefix: Boolean = true
        private set

    var mentionAllEnabled: Boolean = true
        private set

    var mentionAllPermission: String = "phcore.chat.mention.all"
        private set

    var mentionAllFormat: String = "&c@ALL&r"
        private set

    var mentionAllNotifyMessage: String = "&e{sender} mentioned everyone: {message}"
        private set

    var mentionAllActionBar: String = "&eMentioned by {sender}"
        private set

    var mentionAllCooldownSeconds: Int = 60
        private set

    var mentionAllCooldownMessage: String = "&cYou must wait {seconds}s before using @all again."
        private set

    var mentionAllNoPermissionMessage: String = "&cYou don't have permission to use @all."
        private set

    var mentionToggleOnMessage: String = "&7You will now receive @mentions."
        private set

    var mentionToggleOffMessage: String = "&7You will no longer receive @mentions."
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        format = config.getString("format") ?: "&7[{world}] &f{player}: &r{message}"
        allowColor = config.getBoolean("allowColor", false)
        colorPermission = config.getString("colorPermission") ?: "phcore.chat.color"
        usePlaceholder = config.getBoolean("usePlaceholder", true)
        mentionEnabled = config.getBoolean("mention.enabled", true)
        mentionFormat = config.getString("mention.format") ?: "&b@{player}&r"
        mentionNotifyMessage = config.getString("mention.notifyMessage") ?: "&eYou were mentioned by {sender}: {message}"
        mentionActionBar = config.getString("mention.actionBar") ?: "&eMentioned by {sender}"
        mentionTitleEnabled = config.getBoolean("mention.title.enabled", false)
        mentionTitle = config.getString("mention.title.title") ?: "&e你被 {sender} 提及"
        mentionSubtitle = config.getString("mention.title.subtitle") ?: "{message}"
        mentionTitleFadeIn = config.getInt("mention.title.fadeIn", 10).coerceAtLeast(0)
        mentionTitleStay = config.getInt("mention.title.stay", 40).coerceAtLeast(0)
        mentionTitleFadeOut = config.getInt("mention.title.fadeOut", 10).coerceAtLeast(0)
        mentionFuzzyEnabled = config.getBoolean("mention.fuzzy.enabled", true)
        mentionFuzzyMinLength = config.getInt("mention.fuzzy.minLength", 3).coerceAtLeast(1)
        mentionFuzzyPrefix = config.getBoolean("mention.fuzzy.prefixMatch", true)
        mentionAllEnabled = config.getBoolean("mention.all.enabled", true)
        mentionAllPermission = config.getString("mention.all.permission") ?: "phcore.chat.mention.all"
        mentionAllFormat = config.getString("mention.all.format") ?: "&c@ALL&r"
        mentionAllNotifyMessage = config.getString("mention.all.notifyMessage") ?: "&e{sender} mentioned everyone: {message}"
        mentionAllActionBar = config.getString("mention.all.actionBar") ?: "&eMentioned by {sender}"
        mentionAllCooldownSeconds = config.getInt("mention.all.cooldownSeconds", 60).coerceAtLeast(0)
        mentionAllCooldownMessage = config.getString("mention.all.cooldownMessage") ?: "&cYou must wait {seconds}s before using @all again."
        mentionAllNoPermissionMessage = config.getString("mention.all.noPermissionMessage") ?: "&cYou don't have permission to use @all."
        mentionToggleOnMessage = config.getString("mention.toggle.onMessage") ?: "&7You will now receive @mentions."
        mentionToggleOffMessage = config.getString("mention.toggle.offMessage") ?: "&7You will no longer receive @mentions."
    }
}
