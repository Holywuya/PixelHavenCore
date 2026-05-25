package com.pixlehavencore.feature.durability

import com.pixlehavencore.util.PlaceholderUtils.resolvePlaceholders
import com.github.retrooper.packetevents.event.PacketListener
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import com.pixlehavencore.util.TextUtils

object DurabilityPacketListener : PacketListener {

    private val plainText = PlainTextComponentSerializer.plainText()

    override fun onPacketSend(event: PacketSendEvent) {
        if (!DurabilitySettings.enabled) return

        when (event.packetType) {
            PacketType.Play.Server.SET_SLOT -> handleSetSlot(event)
            PacketType.Play.Server.WINDOW_ITEMS -> handleWindowItems(event)
            else -> return
        }
    }

    private fun handleSetSlot(event: PacketSendEvent) {
        val wrapper = WrapperPlayServerSetSlot(event)
        val peItem = wrapper.item ?: return
        if (!peItem.isDamageableItem || peItem.maxDamage <= 0) return

        val bukkitItem = SpigotConversionUtil.toBukkitItemStack(peItem)
        val modified = appendDurabilityLore(bukkitItem) ?: return
        wrapper.item = SpigotConversionUtil.fromBukkitItemStack(modified)
        event.markForReEncode(true)
    }

    private fun handleWindowItems(event: PacketSendEvent) {
        val wrapper = WrapperPlayServerWindowItems(event)
        val items = wrapper.items
        var changed = false
        val newItems = items.map { peItem ->
            if (!peItem.isDamageableItem || peItem.maxDamage <= 0) return@map peItem
            val bukkitItem = SpigotConversionUtil.toBukkitItemStack(peItem)
            val modified = appendDurabilityLore(bukkitItem)
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

    private fun appendDurabilityLore(item: ItemStack): ItemStack? {
        val meta = item.itemMeta ?: return null
        if (meta !is Damageable) return null
        val maxDurability = item.type.maxDurability
        if (maxDurability <= 0) return null

        val currentDamage = meta.damage
        val currentDurability = maxDurability - currentDamage
        val color = durabilityColor(currentDurability.toInt(), maxDurability.toInt())

        val format = DurabilitySettings.loreFormat
        val text = format
            .resolvePlaceholders(
                "{current}" to currentDurability.toString(),
                "{max}" to maxDurability.toString(),
                "{color}" to color
            )

        val existingLore = meta.lore() ?: mutableListOf()
        val filtered = if (existingLore.isNotEmpty()) {
            val lastLine = plainText.serialize(existingLore.last())
            if (lastLine.contains("耐久")) {
                existingLore.dropLast(1)
            } else {
                existingLore
            }
        } else {
            existingLore
        }

        val newLore = filtered.toMutableList()
        newLore.add(TextUtils.parseItem(text))
        meta.lore(newLore)
        item.itemMeta = meta
        return item
    }

    private fun durabilityColor(current: Int, max: Int): String {
        if (max <= 0) return "&a"
        val percent = current.toDouble() / max
        return when {
            percent > 0.5 -> "&a"
            percent > 0.25 -> "&e"
            percent > 0.1 -> "&c"
            else -> "&4"
        }
    }

}
