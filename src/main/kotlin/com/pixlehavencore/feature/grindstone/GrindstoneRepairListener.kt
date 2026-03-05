package com.pixlehavencore.feature.grindstone

import org.bukkit.Material
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.adaptPlayer
import taboolib.platform.util.isRightClick
import com.pixlehavencore.feature.veinminer.VeinminerMessages
import kotlin.random.Random

object GrindstoneRepairListener {

    @SubscribeEvent
    fun onInteract(event: PlayerInteractEvent) {
        if (!GrindstoneRepairSettings.enabled) {
            return
        }
        if (!event.isRightClick()) {
            return
        }
        val block = event.clickedBlock ?: return
        if (block.type != Material.GRINDSTONE) {
            return
        }
        val player = event.player
        val proxyPlayer = adaptPlayer(player)
        if (GrindstoneRepairSettings.permission.isNotEmpty() && !proxyPlayer.hasPermission(GrindstoneRepairSettings.permission)) {
            return
        }
        if (GrindstoneRepairSettings.requireSneak && !proxyPlayer.isSneaking) {
            return
        }
        val main = player.inventory.itemInMainHand
        val offhand = player.inventory.itemInOffHand
        if (main.type == Material.AIR || offhand.type == Material.AIR) {
            return
        }
        if (main.type.maxDurability <= 0) {
            return
        }
        val meta = main.itemMeta as? Damageable ?: return
        if (meta.isUnbreakable) {
            return
        }
        if (meta.damage <= 0) {
            return
        }
        if (!GrindstoneRepairSettings.matchMaterial(main.type, offhand.type)) {
            return
        }
        val restore = GrindstoneRepairSettings.restorePerItem
        if (restore <= 0) {
            return
        }
        if (Random.nextDouble() > GrindstoneRepairSettings.chance) {
            VeinminerMessages.send(proxyPlayer, GrindstoneRepairSettings.messageFailed)
            event.isCancelled = true
            return
        }
        meta.damage = (meta.damage - restore).coerceAtLeast(0)
        main.itemMeta = meta
        player.inventory.setItemInMainHand(main)
        consumeOffhand(offhand)
        VeinminerMessages.send(proxyPlayer, GrindstoneRepairSettings.messageSuccess, mapOf("amount" to restore))
        event.isCancelled = true
    }

    private fun consumeOffhand(item: ItemStack) {
        if (item.type == Material.AIR) {
            return
        }
        item.amount = item.amount - 1
        if (item.amount <= 0) {
            item.type = Material.AIR
        }
    }
}
