package pers.neige.neigeitems.manager;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.tabooproject.baikiruto.core.Baikiruto;
import pers.neige.neigeitems.item.ItemInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * NeigeItems API 兼容桥：将调用映射到 Baikiruto。
 */
public enum ItemManager {

    INSTANCE;

    public ItemInfo isNiItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        String itemId = getApiItemId(itemStack);
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        return new ItemInfo(itemId, itemStack);
    }

    public boolean hasItem(String itemId) {
        String normalized = normalizeItemId(itemId);
        if (normalized == null) {
            return false;
        }
        return getApiItem(normalized) != null;
    }

    public ItemStack getItemStack(String itemId) {
        return getItemStack(itemId, (OfflinePlayer) null);
    }

    public ItemStack getItemStack(String itemId, int amount) {
        ItemStack stack = getItemStack(itemId, (OfflinePlayer) null);
        if (stack == null) {
            return null;
        }
        stack.setAmount(Math.max(1, Math.min(amount, stack.getMaxStackSize())));
        return stack;
    }

    public ItemStack getItemStack(String itemId, OfflinePlayer player) {
        String normalized = normalizeItemId(itemId);
        if (normalized == null) {
            return null;
        }

        Map<String, Object> context = new HashMap<>();
        if (player != null) {
            context.put("player", player);
            context.put("player_name", player.getName() == null ? "" : player.getName());
            context.put("player_uuid", player.getUniqueId().toString());
        }

        return runSafe(() -> Baikiruto.INSTANCE.api().buildItem(normalized, context));
    }

    public ItemStack getItemStack(String itemId, int amount, OfflinePlayer player) {
        ItemStack stack = getItemStack(itemId, player);
        if (stack == null) {
            return null;
        }
        stack.setAmount(Math.max(1, Math.min(amount, stack.getMaxStackSize())));
        return stack;
    }

    private Object getApiItem(String itemId) {
        return runSafe(() -> Baikiruto.INSTANCE.api().getItem(itemId));
    }

    private String getApiItemId(ItemStack itemStack) {
        return runSafe(() -> Baikiruto.INSTANCE.api().getItemId(itemStack));
    }

    private String normalizeItemId(String rawId) {
        if (rawId == null) {
            return null;
        }
        String trimmed = rawId.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String lowered = trimmed.toLowerCase();
        if (lowered.startsWith("ni:")
            || lowered.startsWith("neigeitems:")
            || lowered.startsWith("neige:")
            || lowered.startsWith("bai:")
            || lowered.startsWith("bk:")
            || lowered.startsWith("baikiruto:")) {
            return trimmed.substring(trimmed.indexOf(':') + 1).trim();
        }
        return trimmed;
    }

    private <T> T runSafe(SupplierEx<T> supplier) {
        try {
            if (Bukkit.getPluginManager().getPlugin("Baikiruto") == null) {
                return null;
            }
            return supplier.get();
        } catch (Throwable ignored) {
            return null;
        }
    }

    @FunctionalInterface
    private interface SupplierEx<T> {
        T get() throws Throwable;
    }
}
