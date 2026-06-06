package com.pixlehavencore.feature.base.back

import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent

object BackListener {

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
}
