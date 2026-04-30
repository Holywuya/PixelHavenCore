package com.pixlehavencore.feature.optimization.spawnreducer

import org.bukkit.event.entity.CreatureSpawnEvent
import java.util.concurrent.ThreadLocalRandom

object SpawnReducerService {

    fun init() {
        SpawnReducerSettings.init()
    }

    fun reload() {
        init()
    }

    fun isEnabled(): Boolean {
        return SpawnReducerSettings.enabled
    }

    fun shouldCancelNaturalSpawn(event: CreatureSpawnEvent): Boolean {
        if (!SpawnReducerSettings.enabled) {
            return false
        }
        if (event.isCancelled) {
            return false
        }
        if (event.spawnReason !in SpawnReducerSettings.naturalReasons) {
            return false
        }
        val reduction = SpawnReducerSettings.reductionPercent
        if (reduction <= 0.0) {
            return false
        }
        if (reduction >= 100.0) {
            return true
        }
        return ThreadLocalRandom.current().nextDouble(100.0) < reduction
    }
}
