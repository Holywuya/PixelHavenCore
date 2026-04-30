package com.pixlehavencore.feature.mobdrop

import com.pixlehavencore.util.EnchantUtils
import com.pixlehavencore.util.EntityUtils
import com.pixlehavencore.util.EconomyUtils
import com.pixlehavencore.util.ItemUtils
import com.pixlehavencore.util.MythicMobsBridge
import com.pixlehavencore.util.RandomUtils
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.event.SubscribeEvent
import java.math.BigDecimal

object MobDropListener {

    @SubscribeEvent
    fun onEntityDeath(event: EntityDeathEvent) {
        if (!MobDropSettings.enabled) return

        val mobInfo = MythicMobsBridge.resolveMobInfo(event.entity)
        val mobLevel = mobInfo?.level ?: 1
        val dropConfig = resolveDropConfig(event, mobInfo) ?: return

        // per-entity：只有当该怪物配置了 clearVanillaDrops=true 时，才清除原版掉落
        if (dropConfig.clearVanillaDrops) {
            event.drops.clear()
        }

        val killer = event.entity.killer
        val lootingLevel = EnchantUtils.getLevel(killer?.inventory?.itemInMainHand, "looting")
        val chanceMultiplier = MobDropSettings.resolveLootingMultiplier(lootingLevel)

        val extraDrops = mutableListOf<ItemStack>()
        dropConfig.rules.forEach { rule ->
            val baseChance = MobDropSettings.resolveChance(rule.chanceExpression, mobLevel)
            val actualChance = (baseChance * chanceMultiplier).coerceAtMost(1.0)
            if (!RandomUtils.roll(actualChance)) return@forEach
            val amount = MobDropSettings.resolveAmount(rule, mobLevel)
            val item = ItemUtils.resolveSpec(rule.itemSpec) ?: return@forEach
            ItemUtils.clamp(item, amount)
            extraDrops += item
        }

        event.drops.addAll(extraDrops)
        giveMoneyDrop(event, dropConfig, mobLevel)
    }

    private fun resolveDropConfig(event: EntityDeathEvent, mobInfo: com.pixlehavencore.util.MythicMobInfo?): MobDropSettings.EntityDropConfig? {
        mobInfo?.let { info ->
            MobDropSettings.mythicRules[info.id]?.let { return it }
        }
        return MobDropSettings.rules[event.entityType]
    }

    private fun giveMoneyDrop(event: EntityDeathEvent, dropConfig: MobDropSettings.EntityDropConfig, mobLevel: Int) {
        if (!EconomyUtils.isAvailable()) return
        dropConfig.moneyRules.forEach { money ->
            if (!RandomUtils.roll(MobDropSettings.resolveChance(money.chanceExpression, mobLevel))) {
                return@forEach
            }
            val min = (MobDropSettings.evaluateDouble(money.minExpression, mobLevel) ?: 0.0).coerceAtLeast(0.0)
            val max = (MobDropSettings.evaluateDouble(money.maxExpression, mobLevel) ?: min).coerceAtLeast(min)
            val amount = RandomUtils.nextDouble(min, max)
            if (amount <= 0.0) return@forEach

            when (money.mode) {
                MobDropSettings.MoneyDropMode.KILLER -> {
                    val killer = event.entity.killer ?: return@forEach
                    EconomyUtils.deposit(killer, amount.toBigDecimal())
                }
                MobDropSettings.MoneyDropMode.NEARBY -> {
                    val players = EntityUtils.nearbyPlayers(event.entity.world, event.entity.location, money.radius)
                    if (players.isEmpty()) return@forEach
                    if (money.split) {
                        val each = amount / players.size
                        if (each <= 0.0) return@forEach
                        players.forEach { EconomyUtils.deposit(it, each.toBigDecimal()) }
                    } else {
                        players.forEach { EconomyUtils.deposit(it, amount.toBigDecimal()) }
                    }
                }
            }
        }
    }
}
