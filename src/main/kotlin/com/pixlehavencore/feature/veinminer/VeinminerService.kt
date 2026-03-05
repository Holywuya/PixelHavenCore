package com.pixlehavencore.feature.veinminer

import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import taboolib.common.platform.ProxyGameMode
import taboolib.common.platform.ProxyPlayer
import taboolib.common.platform.function.adaptPlayer
import taboolib.platform.compat.isEconomySupported
import taboolib.platform.compat.getBalance
import taboolib.platform.compat.withdrawBalance
import kotlin.math.min
import java.util.ArrayDeque
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object VeinminerService {

    private val cooldowns = ConcurrentHashMap<UUID, Long>()
    private val mining = Collections.newSetFromMap(ConcurrentHashMap<UUID, Boolean>())
    private val offsetCache = ConcurrentHashMap<Int, List<Offset>>()

    fun handleBlockBreak(player: Player, source: Block): Boolean {
        if (!VeinminerSettings.enabled) {
            return false
        }
        val proxyPlayer = adaptPlayer(player)
        if (VeinminerSettings.permissionRestricted && !proxyPlayer.hasPermission("veinminer.use")) {
            return false
        }
        if (VeinminerSettings.mustSneak && !proxyPlayer.isSneaking) {
            return false
        }
        if (mining.contains(proxyPlayer.uniqueId)) {
            return false
        }
        if (!VeinminerSettings.isBlockAllowed(source.type)) {
            return false
        }
        val tool = player.inventory.itemInMainHand
        if (tool.type == Material.AIR) {
            return false
        }
        if (!VeinminerSettings.isToolAllowed(tool.type)) {
            return false
        }
        if (!checkCooldown(proxyPlayer)) {
            return false
        }
        if (VeinminerSettings.needCorrectTool && !hasCorrectTool(tool, player, source)) {
            return false
        }
        val remaining = if (VeinminerSettings.limitEnabled) {
            VeinminerLimitService.getRemaining(proxyPlayer)
        } else {
            Int.MAX_VALUE
        }
        if (remaining <= 1) {
            return false
        }
        val maxNodes = min(VeinminerSettings.maxChain, remaining)
        val chain = collectChain(source, player, tool, maxNodes)
        if (chain.size <= 1) {
            return false
        }
        if (!VeinminerLimitService.consume(proxyPlayer, chain.size)) {
            VeinminerMessages.send(proxyPlayer, VeinminerSettings.messageLimitDenied)
            return false
        }
        if (!checkEconomy(player, chain.size)) {
            VeinminerLimitService.consume(proxyPlayer, -chain.size)
            return false
        }
        mining.add(proxyPlayer.uniqueId)
        try {
            breakChain(player, tool, chain)
        } finally {
            mining.remove(proxyPlayer.uniqueId)
        }
        VeinminerMessages.send(proxyPlayer, VeinminerSettings.messageLimitRemaining, mapOf("remaining" to VeinminerLimitService.getRemaining(proxyPlayer)))
        return true
    }

    private fun checkCooldown(player: ProxyPlayer): Boolean {
        val now = System.currentTimeMillis()
        val next = cooldowns[player.uniqueId] ?: 0L
        if (now < next) {
            return false
        }
        cooldowns[player.uniqueId] = now + VeinminerSettings.cooldownMillis()
        return true
    }

    private fun collectChain(source: Block, player: Player, tool: ItemStack, maxNodes: Int): List<Block> {
        val originType = source.type
        val originOreType = VeinminerSettings.getOreType(originType)
        val radius = VeinminerSettings.searchRadius
        if (maxNodes <= 1) {
            return listOf(source)
        }
        val offsets = getOffsets(radius)
        val visited = HashSet<Long>(maxNodes * 2)
        val result = ArrayList<Block>(maxNodes)
        val queue: ArrayDeque<Block> = ArrayDeque()
        queue.add(source)
        while (queue.isNotEmpty() && result.size < maxNodes) {
            val current = queue.removeFirst()
            val key = blockKey(current)
            if (!visited.add(key)) {
                continue
            }
            val currentType = current.type
            if (currentType != originType) {
                if (originOreType == null) {
                    continue
                }
                val currentOreType = VeinminerSettings.getOreType(currentType)
                if (currentOreType == null || currentOreType != originOreType) {
                    continue
                }
            }
            if (VeinminerSettings.needCorrectTool && !hasCorrectTool(tool, player, current)) {
                continue
            }
            result.add(current)
            for (offset in offsets) {
                val next = current.getRelative(offset.x, offset.y, offset.z)
                val nextKey = blockKey(next)
                val nextType = next.type
                if (!visited.contains(nextKey) && (nextType == originType || (originOreType != null && VeinminerSettings.getOreType(nextType) == originOreType))) {
                    queue.add(next)
                }
            }
        }
        return result
    }

    private fun getOffsets(radius: Int): List<Offset> {
        return offsetCache.computeIfAbsent(radius) {
            val size = (radius * 2 + 1)
            val list = ArrayList<Offset>(size * size * size)
            for (dx in -radius..radius) {
                for (dy in -radius..radius) {
                    for (dz in -radius..radius) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue
                        }
                        list.add(Offset(dx, dy, dz))
                    }
                }
            }
            list
        }
    }

    private fun blockKey(block: Block): Long {
        val x = block.x.toLong() and 0x3FFFFFF
        val z = block.z.toLong() and 0x3FFFFFF
        val y = block.y.toLong() and 0xFFF
        return (x shl 38) or (z shl 12) or y
    }

    private fun breakChain(player: Player, tool: ItemStack, blocks: List<Block>) {
        val drops: MutableList<ItemStack> = ArrayList()
        val world = player.world
        var canContinue = true
        blocks.forEach { block ->
            if (!canContinue) {
                return@forEach
            }
            if (block.type == Material.AIR) {
                return@forEach
            }
            if (VeinminerSettings.mergeItemDrops) {
                drops.addAll(block.getDrops(tool, player))
                block.type = Material.AIR
            } else {
                block.breakNaturally(tool)
            }
            if (VeinminerSettings.durabilityDecrease) {
                if (!damageTool(player, tool)) {
                    canContinue = false
                    return@forEach
                }
            }
        }
        if (VeinminerSettings.mergeItemDrops && drops.isNotEmpty()) {
            val location = blocks.first().location.add(0.5, 0.5, 0.5)
            drops.forEach { world.dropItemNaturally(location, it) }
        }
    }

    private fun damageTool(player: Player, tool: ItemStack): Boolean {
        if (adaptPlayer(player).gameMode == ProxyGameMode.CREATIVE) {
            return true
        }
        val meta = tool.itemMeta ?: return true
        if (tool.type.maxDurability <= 0) {
            return true
        }
        if (!meta.isUnbreakable && meta is Damageable) {
            meta.damage = meta.damage + 1
            tool.itemMeta = meta
            if (meta.damage >= tool.type.maxDurability) {
                player.inventory.setItemInMainHand(ItemStack(Material.AIR))
                return false
            }
            player.inventory.setItemInMainHand(tool)
        }
        return true
    }

    private fun hasCorrectTool(tool: ItemStack, player: Player, block: Block): Boolean {
        if (tool.type == Material.AIR) {
            return false
        }
        return block.getDrops(tool, player).isNotEmpty()
    }

    private fun checkEconomy(player: Player, count: Int): Boolean {
        val proxyPlayer = adaptPlayer(player)
        val pricePerBlock = VeinminerLimitService.getPricePerBlock(proxyPlayer)
        val cost = pricePerBlock * count
        if (cost <= 0) {
            return true
        }
        if (!isEconomySupported) {
            return true
        }
        val balance = player.getBalance()
        if (balance < cost) {
            VeinminerMessages.send(proxyPlayer, VeinminerSettings.messageMoneyNotEnough, mapOf("cost" to cost, "balance" to balance))
            return false
        }
        val response = player.withdrawBalance(cost)
        if (!response.transactionSuccess()) {
            VeinminerMessages.send(proxyPlayer, VeinminerSettings.messageMoneyFailed, mapOf("cost" to cost))
            return false
        }
        return true
    }

    private data class Offset(val x: Int, val y: Int, val z: Int)
}
