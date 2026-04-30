package pers.neige.neigeitems.item;

import org.bukkit.inventory.ItemStack;

/**
 * NI 的最小兼容 ItemInfo。
 * 仅提供常见插件会用到的字段和方法。
 */
public class ItemInfo {

    private final String id;
    private final ItemStack itemStack;

    public ItemInfo(String id, ItemStack itemStack) {
        this.id = id;
        this.itemStack = itemStack;
    }

    public String getId() {
        return id;
    }

    public String getItemId() {
        return id;
    }

    public ItemStack getItemStack() {
        return itemStack == null ? null : itemStack.clone();
    }
}
