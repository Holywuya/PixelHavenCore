package com.pixlehavencore.feature.base.protection

import org.bukkit.entity.Creeper
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.EntityPortalEnterEvent
import org.bukkit.event.entity.EntityTeleportEvent
import org.bukkit.event.world.ChunkLoadEvent
import taboolib.common.platform.event.SubscribeEvent

object ProtectionListener {

    @SubscribeEvent
    fun onCreeperExplode(event: EntityExplodeEvent) {
        if (!ProtectionSettings.creeperProtectEnabled) return
        if (event.entity !is Creeper) return
        if (ProtectionSettings.creeperProtectCancelDamage) {
            event.isCancelled = true
        } else {
            event.blockList().clear()
        }
    }

    @SubscribeEvent
    fun onBlockedEntityPortalEnter(event: EntityPortalEnterEvent) {
        if (!ProtectionSettings.portalProtectionEnabled) return
        if (!ProtectionSettings.blockedPortalEntities.contains(event.entityType)) return
        event.entity.remove()
    }

    @SubscribeEvent
    fun onBlockedEntityTeleport(event: EntityTeleportEvent) {
        if (!ProtectionSettings.portalProtectionEnabled) return
        if (!ProtectionSettings.blockedPortalEntities.contains(event.entityType)) return
        event.isCancelled = true
        event.entity.remove()
    }

    @SubscribeEvent
    fun onBlockedEntitySpawn(event: CreatureSpawnEvent) {
        if (!ProtectionSettings.clearEntitiesInNetherEndEnabled) return
        if (!ProtectionSettings.clearEntitiesInNetherEnd.contains(event.entityType)) return
        val environment = event.location.world?.environment ?: return
        if (environment.name == "NETHER" || environment.name == "THE_END") {
            event.isCancelled = true
            event.entity.remove()
        }
    }

    @SubscribeEvent
    fun onChunkLoad(event: ChunkLoadEvent) {
        if (!ProtectionSettings.clearEntitiesInNetherEndEnabled) return
        val environment = event.world.environment
        if (environment.name != "NETHER" && environment.name != "THE_END") return
        event.chunk.entities
            .filter { ProtectionSettings.clearEntitiesInNetherEnd.contains(it.type) }
            .forEach { it.remove() }
    }
}
