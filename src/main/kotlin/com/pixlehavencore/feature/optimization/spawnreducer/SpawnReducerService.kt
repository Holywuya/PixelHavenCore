package com.pixlehavencore.feature.optimization.spawnreducer

import org.bukkit.event.entity.CreatureSpawnEvent
import taboolib.common.platform.function.warning
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
        val enabledWorld = SpawnReducerSettings.enabledWorld
        if (enabledWorld.isNotEmpty()) {
            val worldName = event.entity?.world?.name
            if (worldName == null) {
                warning("[SpawnReducer] CreatureSpawnEvent 中实体世界引用为 null，跳过缩减判定")
                return false
            }
            if (worldName !in enabledWorld) {
                return false
            }
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
