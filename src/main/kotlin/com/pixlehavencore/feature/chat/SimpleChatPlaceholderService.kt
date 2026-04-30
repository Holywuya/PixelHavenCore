package com.pixlehavencore.feature.chat

import org.bukkit.entity.Player
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.submit
import taboolib.platform.compat.replacePlaceholder
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object SimpleChatPlaceholderService {

    private val levelCache = ConcurrentHashMap<UUID, String>()
    private val missingLevels = ConcurrentHashMap.newKeySet<UUID>()
    private val loadingLevels = ConcurrentHashMap.newKeySet<UUID>()

    fun reload() {
        levelCache.clear()
        missingLevels.clear()
        loadingLevels.clear()
    }

    fun apply(player: Player, text: String): String {
        val displayName = player.displayName
        val worldName = player.world.name
        var result = text
            .replace("%player_server%", SimpleChatSettings.redisServerId)
            .replace("%player_server_alias%", SimpleChatSettings.redisServerId.take(1).lowercase())
            .replace("%player_server_alias_uppercase%", SimpleChatSettings.redisServerId.take(1).uppercase())
            .replace("%player_name%", player.name)
            .replace("%player_displayname%", displayName)
            .replace("%player_world%", worldName)

        if (!SimpleChatSettings.ignorePlaceholderApi) {
            result = runCatching { result.replacePlaceholder(player) }.getOrDefault(result)
        } else {
            val fromSimpleChat = resolveSimpleChatPlayerLevel(player)
            result = result
                .replace("%player_level%", fromSimpleChat ?: player.level.toString())
                .replace("%player_health%", player.health.toInt().toString())
                .replace("%player_food%", player.foodLevel.toString())
                .replace("%player_exp%", (player.exp * 100).toInt().toString())
                .replace("%player_gamemode%", player.gameMode.name.lowercase())
        }

        return result
    }

    fun applyPrivate(sender: Player, receiver: Player, text: String): String {
        val senderDisplayName = sender.displayName
        val senderWorldName = sender.world.name
        val receiverDisplayName = receiver.displayName
        val receiverWorldName = receiver.world.name
        return apply(sender, text)
            .replace("%sender_name%", sender.name)
            .replace("%sender_displayname%", senderDisplayName)
            .replace("%sender_world%", senderWorldName)
            .replace("%receiver_name%", receiver.name)
            .replace("%receiver_displayname%", receiverDisplayName)
            .replace("%receiver_world%", receiverWorldName)
    }

    fun applySay(sender: Player, text: String): String {
        val displayName = sender.displayName
        val worldName = sender.world.name
        return apply(sender, text)
            .replace("%sender_name%", sender.name)
            .replace("%sender_displayname%", displayName)
            .replace("%sender_world%", worldName)
    }

    private fun resolveSimpleChatPlayerLevel(player: Player): String? {
        val uniqueId = player.uniqueId
        levelCache[uniqueId]?.let { return it }
        if (missingLevels.contains(uniqueId)) {
            return null
        }
        if (loadingLevels.add(uniqueId)) {
            submit(async = true) {
                val level = loadSimpleChatPlayerLevel(uniqueId)
                if (level == null) {
                    missingLevels.add(uniqueId)
                } else {
                    levelCache[uniqueId] = level
                    missingLevels.remove(uniqueId)
                }
                loadingLevels.remove(uniqueId)
            }
        }
        return null
    }

    private fun loadSimpleChatPlayerLevel(uniqueId: UUID): String? {
        val levelFile = File(getDataFolder(), "SimpleChat/PlayerLevelStorageData/$uniqueId.yml")
        if (!levelFile.exists()) {
            return null
        }
        return runCatching {
            val yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(levelFile)
            yaml.getString("level") ?: yaml.getInt("level", -1).takeIf { it >= 0 }?.toString()
        }.getOrNull()
    }
}
