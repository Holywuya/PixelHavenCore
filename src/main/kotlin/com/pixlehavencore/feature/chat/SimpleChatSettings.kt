package com.pixlehavencore.feature.chat

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object SimpleChatSettings {

    @Config("feature/chat/chat.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    var ignorePlaceholderApi: Boolean = false
        private set

    var asciiEnabled: Boolean = true
        private set

    var asciiColor: String = "GREEN"
        private set

    var format: String = "&7[&eLv&c.&a%player_level%&7]"
        private set

    var nameFormat: String = "<hover:show_text:点击私聊玩家><click:suggest_command:/msg %player_name%>&f<%player_name%>&r"
        private set

    var messageSeparator: String = " "
        private set

    var privateSenderFormat: String = "&f✉&a⬆ <hover:show_text:点击追加私聊><click:suggest_command:/msg %receiver_name%>&b你 &7-> &e%receiver_name%&7: &r"
        private set

    var privateReceiverFormat: String = "&f✉&a⬇ <hover:show_text:点击回复><click:suggest_command:/msg %sender_name%>&b%sender_name% &7-> &e你&7: &r"
        private set

    var sayFormat: String = "&f%sender_name%&7: &r"
        private set

    var sayConsoleFormat: String = "&c[&6CONSOLE&c] &r"
        private set

    var atEnabled: Boolean = true
        private set

    var atFormat: String = "&b@%player_name%"
        private set

    var atSoundEnabled: Boolean = true
        private set

    var atSoundType: String = "ENTITY_PLAYER_LEVELUP"
        private set

    var atSoundCooldownSeconds: Int = 5
        private set

    var linkDetectionEnabled: Boolean = true
        private set

    var numberDetectionEnabled: Boolean = true
        private set

    var numberMinLength: Int = 5
        private set

    var numberMaxLength: Int = 13
        private set

    var redisEnabled: Boolean = false
        private set

    var redisHost: String = "localhost"
        private set

    var redisPort: Int = 6379
        private set

    var redisDatabase: Int = 0
        private set

    var redisPassword: String = ""
        private set

    var redisServerId: String = "server1"
        private set

    var redisChannel: String = "simplechat"
        private set

    var redisLogOtherServers: Boolean = true
        private set

    var redisConsoleColor: String = "GREEN"
        private set

    var redisClearClick: Boolean = true
        private set

    var redisIgnoreKeywords: List<String> = listOf("点击", "click")
        private set

    var redisWhitelistKeywords: List<String> = listOf("点击打开链接", "点击复制", "打开URL", "复制内容")
        private set

    var defaultLevel: String = "?"
        private set

    var defaultHealth: String = "?"
        private set

    var defaultFood: String = "?"
        private set

    var defaultExp: String = "?"
        private set

    var defaultGamemode: String = "?"
        private set

    var chatColorEnabled: Boolean = true
        private set

    var chatFormatEnabled: Boolean = true
        private set

    var chatMentionEnabled: Boolean = true
        private set

    var chatIgnoreEnabled: Boolean = true
        private set

    var chatBypassEnabled: Boolean = true
        private set

    var chatJsonEnabled: Boolean = false
        private set

    var chatAdminPermission: String = "simplechat.admin"
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()

        enabled = config.getBoolean("enabled", true)
        ignorePlaceholderApi = config.getBoolean("ignorePlaceholderApi", false)

        asciiEnabled = config.getBoolean("ascii.enabled", true)
        asciiColor = (config.getString("ascii.color") ?: "GREEN").uppercase()

        format = config.getString("format") ?: "&7[&eLv&c.&a%player_level%&7]"
        nameFormat = config.getString("nameFormat") ?: "<hover:show_text:点击私聊玩家><click:suggest_command:/msg %player_name%>&f<%player_name%>&r"
        messageSeparator = config.getString("messageSeparator") ?: " "

        privateSenderFormat = config.getString("privateMessage.senderFormat")
            ?: "&f✉&a⬆ <hover:show_text:点击追加私聊><click:suggest_command:/msg %receiver_name%>&b你 &7-> &e%receiver_name%&7: &r"
        privateReceiverFormat = config.getString("privateMessage.receiverFormat")
            ?: "&f✉&a⬇ <hover:show_text:点击回复><click:suggest_command:/msg %sender_name%>&b%sender_name% &7-> &e你&7: &r"

        sayFormat = config.getString("sayCommand.format") ?: "&f%sender_name%&7: &r"
        sayConsoleFormat = config.getString("sayCommand.consoleFormat") ?: "&c[&6CONSOLE&c] &r"

        atEnabled = config.getBoolean("at.enabled", true)
        atFormat = config.getString("at.format") ?: "&b@%player_name%"
        atSoundEnabled = config.getBoolean("at.sound.enabled", true)
        atSoundType = config.getString("at.sound.type") ?: "ENTITY_PLAYER_LEVELUP"
        atSoundCooldownSeconds = config.getInt("at.sound.cooldown", 5).coerceAtLeast(0)

        linkDetectionEnabled = config.getBoolean("linkDetection.enabled", true)
        numberDetectionEnabled = config.getBoolean("numberDetection.enabled", true)
        numberMinLength = config.getInt("numberDetection.pattern.min", 5).coerceAtLeast(1)
        numberMaxLength = config.getInt("numberDetection.pattern.max", 13).coerceAtLeast(numberMinLength)

        redisEnabled = config.getBoolean("redisChat.enabled", false)
        redisHost = config.getString("redisChat.host") ?: "localhost"
        redisPort = config.getInt("redisChat.port", 6379).coerceAtLeast(1)
        redisDatabase = config.getInt("redisChat.database", 0).coerceAtLeast(0)
        redisPassword = config.getString("redisChat.password") ?: ""
        redisServerId = config.getString("redisChat.serverId") ?: "server1"
        redisChannel = config.getString("redisChat.channel") ?: "simplechat"
        redisLogOtherServers = config.getBoolean("redisChat.logOtherServers", true)
        redisConsoleColor = (config.getString("redisChat.consoleColor") ?: "GREEN").uppercase()
        redisClearClick = config.getBoolean("redisChat.clearClick", true)
        redisIgnoreKeywords = config.getStringList("redisChat.ignoreKeywords").ifEmpty { listOf("点击", "click") }
        redisWhitelistKeywords = config.getStringList("redisChat.whitelistKeywords").ifEmpty { listOf("点击打开链接", "点击复制", "打开URL", "复制内容") }

        defaultLevel = config.getString("placeholders.defaultLevel") ?: "?"
        defaultHealth = config.getString("placeholders.defaultHealth") ?: "?"
        defaultFood = config.getString("placeholders.defaultFood") ?: "?"
        defaultExp = config.getString("placeholders.defaultExp") ?: "?"
        defaultGamemode = config.getString("placeholders.defaultGamemode") ?: "?"

        chatColorEnabled = config.getBoolean("chat.color.enabled", true)
        chatFormatEnabled = config.getBoolean("chat.format.enabled", true)
        chatMentionEnabled = config.getBoolean("chat.mention.enabled", true)
        chatIgnoreEnabled = config.getBoolean("chat.ignore.enabled", true)
        chatBypassEnabled = config.getBoolean("chat.bypass.enabled", true)
        chatJsonEnabled = config.getBoolean("chat.json.enabled", false)

        chatAdminPermission = config.getString("permissions.admin") ?: "simplechat.admin"
    }
}
