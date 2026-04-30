package com.pixlehavencore.feature.base

import org.bukkit.entity.EntityType
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object BaseCommandSettings {

    @Config("feature/base-command.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    var suicideMessage: String = "&c你已自杀。"
        private set

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
        enabled = config.getBoolean("enabled", true)
        suicideMessage = config.getString("messages.suicide") ?: "&c你已自杀。"
        creeperProtectEnabled = config.getBoolean("creeperProtect.enabled", false)
        creeperProtectCancelDamage = config.getBoolean("creeperProtect.cancelDamage", false)
        portalProtectionEnabled = config.getBoolean("portalProtection.enabled", config.getBoolean("frogProtection.enabled", true))
        blockedPortalEntities = parseEntityTypes(
            config.getStringList("portalProtection.blockedEntities").ifEmpty { listOf("FROG") }
        )
        clearEntitiesInNetherEndEnabled = config.getBoolean("portalProtection.clearInNetherEnd", config.getBoolean("frogProtection.clearInNetherEnd", true))
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
