package com.pixlehavencore.feature.realworld.foodcorrosion

import com.pixlehavencore.util.PlaceholderUtils.resolvePlaceholders
import com.pixlehavencore.util.TextUtils
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

object FoodCorrosionEngine {

    private val NAMESPACED_KEY = NamespacedKey("phcore", "food_corrosion")
    private val DISPLAYED_DAYS_KEY = NamespacedKey("phcore", "food_displayed_days")

    fun isCorrosiveFood(item: ItemStack): Boolean {
        if (item.type.isAir) return false
        if (!item.type.isEdible) return false
        if (item.type.name in FoodCorrosionSettings.excludedItems) return false
        return true
    }

    fun getCorrosionValue(item: ItemStack): Int {
        val meta = item.itemMeta ?: return 0
        return meta.persistentDataContainer.get(NAMESPACED_KEY, PersistentDataType.INTEGER) ?: 0
    }

    fun setCorrosionValue(item: ItemStack, value: Int) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(NAMESPACED_KEY, PersistentDataType.INTEGER, value)
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

    fun computeRemainingDays(current: Int, max: Int): Int {
        val totalDays = FoodCorrosionSettings.totalDays
        return ((max - current).toDouble() / max * totalDays).toInt().coerceIn(0, totalDays)
    }

    fun getCorrosionRate(material: Material): Int {
        return FoodCorrosionSettings.itemRates[material.name] ?: FoodCorrosionSettings.defaultRate
    }

    fun tickPlayer(player: Player) {
        if (!FoodCorrosionSettings.enabled) return
        val maxCorrosion = FoodCorrosionSettings.maxCorrosion
        val contents = player.inventory.contents
        for ((slot, item) in contents.withIndex()) {
            if (item == null || item.type.isAir) continue
            if (!isCorrosiveFood(item)) continue
            val current = getCorrosionValue(item)
            if (current >= maxCorrosion) continue
            val rate = getCorrosionRate(item.type)
            val newValue = (current + rate).coerceAtMost(maxCorrosion)
            setCorrosionValue(item, newValue)
            if (newValue >= maxCorrosion) {
                convertToRottenFlesh(player, slot, item)
            }
        }
    }

    fun convertToRottenFlesh(player: Player, slot: Int, item: ItemStack) {
        val amount = item.amount
        val rottenFlesh = ItemStack(Material.ROTTEN_FLESH, amount)
        player.inventory.setItem(slot, rottenFlesh)
        val message = FoodCorrosionSettings.conversionMessage
        if (message.isNotEmpty()) {
            player.sendMessage(TextUtils.parse(message))
        }
    }

    fun corrosionColor(days: Int): String {
        val totalDays = FoodCorrosionSettings.totalDays
        val ratio = if (totalDays > 0) days.toDouble() / totalDays else 0.0
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
