package com.pixlehavencore.feature.realworld.foodcorrosion

import com.github.retrooper.packetevents.event.PacketListener
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import com.pixlehavencore.bridge.TextBridge
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import com.pixlehavencore.util.TextUtils

object FoodCorrosionPacketListener : PacketListener {

    override fun onPacketSend(event: PacketSendEvent) {
        if (!FoodCorrosionSettings.enabled) return

        when (event.packetType) {
            PacketType.Play.Server.SET_SLOT -> handleSetSlot(event)
            PacketType.Play.Server.WINDOW_ITEMS -> handleWindowItems(event)
            else -> return
        }
    }

    private fun handleSetSlot(event: PacketSendEvent) {
        val wrapper = WrapperPlayServerSetSlot(event)
        val peItem = wrapper.item ?: return

        val bukkitItem = SpigotConversionUtil.toBukkitItemStack(peItem)
        if (!FoodCorrosionEngine.isCorrosiveFood(bukkitItem)) return
        val modified = appendCorrosionLore(bukkitItem) ?: return
        wrapper.item = SpigotConversionUtil.fromBukkitItemStack(modified)
        event.markForReEncode(true)
    }

    private fun handleWindowItems(event: PacketSendEvent) {
        val wrapper = WrapperPlayServerWindowItems(event)
        val items = wrapper.items
        var changed = false
        val newItems = items.map { peItem ->
            // 快速检查：如果物品类型不是食物，跳过转换
            val materialName = peItem.type.name.toString()
            val material = Material.matchMaterial(materialName) ?: return@map peItem
            if (!material.isEdible) return@map peItem
            
            val bukkitItem = SpigotConversionUtil.toBukkitItemStack(peItem)
            if (!FoodCorrosionEngine.isCorrosiveFood(bukkitItem)) return@map peItem
            val modified = appendCorrosionLore(bukkitItem)
            if (modified != null) {
                changed = true
                SpigotConversionUtil.fromBukkitItemStack(modified)
            } else {
                peItem
            }
        }
        if (changed) {
            wrapper.setItems(newItems)
            event.markForReEncode(true)
        }
    }

    private fun appendCorrosionLore(item: ItemStack): ItemStack? {
        val meta = item.itemMeta ?: return null

        // 检查是否已有过期时间 lore，避免重复添加
        val existingLore = TextBridge.getLore(item) ?: emptyList()
        val lastLine = existingLore.lastOrNull()?.let { TextBridge.toPlain(it) }
        if (lastLine != null && lastLine.contains("过期时间")) return null

        val remainingDays = FoodCorrosionEngine.computeRemainingDays(item)
        val shelfLife = FoodCorrosionEngine.getItemDays(item.type)
        val text = FoodCorrosionEngine.buildCorrosionLoreText(remainingDays, shelfLife)

        val newLore = existingLore.toMutableList()
        newLore.add(TextUtils.parseMiniMessage(text).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false))
        TextBridge.setLore(item, newLore)
        return item
    }
}
