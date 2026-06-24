package com.pixlehavencore.feature.deathdrop

import org.bukkit.entity.Player
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

    data class PermissionGroup(val permission: String, val count: Int)

    var permissionGroups: List<PermissionGroup> = listOf(
        PermissionGroup("phcore.deathdrop.vip", 5),
        PermissionGroup("phcore.deathdrop.mvp", 10)
    )
        private set

    var keepMessage: String = "&a本次死亡不掉落，已消耗 &f{consume}&a 次保护，今日剩余：&f{left}&a/&f{total}"
        private set

    var outOfProtectionMessage: String = "&c今日死亡保护次数已用尽，物品正常掉落。"
        private set

    fun getBaseCount(player: Player): Int {
        return permissionGroups
            .firstOrNull { it.permission.isNotEmpty() && player.hasPermission(it.permission) }
            ?.count
            ?: dailyKeepCount
    }

    fun init() = reload()

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        worlds = config.getStringList("worlds").toSet()
        dailyKeepCount = config.getInt("daily-keep-count", 3).coerceAtLeast(0)
        exemptPermission = config.getString("exempt-permission") ?: "phcore.deathdrop.exempt"
        permissionGroups = config.getMapList("permission-groups").mapNotNull { map ->
            val perm = map["permission"]?.toString() ?: return@mapNotNull null
            val count = map["count"]?.toString()?.toIntOrNull() ?: return@mapNotNull null
            PermissionGroup(perm, count.coerceAtLeast(0))
        }
        keepMessage = config.getString("keep-message") ?: "&a本次死亡不掉落，已消耗 &f{consume}&a 次保护，今日剩余：&f{left}&a/&f{total}"
        outOfProtectionMessage = config.getString("out-of-protection-message") ?: "&c今日死亡保护次数已用尽，物品正常掉落。"
    }
}
