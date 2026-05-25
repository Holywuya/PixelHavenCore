package com.pixlehavencore.feature.keycommand

import com.pixlehavencore.util.PlaceholderUtils.resolvePlaceholders
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryType
import taboolib.platform.util.submit as submitOnEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object KeyCommandService {

    private val cooldown = ConcurrentHashMap<UUID, Long>()

    fun init() {
        KeyCommandSettings.init()
        cooldown.clear()
    }

    fun reload() {
        init()
    }

    fun triggerF(player: Player, shift: Boolean): Boolean {
        if (!KeyCommandSettings.enabled) {
            return false
        }
        if (player.openInventory.topInventory.type != InventoryType.CRAFTING) {
            return false
        }

        val command = when {
            shift && KeyCommandSettings.commandShiftF.isNotBlank() -> KeyCommandSettings.commandShiftF
            !shift && KeyCommandSettings.commandF.isNotBlank() -> KeyCommandSettings.commandF
            else -> ""
        }
        if (command.isBlank()) {
            return false
        }

        val now = System.currentTimeMillis()
        val last = cooldown[player.uniqueId] ?: 0L
        if (now - last < KeyCommandSettings.cooldownMillis) {
            return false
        }
        cooldown[player.uniqueId] = now

        player.submitOnEntity(delay = 1L) {
            val formatted = command
                .resolvePlaceholders("{player}" to player.name)
                .removePrefix("/")
                .trim()
            if (formatted.isNotBlank()) {
                player.performCommand(formatted)
            }
        }
        return true
    }
}
