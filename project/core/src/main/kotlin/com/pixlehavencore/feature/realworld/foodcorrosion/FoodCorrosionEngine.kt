package com.pixlehavencore.feature.realworld.foodcorrosion

import com.pixlehavencore.util.PlaceholderUtils.resolvePlaceholders
import com.pixlehavencore.util.TextUtils
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import taboolib.platform.util.modifyMeta

object FoodCorrosionEngine {

    private val CREATION_DAY_KEY = NamespacedKey("phcore", "food_creation_day")
    private val DISPLAYED_DAYS_KEY = NamespacedKey("phcore", "food_displayed_days")
    private val OLD_CREATION_TIME_KEY = NamespacedKey("phcore", "food_creation_time")
    private val OLD_CORROSION_KEY = NamespacedKey("phcore", "food_corrosion")

    fun getCurrentGameDay(): Int {
        val world = Bukkit.getWorlds().firstOrNull() ?: return 0
        return (world.fullTime / 24000).toInt()
    }

    fun isCorrosiveFood(item: ItemStack): Boolean {
        if (item.type.isAir) return false
        if (!item.type.isEdible) return false
        if (item.type.name in FoodCorrosionSettings.excludedItems) return false
        return true
    }

    fun getCreationDay(item: ItemStack): Int? {
        val meta = item.itemMeta ?: return null
        val pdc = meta.persistentDataContainer
        val day = pdc.get(CREATION_DAY_KEY, PersistentDataType.INTEGER)
            ?: pdc.get(OLD_CREATION_TIME_KEY, PersistentDataType.LONG)?.toInt()?.also {
                item.modifyMeta<ItemMeta> {
                    persistentDataContainer.remove(OLD_CREATION_TIME_KEY)
                    persistentDataContainer.set(CREATION_DAY_KEY, PersistentDataType.INTEGER, it)
                }
            }
        return day
    }

    fun setCreationTimeIfAbsent(item: ItemStack) {
        val meta = item.itemMeta ?: return
        val pdc = meta.persistentDataContainer
        if (pdc.has(CREATION_DAY_KEY, PersistentDataType.INTEGER)) return
        item.modifyMeta<ItemMeta> {
            if (persistentDataContainer.has(OLD_CREATION_TIME_KEY, PersistentDataType.LONG)) {
                val oldDay = persistentDataContainer.get(OLD_CREATION_TIME_KEY, PersistentDataType.LONG)?.toInt() ?: getCurrentGameDay()
                persistentDataContainer.remove(OLD_CREATION_TIME_KEY)
                persistentDataContainer.set(CREATION_DAY_KEY, PersistentDataType.INTEGER, oldDay)
            } else {
                persistentDataContainer.set(CREATION_DAY_KEY, PersistentDataType.INTEGER, getCurrentGameDay())
            }
            persistentDataContainer.remove(OLD_CORROSION_KEY)
        }
    }

    fun getDisplayedDays(item: ItemStack): Int? {
        val meta = item.itemMeta ?: return null
        return meta.persistentDataContainer.get(DISPLAYED_DAYS_KEY, PersistentDataType.INTEGER)
    }

    fun setDisplayedDays(item: ItemStack, days: Int) {
        item.modifyMeta<ItemMeta> {
            persistentDataContainer.set(DISPLAYED_DAYS_KEY, PersistentDataType.INTEGER, days)
        }
    }

    fun computeRemainingDays(item: ItemStack): Int {
        val creationDay = getCreationDay(item) ?: return FoodCorrosionSettings.defaultDays
        val elapsedDays = getCurrentGameDay() - creationDay
        val maxDays = getItemDays(item.type)
        return (maxDays - elapsedDays).coerceIn(0, maxDays)
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
            player.sendMessage(TextUtils.parseMiniMessage(message))
        }
    }

    fun corrosionColor(remainingDays: Int, shelfLife: Int): String {
        val ratio = if (shelfLife > 0) remainingDays.toDouble() / shelfLife else 0.0
        return when {
            ratio > 0.50 -> "<green>"
            ratio > 0.25 -> "<yellow>"
            ratio > 0.10 -> "<red>"
            else -> "<dark_red>"
        }
    }

    fun buildCorrosionLoreText(remainingDays: Int, shelfLife: Int): String {
        val color = corrosionColor(remainingDays, shelfLife)
        return FoodCorrosionSettings.loreFormat
            .resolvePlaceholders(
                "{days}" to remainingDays.toString(),
                "{color}" to color,
            )
    }
}
