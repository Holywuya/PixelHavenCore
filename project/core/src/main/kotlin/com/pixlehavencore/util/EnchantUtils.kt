package com.pixlehavencore.util

import org.bukkit.inventory.ItemStack

object EnchantUtils {

    fun getLevel(item: ItemStack?, key: String): Int {
        val stack = item ?: return 0
        return stack.enchantments.entries.firstOrNull {
            it.key.key.key.equals(key, ignoreCase = true)
        }?.value ?: 0
    }
}
