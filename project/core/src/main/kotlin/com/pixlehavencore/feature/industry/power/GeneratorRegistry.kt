package com.pixlehavencore.feature.industry.power

import java.util.concurrent.ConcurrentHashMap

object GeneratorRegistry {

    private val byConfigId = ConcurrentHashMap<String, GeneratorType>()
    private val byCraftengineId = ConcurrentHashMap<String, GeneratorType>()

    fun reload() {
        byConfigId.clear()
        byCraftengineId.clear()

        for (config in PowerSettings.generators) {
            val generator = when (config.type.lowercase()) {
                "passive" -> PassiveGenerator(
                    id = config.id,
                    displayName = config.displayName,
                    generatePerSecond = config.generatePerSecond,
                    capacityContribution = config.capacityContribution
                )
                "fuel" -> FuelGenerator(
                    id = config.id,
                    displayName = config.displayName,
                    generatePerSecond = config.generatePerSecond,
                    capacityContribution = config.capacityContribution
                )
                else -> continue
            }
            byConfigId[config.id] = generator
            byCraftengineId[config.craftengineId] = generator
        }
    }

    fun get(configId: String): GeneratorType? = byConfigId[configId]

    fun getByCraftengineId(craftengineId: String): GeneratorType? = byCraftengineId[craftengineId]
}
