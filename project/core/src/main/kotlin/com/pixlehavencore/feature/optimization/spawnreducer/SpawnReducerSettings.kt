package com.pixlehavencore.feature.optimization.spawnreducer

import org.bukkit.event.entity.CreatureSpawnEvent
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object SpawnReducerSettings {

    @Config("feature/optimization/spawn-reducer.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    var reductionPercent: Double = 30.0
        private set

    var naturalReasons: Set<CreatureSpawnEvent.SpawnReason> = setOf(
        CreatureSpawnEvent.SpawnReason.NATURAL,
        CreatureSpawnEvent.SpawnReason.CHUNK_GEN
    )
        private set

    var enabledWorld: Set<String> = emptySet()
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        reductionPercent = config.getDouble("reduction-percent", 30.0).coerceIn(0.0, 100.0)

        val loadedReasons = config.getStringList("natural-reasons")
            .mapNotNull { raw ->
                runCatching {
                    CreatureSpawnEvent.SpawnReason.valueOf(raw.trim().uppercase())
                }.getOrNull()
            }
            .toSet()

        naturalReasons = if (loadedReasons.isEmpty()) {
            setOf(
                CreatureSpawnEvent.SpawnReason.NATURAL,
                CreatureSpawnEvent.SpawnReason.CHUNK_GEN
            )
        } else {
            loadedReasons
        }
        enabledWorld = config.getStringList("enabled-world").toSet()
    }
}
