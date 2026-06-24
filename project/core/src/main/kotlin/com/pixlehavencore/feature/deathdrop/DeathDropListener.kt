package com.pixlehavencore.feature.deathdrop

import com.pixlehavencore.util.PlaceholderUtils.resolvePlaceholders
import com.pixlehavencore.util.TextUtils
import org.bukkit.entity.Player
import org.bukkit.event.entity.PlayerDeathEvent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent

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
                TextUtils.parse(DeathDropSettings.keepMessage
                    .resolvePlaceholders(
                        "{consume}" to "1",
                        "{left}" to (total - used).coerceAtLeast(0).toString(),
                        "{used}" to used.toString(),
                        "{total}" to total.toString()
                    ))
            )
            return
        }

        // 否则正常掉落（没有墓碑）
        if (DeathDropSettings.outOfProtectionMessage.isNotBlank()) {
            player.sendMessage(TextUtils.parse(DeathDropSettings.outOfProtectionMessage))
        }
    }

    private fun shouldApplyProtection(player: Player): Boolean {
        if (player.world.name !in DeathDropSettings.worlds) return false
        val perm = DeathDropSettings.exemptPermission
        if (perm.isNotEmpty() && player.hasPermission(perm)) return false
        return true
    }

    private fun totalProtection(player: Player): Int {
        return (DeathDropSettings.getBaseCount(player) + DeathDropUsageStorage.getBonusToday(player.uniqueId)).coerceAtLeast(0)
    }
}
