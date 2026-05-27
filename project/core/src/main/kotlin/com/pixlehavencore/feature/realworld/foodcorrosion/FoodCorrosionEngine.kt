package com.pixlehavencore.feature.realworld.foodcorrosion

import com.pixlehavencore.util.PlaceholderUtils.resolvePlaceholders
import com.pixlehavencore.util.TextUtils
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

object FoodCorrosionEngine {

    private val CREATION_TIME_KEY = NamespacedKey("phcore", "food_creation_time")
    private val DISPLAYED_DAYS_KEY = NamespacedKey("phcore", "food_displayed_days")
    private val OLD_CORROSION_KEY = NamespacedKey("phcore", "food_corrosion")
    private const val SECONDS_PER_DAY = 86400L

    fun isCorrosiveFood(item: ItemStack): Boolean {
        if (item.type.isAir) return false
        if (!item.type.isEdible) return false
        if (item.type.name in FoodCorrosionSettings.excludedItems) return false
        return true
    }

    fun getCreationTime(item: ItemStack): Long? {
        val meta = item.itemMeta ?: return null
        return meta.persistentDataContainer.get(CREATION_TIME_KEY, PersistentDataType.LONG)
    }

    fun setCreationTimeIfAbsent(item: ItemStack) {
        val meta = item.itemMeta ?: return
        if (meta.persistentDataContainer.has(CREATION_TIME_KEY, PersistentDataType.LONG)) return
        meta.persistentDataContainer.set(CREATION_TIME_KEY, PersistentDataType.LONG, System.currentTimeMillis())
        meta.persistentDataContainer.remove(OLD_CORROSION_KEY)
        item.itemMeta = meta
    }

    fun getDisplayedDays(item: ItemStack): Int? {
        val meta = item.itemMeta ?: return null
        return meta.persistentDataContainer.get(DISPLAYED_DAYS_KEY, PersistentDataType.INTEGER)
    }

    fun setDisplayedDays(item: ItemStack, days: Int) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(DISPLAYED_DAYS_KEY, PersistentDataType.INTEGER, days)
        item.itemMeta = meta
    }

    fun computeRemainingDays(item: ItemStack): Int {
        val creationMs = getCreationTime(item) ?: return FoodCorrosionSettings.defaultDays
        val elapsedMs = System.currentTimeMillis() - creationMs
        val elapsedDays = (elapsedMs / 1000.0 / SECONDS_PER_DAY)
        val maxDays = getItemDays(item.type)
        return (maxDays - elapsedDays).toInt().coerceIn(0, maxDays)
    }

    fun getItemDays(material: Material): Int {
        return FoodCorrosionSettings.itemDays[material.name] ?: FoodCorrosionSettings.defaultDays
    }

    fun tickPlayer(player: Player) {
        if (!FoodCorrosionSettings.enabled) return
        val contents = player.inventory.contents
        for ((slot, item) in contents.withIndex()) {
            if (item == null || item.type.isAir) continue
            if (!isCorrosiveFood(item)) continue
            setCreationTimeIfAbsent(item)
            if (computeRemainingDays(item) <= 0) {
                convertToExpiredItem(player, slot, item)
            }
        }
    }

    fun convertToExpiredItem(player: Player, slot: Int, item: ItemStack) {
        val amount = item.amount
        val expiredMaterial = Material.matchMaterial(FoodCorrosionSettings.expiredItem)
            ?: Material.ROTTEN_FLESH
        val expiredItem = ItemStack(expiredMaterial, amount)
        player.inventory.setItem(slot, expiredItem)
        val message = FoodCorrosionSettings.conversionMessage
        if (message.isNotEmpty()) {
            player.sendMessage(TextUtils.parse(message))
        }
    }

    fun corrosionColor(days: Int): String {
        val maxDays = FoodCorrosionSettings.defaultDays
        val ratio = if (maxDays > 0) days.toDouble() / maxDays else 0.0
        return when {
            ratio > 0.50 -> "&a"
            ratio > 0.25 -> "&e"
            ratio > 0.10 -> "&c"
            else -> "&4"
        }
    }

    fun buildCorrosionLoreText(days: Int): String {
        val color = corrosionColor(days)
        return FoodCorrosionSettings.loreFormat
            .resolvePlaceholders(
                "{days}" to days.toString(),
                "{color}" to color,
            )
    }
}
