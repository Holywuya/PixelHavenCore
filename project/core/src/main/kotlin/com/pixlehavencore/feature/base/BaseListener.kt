package com.pixlehavencore.feature.base

import org.bukkit.entity.Creeper
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.EntityPortalEnterEvent
import org.bukkit.event.entity.EntityTeleportEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.world.ChunkLoadEvent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent

object BaseListener {

    @SubscribeEvent
    fun onCreeperExplode(event: EntityExplodeEvent) {
        if (!BaseCommandSettings.enabled) return
        if (!BaseCommandSettings.creeperProtectEnabled) return
        if (event.entity !is Creeper) return

        if (BaseCommandSettings.creeperProtectCancelDamage) {
            // 完全取消爆炸：无方块破坏，无爆炸伤害
            event.isCancelled = true
        } else {
            // 仅保护方块不受破坏，爆炸伤害正常生效
            event.blockList().clear()
        }
    }

    @SubscribeEvent
    fun onBlockedEntityPortalEnter(event: EntityPortalEnterEvent) {
        if (!BaseCommandSettings.enabled) return
        if (!BaseCommandSettings.portalProtectionEnabled) return
        if (!BaseCommandSettings.blockedPortalEntities.contains(event.entityType)) return
        event.entity.remove()
    }

    @SubscribeEvent
    fun onBlockedEntityTeleport(event: EntityTeleportEvent) {
        if (!BaseCommandSettings.enabled) return
        if (!BaseCommandSettings.portalProtectionEnabled) return
        if (!BaseCommandSettings.blockedPortalEntities.contains(event.entityType)) return
        event.isCancelled = true
        event.entity.remove()
    }

    @SubscribeEvent
    fun onBlockedEntitySpawn(event: CreatureSpawnEvent) {
        if (!BaseCommandSettings.enabled) return
        if (!BaseCommandSettings.clearEntitiesInNetherEndEnabled) return
        if (!BaseCommandSettings.clearEntitiesInNetherEnd.contains(event.entityType)) return
        val environment = event.location.world?.environment ?: return
        if (environment.name == "NETHER" || environment.name == "THE_END") {
            event.isCancelled = true
            event.entity.remove()
        }
    }

    @SubscribeEvent
    fun onChunkLoad(event: ChunkLoadEvent) {
        if (!BaseCommandSettings.enabled) return
        if (!BaseCommandSettings.clearEntitiesInNetherEndEnabled) return
        val environment = event.world.environment
        if (environment.name != "NETHER" && environment.name != "THE_END") return
        // Folia: ChunkLoadEvent 在该区块的区域线程上触发，chunk.entities 读取和 entity.remove() 在同一区域线程上是安全的
        event.chunk.entities.filter { BaseCommandSettings.clearEntitiesInNetherEnd.contains(it.type) }.forEach { it.remove() }
    }

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        if (!BaseCommandSettings.enabled) return
        BackService.record(event.player.uniqueId, event.from, "teleport")
    }

    @SubscribeEvent(priority = EventPriority.MONITOR)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        if (!BaseCommandSettings.enabled) return
        BackService.record(event.player.uniqueId, event.player.location, "death")
    }

    @SubscribeEvent
    fun onPlayerDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        BackService.cancelWarmup(player.uniqueId)
    }

    @SubscribeEvent
    fun onPlayerJoin(event: PlayerJoinEvent) {
        FirstJoinService.handleJoin(event.player)
    }
}
