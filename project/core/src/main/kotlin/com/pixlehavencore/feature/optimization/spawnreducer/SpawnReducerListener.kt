package com.pixlehavencore.feature.optimization.spawnreducer

import org.bukkit.event.entity.CreatureSpawnEvent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent

object SpawnReducerListener {

    @SubscribeEvent(priority = EventPriority.NORMAL)
    fun onCreatureSpawn(event: CreatureSpawnEvent) {
        if (SpawnReducerService.shouldCancelNaturalSpawn(event)) {
            event.isCancelled = true
        }
    }
}
