package com.pixlehavencore.feature.world

import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.entity.Player
import com.pixlehavencore.feature.chat.WorldNameMapper
import com.pixlehavencore.util.PlaceholderUtils.resolvePlaceholders
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submit
import com.pixlehavencore.util.TextUtils
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
        // Folia: submit {} 调度到全局区域调度器，createWorld 在此上下文中是安全的
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

    /**
     * 同步加载世界。只允许在全局区域调度器上调用（preloadConfiguredWorlds 已确保）。
     */
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

    /**
     * 异步传送玩家到目标世界。
     * 如果世界未加载，先在全局区域调度器上加载，再在玩家区域线程上执行传送。
     */
    fun teleportSelf(player: Player, targetWorldName: String): Boolean {
        if (!WorldSettings.enabled) {
            return false
        }
        val worldName = WorldSettings.resolveWorldName(targetWorldName) ?: run {
            player.submitOnEntity {
                player.sendMessage(TextUtils.parse(WorldSettings.messageWorldMissing.resolvePlaceholders("{world}" to targetWorldName)))
            }
            return false
        }
        // 已加载的世界直接传送
        val existing = Bukkit.getWorld(worldName)
        if (existing != null) {
            doTeleport(player, existing)
            return true
        }
        if (!WorldSettings.shouldLoadOnDemand(worldName)) {
            player.submitOnEntity {
                player.sendMessage(TextUtils.parse(WorldSettings.messageWorldMissing.resolvePlaceholders("{world}" to worldName)))
            }
            return false
        }
        // Folia: 异步加载世界 → 加载完成后在玩家区域线程上执行传送
        submit {
            val world = runCatching { Bukkit.createWorld(WorldCreator(worldName)) }.getOrNull()
            player.submitOnEntity {
                if (!player.isOnline) return@submitOnEntity
                if (world != null) {
                    doTeleport(player, world)
                } else {
                    player.sendMessage(TextUtils.parse(WorldSettings.messageWorldMissing.resolvePlaceholders("{world}" to worldName)))
                }
            }
        }
        return true
    }

    /**
     * 异步传送其他玩家到目标世界。
     */
    fun teleportOther(target: Player, targetWorldName: String): Boolean {
        if (!WorldSettings.enabled) {
            return false
        }
        val worldName = WorldSettings.resolveWorldName(targetWorldName) ?: run {
            target.submitOnEntity {
                target.sendMessage(TextUtils.parse(WorldSettings.messageWorldMissing.resolvePlaceholders("{world}" to targetWorldName)))
            }
            return false
        }
        val existing = Bukkit.getWorld(worldName)
        if (existing != null) {
            doTeleport(target, existing)
            return true
        }
        if (!WorldSettings.shouldLoadOnDemand(worldName)) {
            target.submitOnEntity {
                target.sendMessage(TextUtils.parse(WorldSettings.messageWorldMissing.resolvePlaceholders("{world}" to worldName)))
            }
            return false
        }
        submit {
            val world = runCatching { Bukkit.createWorld(WorldCreator(worldName)) }.getOrNull()
            target.submitOnEntity {
                if (!target.isOnline) return@submitOnEntity
                if (world != null) {
                    doTeleport(target, world)
                } else {
                    target.sendMessage(TextUtils.parse(WorldSettings.messageWorldMissing.resolvePlaceholders("{world}" to worldName)))
                }
            }
        }
        return true
    }

    /**
     * 在玩家区域线程上执行传送（已通过 submitOnEntity 保证线程安全）。
     */
    private fun doTeleport(player: Player, world: World) {
        player.teleportAsync(world.spawnLocation).thenAccept {
            player.sendMessage(TextUtils.parse(WorldSettings.messageTeleportSelf.resolvePlaceholders("{world}" to WorldNameMapper.resolve(world.name))))
        }
    }

    fun currentWorldName(player: Player): String? {
        return player.world?.name
    }

}
