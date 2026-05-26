package com.pixlehavencore.util

import org.bukkit.Material
import org.bukkit.block.Container
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta

object InventoryUtils {

    fun compact(items: Array<ItemStack?>): Array<ItemStack?> {
        val grouped = mutableMapOf<String, MutableList<ItemStack>>()
        val unique = mutableListOf<ItemStack>()
        items.forEach { item ->
            if (item != null && item.type != Material.AIR && item.amount > 0) {
                if (shouldKeepUnique(item)) {
                    unique += item.clone()
                } else {
                    grouped.getOrPut(stackKey(item)) { mutableListOf() }.add(item.clone())
                }
            }
        }

        val ordered = mutableListOf<ItemStack>()
        ordered += unique
        grouped.toSortedMap().forEach { (_, stacks) ->
            val model = stacks.first()
            var remain = stacks.sumOf { it.amount }
            val max = model.maxStackSize.coerceAtLeast(1)
            while (remain > 0) {
                val piece = model.clone()
                val amount = minOf(remain, max)
                piece.amount = amount
                ordered += piece
                remain -= amount
            }
        }

        return resizeNullable(items.size, ordered.map { it.clone() })
    }

    fun canFitWithCompaction(inventory: Inventory, items: List<ItemStack>): Boolean {
        val clone = compact(inventory.contents)
        var work = clone
        items.forEach { incoming ->
            work = addToVirtual(work, incoming)
            if (work.isEmpty()) {
                return false
            }
        }
        return true
    }

    fun addToVirtual(contents: Array<ItemStack?>, incoming: ItemStack): Array<ItemStack?> {
        val clone = contents.map { it?.clone() }.toTypedArray()
        var remaining = incoming.amount

        for (i in clone.indices) {
            val current = clone[i]
            if (current != null && canStack(current, incoming) && current.amount < current.maxStackSize) {
                val take = minOf(current.maxStackSize - current.amount, remaining)
                current.amount += take
                remaining -= take
                if (remaining <= 0) {
                    return clone
                }
            }
        }

        for (i in clone.indices) {
            if (clone[i] == null || clone[i]?.type == Material.AIR) {
                val piece = incoming.clone()
                piece.amount = minOf(piece.maxStackSize.coerceAtLeast(1), remaining)
                clone[i] = piece
                remaining -= piece.amount
                if (remaining <= 0) {
                    return clone
                }
            }
        }

        return emptyArray()
    }

    fun resizeNullable(size: Int, list: List<ItemStack?>): Array<ItemStack?> {
        val array = arrayOfNulls<ItemStack>(size)
        for (i in 0 until minOf(size, list.size)) {
            array[i] = list[i]?.clone()
        }
        return array
    }

    fun stackKey(item: ItemStack): String {
        return buildString {
            append(item.type.name)
            append('#')
            append(item.itemMeta?.asString ?: "")
            append('#')
            append(item.enchantments.entries.sortedBy { it.key.key.key }.joinToString(";") { "${it.key.key.key}:${it.value}" })
        }
    }

    private fun canStack(a: ItemStack, b: ItemStack): Boolean {
        if (shouldKeepUnique(a) || shouldKeepUnique(b)) {
            return false
        }
        return a.isSimilar(b)
    }

    private fun shouldKeepUnique(item: ItemStack): Boolean {
        if (item.maxStackSize <= 1) {
            return true
        }
        val meta = item.itemMeta
        return meta is BlockStateMeta && meta.blockState is Container
    }
}
