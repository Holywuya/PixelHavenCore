package com.pixlehavencore.feature.deathdrop

import org.bukkit.event.entity.PlayerDeathEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.module.chat.colored
import kotlin.random.Random

object DeathDropListener {

    @SubscribeEvent
    fun onPlayerDeath(event: PlayerDeathEvent) {
        if (!DeathDropSettings.enabled) return

        val player = event.entity
        if (player.world.name !in DeathDropSettings.worlds) return

        // 权限豁免检查
        val perm = DeathDropSettings.exemptPermission
        if (perm.isNotEmpty() && player.hasPermission(perm)) return

        // 仅在 KeepInventory 开启时生效（关闭时走原版掉落）
        if (!event.keepInventory) return

        // 本次死亡的掉落率（0.0 ~ 1.0）
        val ratePercent = resolveDropRate()
        val dropRate = ratePercent / 100.0

        var droppedCount = 0
        var keptCount = 0

        // KeepInventory 已开启，手动掉落随机比例物品
        event.drops.clear()

        val location = player.location
        val world = player.world
        val inventory = player.inventory

        val contents = inventory.contents
        for (index in contents.indices) {
            val item = contents[index] ?: continue
            if (item.type.isAir) continue
            if (Random.nextDouble() < dropRate) {
                world.dropItemNaturally(location, item.clone())
                contents[index] = null
                droppedCount++
            } else {
                keptCount++
            }
        }
        inventory.contents = contents

        val armorContents = inventory.armorContents
        for (index in armorContents.indices) {
            val item = armorContents[index] ?: continue
            if (item.type.isAir) continue
            if (Random.nextDouble() < dropRate) {
                world.dropItemNaturally(location, item.clone())
                armorContents[index] = null
                droppedCount++
            } else {
                keptCount++
            }
        }
        inventory.armorContents = armorContents

        val extraContents = inventory.extraContents
        for (index in extraContents.indices) {
            val item = extraContents[index] ?: continue
            if (item.type.isAir) continue
            if (Random.nextDouble() < dropRate) {
                world.dropItemNaturally(location, item.clone())
                extraContents[index] = null
                droppedCount++
            } else {
                keptCount++
            }
        }
        inventory.extraContents = extraContents

        // 发送提示消息（若配置为空则跳过）
        val msg = DeathDropSettings.deathMessage
        if (msg.isNotEmpty()) {
            player.sendMessage(
                msg.replace("{dropped}", droppedCount.toString())
                    .replace("{kept}", keptCount.toString())
                    .replace("{lost}", keptCount.toString())
                    .replace("{rate}", ratePercent.toInt().toString())
                    .colored()
            )
        }
    }

    /**
     * 在 [dropChanceMin, dropChanceMax] 范围内随机取本次死亡的掉落率（%）。
     * 若两端相等则直接返回该值，避免 Random.nextDouble 的 from >= until 异常。
     */
    private fun resolveDropRate(): Double {
        val min = DeathDropSettings.dropChanceMin
        val max = DeathDropSettings.dropChanceMax
        return if (min >= max) min else Random.nextDouble(min, max)
    }
}
