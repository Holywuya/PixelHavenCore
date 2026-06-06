package com.pixlehavencore.feature.base.protection

import org.bukkit.entity.EntityType
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object ProtectionSettings {

    @Config("feature/base/protection.yml")
    private lateinit var config: Configuration

    var creeperProtectEnabled: Boolean = false
        private set

    var creeperProtectCancelDamage: Boolean = false
        private set

    var portalProtectionEnabled: Boolean = true
        private set

    var blockedPortalEntities: Set<EntityType> = setOf(EntityType.FROG)
        private set

    var clearEntitiesInNetherEndEnabled: Boolean = true
        private set

    var clearEntitiesInNetherEnd: Set<EntityType> = setOf(EntityType.FROG)
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        creeperProtectEnabled = config.getBoolean("creeperProtect.enabled", false)
        creeperProtectCancelDamage = config.getBoolean("creeperProtect.cancelDamage", false)
        portalProtectionEnabled = config.getBoolean("portalProtection.enabled", true)
        blockedPortalEntities = parseEntityTypes(
            config.getStringList("portalProtection.blockedEntities").ifEmpty { listOf("FROG") }
        )
        clearEntitiesInNetherEndEnabled = config.getBoolean("portalProtection.clearInNetherEnd", true)
        clearEntitiesInNetherEnd = parseEntityTypes(
            config.getStringList("portalProtection.clearEntitiesInNetherEnd").ifEmpty { listOf("FROG") }
        )
    }

    private fun parseEntityTypes(values: List<String>): Set<EntityType> {
        return values.mapNotNull { raw ->
            runCatching { EntityType.valueOf(raw.trim().uppercase()) }.getOrNull()
        }.toSet()
    }
}
