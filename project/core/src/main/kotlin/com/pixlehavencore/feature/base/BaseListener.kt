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
            event.isCancelled = true
        } else {
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
        event.chunk.entities
            .filter { BaseCommandSettings.clearEntitiesInNetherEnd.contains(it.type) }
            .forEach { it.remove() }
    }

    @SubscribeEvent(priority = EventPriority.MONITOR)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        BackService.handleDeath(event.player)
    }

    @SubscribeEvent
    fun onPlayerDamage(event: EntityDamageEvent) {
        if (!BackSettings.cancelOnDamage) return
        val player = event.entity as? Player ?: return
        BackService.cancelWarmup(player.uniqueId)
    }

    @SubscribeEvent
    fun onPlayerJoin(event: PlayerJoinEvent) {
        FirstJoinService.handleJoin(event.player)
    }
}
