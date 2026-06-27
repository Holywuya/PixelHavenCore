package com.pixlehavencore.feature.industry.power

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object PowerSettings {

    @Config("feature/industry/power.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    var energyPerBlock: Double = 10.0
        private set

    data class GeneratorConfig(
        val id: String,
        val type: String,
        val craftengineId: String,
        val displayName: String,
        val generatePerSecond: Double,
        val capacityContribution: Double
    )

    var generators: List<GeneratorConfig> = emptyList()
        private set

    fun init() = reload()

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        energyPerBlock = config.getDouble("energyPerBlock") ?: 10.0

        generators = config.getConfigurationSection("generators")?.getKeys(false)?.mapNotNull { key ->
            val section = config.getConfigurationSection("generators.$key") ?: return@mapNotNull null
            GeneratorConfig(
                id = key,
                type = section.getString("type") ?: return@mapNotNull null,
                craftengineId = section.getString("craftengineId") ?: return@mapNotNull null,
                displayName = section.getString("displayName") ?: key,
                generatePerSecond = section.getDouble("generatePerSecond") ?: 0.0,
                capacityContribution = section.getDouble("capacityContribution") ?: 0.0
            )
        } ?: emptyList()
    }
}
