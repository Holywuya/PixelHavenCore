package com.pixlehavencore.feature.mmhealthbar

import com.pixlehavencore.util.MythicMobsBridge
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.platform.event.SubscribeEvent

object MMHealthBarListener {

    @SubscribeEvent
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        if (!MMHealthBarSettings.enabled) return
        val player = event.damager as? Player ?: return
        val entity = event.entity
        if (!MythicMobsBridge.isAvailable()) return
        if (MythicMobsBridge.resolveMobInfo(entity) == null) return
        MMHealthBarService.showBar(player, entity, event.damage)
    }

    @SubscribeEvent
    fun onEntityDeath(event: EntityDeathEvent) {
        if (!MMHealthBarSettings.enabled) return
        MMHealthBarService.onEntityRemoved(event.entity.uniqueId)
    }

    @SubscribeEvent
    fun onPlayerQuit(event: PlayerQuitEvent) {
        MMHealthBarService.removeBar(event.player.uniqueId)
    }
}
