package com.pixlehavencore.bridge;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

/**
 * PersistentDataContainer 便捷操作封装。
 */
public final class PdcBridge {

    private PdcBridge() {}

    @Nullable
    public static <T> T get(ItemStack item, NamespacedKey key, PersistentDataType<?, T> type) {
        if (item == null || item.isEmpty() || key == null || type == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(key, type);
    }

    public static <T, Z> void set(ItemStack item, NamespacedKey key, PersistentDataType<T, Z> type, Z value) {
        if (item == null || item.isEmpty() || key == null || type == null || value == null) return;
        item.editMeta(meta -> meta.getPersistentDataContainer().set(key, type, value));
    }

    public static boolean has(ItemStack item, NamespacedKey key, PersistentDataType<?, ?> type) {
        if (item == null || item.isEmpty() || key == null || type == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(key, type);
    }

    public static void remove(ItemStack item, NamespacedKey key) {
        if (item == null || item.isEmpty() || key == null) return;
        item.editMeta(meta -> meta.getPersistentDataContainer().remove(key));
    }
}
