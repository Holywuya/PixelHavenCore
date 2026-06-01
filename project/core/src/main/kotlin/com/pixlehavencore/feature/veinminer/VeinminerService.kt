package com.pixlehavencore.feature.veinminer

import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import com.pixlehavencore.util.InventoryUtils
import taboolib.common.platform.ProxyGameMode
import taboolib.platform.util.modifyMeta
import taboolib.common.platform.ProxyPlayer
import taboolib.common.platform.function.adaptPlayer
import taboolib.platform.util.submit as submitOnLocation
import kotlin.math.min
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import taboolib.platform.util.PlayerSessionMap

object VeinminerService {

    private val cooldowns = PlayerSessionMap<Long>({ 0L })
    private val mining = PlayerSessionMap<Boolean>({ false })
    private val lastConsumeTime = PlayerSessionMap<Long>({ 0L })
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
        if (mining[proxyPlayer.uniqueId] == true) {
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
        val maxNodes = min(VeinminerSettings.maxChain, remaining + 1)
        val chain = collectChain(source, player, tool, maxNodes)
        if (chain.size <= 1) {
            return false
        }
        val consumeAmount = chain.size - 1
        // 防止重复消耗（Folia 多线程环境下可能触发多次）
        val now = System.currentTimeMillis()
        val lastConsume = lastConsumeTime[proxyPlayer.uniqueId] ?: 0L
        if (now - lastConsume < 100) {
            return false
        }
        lastConsumeTime[proxyPlayer.uniqueId] = now
        if (!VeinminerLimitService.consume(proxyPlayer, consumeAmount)) {
            VeinminerMessages.send(proxyPlayer, VeinminerSettings.messageLimitDenied)
            return false
        }
        mining[proxyPlayer.uniqueId] = true
        try {
            breakChain(player, tool, chain)
        } finally {
            mining[proxyPlayer.uniqueId] = false
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

    private data class MergedDrop(
        val model: ItemStack,
        var totalAmount: Int
    )

    private fun breakChain(player: Player, tool: ItemStack, blocks: List<Block>) {
        val mergedDrops = LinkedHashMap<String, MergedDrop>()
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
                mergeDropsInto(block.getDrops(tool, player), mergedDrops)
                // Folia: block.type 设置必须在方块所属区域线程上执行
                // 当前从 BlockBreakEvent 处理器调用，事件在区域线程上触发，同一区块内的方块操作是安全的
                block.type = Material.AIR
            } else {
                // Folia: 同上，block.breakNaturally 在区域线程上执行
                block.breakNaturally(tool)
            }
            if (VeinminerSettings.durabilityDecrease) {
                if (!damageTool(player, tool)) {
                    canContinue = false
                    return@forEach
                }
            }
        }
        if (mergedDrops.isNotEmpty()) {
            val location = blocks.first().location.add(0.5, 0.5, 0.5)
            // Folia: dropItemNaturally 需要在位置所属的区域线程上执行
            location.submitOnLocation {
                mergedDrops.values.forEach { merged ->
                    val maxStackSize = merged.model.maxStackSize.coerceAtLeast(1)
                    var remaining = merged.totalAmount
                    while (remaining > 0) {
                        val piece = merged.model.clone()
                        val amount = min(remaining, maxStackSize)
                        piece.amount = amount
                        world.dropItemNaturally(location, piece)
                        remaining -= amount
                    }
                }
            }
        }
    }

    private fun mergeDropsInto(drops: Collection<ItemStack>, mergedDrops: MutableMap<String, MergedDrop>) {
        drops.forEach { drop ->
            if (drop.type == Material.AIR || drop.amount <= 0) {
                return@forEach
            }
            val key = InventoryUtils.stackKey(drop)
            val bucket = mergedDrops[key]
            if (bucket == null) {
                mergedDrops[key] = MergedDrop(drop.clone(), drop.amount)
            } else {
                bucket.totalAmount += drop.amount
            }
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
            tool.modifyMeta<Damageable> {
                damage += 1
            }
            if ((meta as Damageable).damage + 1 >= tool.type.maxDurability) {
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

    private data class Offset(val x: Int, val y: Int, val z: Int)
}
