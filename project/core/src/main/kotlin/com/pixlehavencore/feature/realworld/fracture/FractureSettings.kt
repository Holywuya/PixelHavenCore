package com.pixlehavencore.feature.realworld.fracture

import org.bukkit.Material
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object FractureSettings {

    @Config("feature/realworld/fracture.yml")
    private lateinit var config: Configuration

    var fractureEnabled: Boolean = true
        private set
    var fractureMinFallDamage: Double = 4.0
        private set
    var fractureDamageMultiplier: Double = 5.0
        private set
    var fractureRecoveryRate: Double = 2.0
        private set
    var fractureBandageHealAmount: Double = 30.0
        private set
    var fractureBandageMaterial: Material = Material.PAPER
        private set
    var fractureCastMaterial: Material = Material.CLAY_BALL
        private set
    var fractureMildThreshold: Double = 20.0
        private set
    var fractureModerateThreshold: Double = 50.0
        private set
    var fractureSevereThreshold: Double = 80.0
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()

        fractureEnabled = config.getBoolean("enabled", true)
        fractureMinFallDamage = config.getDouble("min-fall-damage", 4.0).coerceAtLeast(0.0)
        fractureDamageMultiplier = config.getDouble("damage-multiplier", 5.0).coerceAtLeast(0.0)
        fractureRecoveryRate = config.getDouble("recovery-rate", 2.0).coerceAtLeast(0.0)
        fractureBandageHealAmount = config.getDouble("bandage-heal-amount", 30.0).coerceAtLeast(0.0)
        fractureBandageMaterial = runCatching {
            Material.valueOf((config.getString("bandage-material", "PAPER") ?: "PAPER").uppercase())
        }.getOrDefault(Material.PAPER)
        fractureCastMaterial = runCatching {
            Material.valueOf((config.getString("cast-material", "CLAY_BALL") ?: "CLAY_BALL").uppercase())
        }.getOrDefault(Material.CLAY_BALL)
        fractureMildThreshold = config.getDouble("thresholds.mild", 20.0).coerceAtLeast(0.0)
        fractureModerateThreshold = config.getDouble("thresholds.moderate", 50.0).coerceAtLeast(fractureMildThreshold)
        fractureSevereThreshold = config.getDouble("thresholds.severe", 80.0).coerceAtLeast(fractureModerateThreshold)
    }
}
