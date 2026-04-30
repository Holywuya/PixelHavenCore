package com.pixlehavencore.feature.world

import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.entity.Player
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submit
import taboolib.module.chat.colored
import taboolib.platform.util.submit as submitOnEntity

object WorldService {

    private var initialized = false

    fun init() {
        WorldSettings.init()
        preloadConfiguredWorlds()
        initialized = WorldSettings.enabled
    }

    fun reload() {
        init()
    }

    fun stop() {
        initialized = false
    }

    fun isEnabled(): Boolean {
        return initialized && WorldSettings.enabled
    }

    fun preloadConfiguredWorlds() {
        if (!WorldSettings.enabled) {
            return
        }
        submit {
            WorldSettings.allWorldNames().forEach { worldName ->
                ensureWorldLoaded(worldName)
            }
        }
    }

    fun ensureConfiguredWorldsLoaded() {
        if (!WorldSettings.enabled) {
            return
        }
        WorldSettings.allWorldNames().forEach { worldName ->
            ensureWorldLoaded(worldName)
        }
    }

    fun ensureWorldPresent(worldName: String): World? {
        val normalized = worldName.trim()
        if (normalized.isEmpty()) {
            return null
        }
        return ensureWorldLoaded(normalized)
    }

    private fun ensureWorldLoaded(worldName: String): World? {
        Bukkit.getWorld(worldName)?.let { return it }
        if (!WorldSettings.shouldLoadOnDemand(worldName)) {
            return null
        }
        return runCatching {
            Bukkit.createWorld(WorldCreator(worldName))
        }.getOrNull()?.also {
            info("[World] 已加载世界: ${it.name}")
        }
    }

    fun teleportSelf(player: Player, targetWorldName: String): Boolean {
        if (!WorldSettings.enabled) {
            return false
        }
        val worldName = WorldSettings.resolveWorldName(targetWorldName) ?: run {
            player.submitOnEntity {
                player.sendMessage(WorldSettings.messageWorldMissing.replace("{world}", targetWorldName).colored())
            }
            return false
        }
        val world = ensureWorldLoaded(worldName) ?: run {
            player.submitOnEntity {
                player.sendMessage(WorldSettings.messageWorldMissing.replace("{world}", worldName).colored())
            }
            return false
        }
        player.submitOnEntity {
            player.teleportAsync(world.spawnLocation).thenAccept {
                player.sendMessage(WorldSettings.messageTeleportSelf.replace("{world}", world.name).colored())
            }
        }
        return true
    }

    fun teleportOther(target: Player, targetWorldName: String): Boolean {
        if (!WorldSettings.enabled) {
            return false
        }
        val worldName = WorldSettings.resolveWorldName(targetWorldName) ?: run {
            target.submitOnEntity {
                target.sendMessage(WorldSettings.messageWorldMissing.replace("{world}", targetWorldName).colored())
            }
            return false
        }
        val world = ensureWorldLoaded(worldName) ?: run {
            target.submitOnEntity {
                target.sendMessage(WorldSettings.messageWorldMissing.replace("{world}", worldName).colored())
            }
            return false
        }
        target.submitOnEntity {
            target.teleportAsync(world.spawnLocation).thenAccept {
                target.sendMessage(WorldSettings.messageTeleportSelf.replace("{world}", world.name).colored())
            }
        }
        return true
    }

    fun currentWorldName(player: Player): String? {
        return player.world?.name
    }

}
