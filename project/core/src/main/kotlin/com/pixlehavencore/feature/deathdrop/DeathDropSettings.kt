package com.pixlehavencore.feature.deathdrop

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object DeathDropSettings {

    @Config("feature/death-drop.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    var worlds: Set<String> = setOf("world_the_end", "world_nether")
        private set

    var dailyKeepCount: Int = 3
        private set

    var exemptPermission: String = "phcore.deathdrop.exempt"
        private set

    var keepMessage: String = "&a本次死亡不掉落，已消耗 &f{consume}&a 次保护，今日剩余：&f{left}&a/&f{total}"
        private set

    var outOfProtectionMessage: String = "&c今日死亡保护次数已用尽，物品正常掉落。"
        private set

    fun init() = reload()

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        worlds = config.getStringList("worlds").toSet()
        dailyKeepCount = config.getInt("dailyKeepCount", 3).coerceAtLeast(0)
        exemptPermission = config.getString("exemptPermission") ?: "phcore.deathdrop.exempt"
        keepMessage = config.getString("keepMessage") ?: "&a本次死亡不掉落，已消耗 &f{consume}&a 次保护，今日剩余：&f{left}&a/&f{total}"
        outOfProtectionMessage = config.getString("outOfProtectionMessage") ?: "&c今日死亡保护次数已用尽，物品正常掉落。"
    }
}
