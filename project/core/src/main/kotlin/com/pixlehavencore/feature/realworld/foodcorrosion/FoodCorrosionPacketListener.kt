package com.pixlehavencore.feature.realworld.foodcorrosion

import com.github.retrooper.packetevents.event.PacketListener
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import com.pixlehavencore.bridge.TextBridge
import net.kyori.adventure.text.Component
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

        val remainingDays = FoodCorrosionEngine.computeRemainingDays(item)
        val displayedDays = FoodCorrosionEngine.getDisplayedDays(item)

        // 天数未变化则跳过，避免频繁刷新 lore
        if (displayedDays != null && displayedDays == remainingDays) return null

        // 记录本次显示的天数
        FoodCorrosionEngine.setDisplayedDays(item, remainingDays)

        val text = FoodCorrosionEngine.buildCorrosionLoreText(remainingDays)

        // 需要重新获取 lore（setDisplayedDays 修改了 itemMeta）
        val existingLore = TextBridge.getLore(item) ?: emptyList()
        val filtered = if (existingLore.isNotEmpty()) {
            val lastLine = TextBridge.toPlain(existingLore.last())
            if (lastLine.contains("过期时间")) {
                existingLore.dropLast(1)
            } else {
                existingLore
            }
        } else {
            existingLore
        }

        val newLore = filtered.toMutableList()
        newLore.add(TextUtils.parseItem(text))
        TextBridge.setLore(item, newLore)
        return item
    }
}
