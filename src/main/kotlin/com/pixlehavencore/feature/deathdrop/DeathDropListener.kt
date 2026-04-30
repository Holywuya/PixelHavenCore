package com.pixlehavencore.feature.deathdrop

import org.bukkit.entity.Player
import org.bukkit.event.entity.PlayerDeathEvent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.module.chat.colored

object DeathDropListener {

    @SubscribeEvent(priority = EventPriority.MONITOR)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        if (!DeathDropSettings.enabled) return

        val player = event.entity
        if (!shouldApplyProtection(player)) return

        val usedBefore = DeathDropUsageStorage.getUsedToday(player.uniqueId)
        val total = totalProtection(player)

        if (usedBefore < total) {
            val used = DeathDropUsageStorage.consumeKeep(player.uniqueId)
            event.keepInventory = true
            event.keepLevel = true
            event.drops.clear()
            player.sendMessage(
                DeathDropSettings.keepMessage
                    .replace("{consume}", "1")
                    .replace("{left}", (total - used).coerceAtLeast(0).toString())
                    .replace("{used}", used.toString())
                    .replace("{total}", total.toString())
                    .colored()
            )
            return
        }
        
        // 否则正常掉落（没有墓碑）
        if (DeathDropSettings.outOfProtectionMessage.isNotBlank()) {
            player.sendMessage(DeathDropSettings.outOfProtectionMessage.colored())
        }
    }

    private fun shouldApplyProtection(player: Player): Boolean {
        if (player.world.name !in DeathDropSettings.worlds) return false
        val perm = DeathDropSettings.exemptPermission
        if (perm.isNotEmpty() && player.hasPermission(perm)) return false
        return true
    }

    private fun totalProtection(player: Player): Int {
        return (DeathDropSettings.dailyKeepCount + DeathDropUsageStorage.getBonusToday(player.uniqueId)).coerceAtLeast(0)
    }
}
