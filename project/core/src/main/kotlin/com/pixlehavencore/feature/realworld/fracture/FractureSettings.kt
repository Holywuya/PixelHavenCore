package com.pixlehavencore.feature.realworld.fracture

import org.bukkit.Material
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object FractureSettings {

    @Config("feature/realworld/fracture.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set
    var minFallDamage: Double = 4.0
        private set
    var damageMultiplier: Double = 5.0
        private set
    var recoveryRate: Double = 2.0
        private set
    var bandageHealAmount: Double = 30.0
        private set
    var bandageMaterial: Material = Material.PAPER
        private set
    var castMaterial: Material = Material.CLAY_BALL
        private set
    var mildThreshold: Double = 20.0
        private set
    var moderateThreshold: Double = 50.0
        private set
    var severeThreshold: Double = 80.0
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        minFallDamage = config.getDouble("min-fall-damage", 4.0).coerceAtLeast(0.0)
        damageMultiplier = config.getDouble("damage-multiplier", 5.0).coerceAtLeast(0.0)
        recoveryRate = config.getDouble("recovery-rate", 2.0).coerceAtLeast(0.0)
        bandageHealAmount = config.getDouble("bandage-heal-amount", 30.0).coerceAtLeast(0.0)
        bandageMaterial = runCatching {
            Material.valueOf((config.getString("bandage-material", "PAPER") ?: "PAPER").uppercase())
        }.getOrDefault(Material.PAPER)
        castMaterial = runCatching {
            Material.valueOf((config.getString("cast-material", "CLAY_BALL") ?: "CLAY_BALL").uppercase())
        }.getOrDefault(Material.CLAY_BALL)
        mildThreshold = config.getDouble("thresholds.mild", 20.0).coerceAtLeast(0.0)
        moderateThreshold = config.getDouble("thresholds.moderate", 50.0).coerceAtLeast(mildThreshold)
        severeThreshold = config.getDouble("thresholds.severe", 80.0).coerceAtLeast(moderateThreshold)
    }
}
