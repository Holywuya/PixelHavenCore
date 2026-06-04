package com.pixlehavencore.bridge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Adventure Component + Paper ItemMeta 操作封装。
 * <p>
 * core 模块中所有 Component / ItemMeta 操作应经过此类，
 * 避免 Kotlin 类型推断问题并集中管理序列化逻辑。
 */
public final class TextBridge {

    private static final LegacyComponentSerializer LEGACY_SECTION =
            LegacyComponentSerializer.legacySection();
    private static final LegacyComponentSerializer LEGACY_AMPERSAND =
            LegacyComponentSerializer.legacyAmpersand();
    private static final PlainTextComponentSerializer PLAIN =
            PlainTextComponentSerializer.plainText();
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private TextBridge() {}

    // ══════════════════════════════════════════
    //  Component 序列化
    // ══════════════════════════════════════════

    /** § 颜色码 → Component */
    public static Component fromLegacy(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        return LEGACY_SECTION.deserialize(text);
    }

    /** & 颜色码 → Component */
    public static Component fromAmpersand(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        return LEGACY_AMPERSAND.deserialize(text);
    }

    /** MiniMessage 标签 → Component */
    public static Component fromMiniMessage(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        return MINI_MESSAGE.deserialize(text);
    }

    /** Component → § 颜色码 */
    public static String toLegacy(Component component) {
        if (component == null) return "";
        return LEGACY_SECTION.serialize(component);
    }

    /** Component → 纯文本（无格式码） */
    public static String toPlain(Component component) {
        if (component == null) return "";
        return PLAIN.serialize(component);
    }

    /** 去除 § 颜色码 */
    public static String stripLegacyColors(String text) {
        if (text == null) return "";
        return text.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }

    /** & 颜色码列表 → Component 列表 */
    public static List<Component> fromAmpersandList(List<String> lines) {
        if (lines == null || lines.isEmpty()) return List.of();
        List<Component> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            if (line != null) {
                result.add(fromAmpersand(line));
            }
        }
        return result;
    }

    /** Component 列表 → § 颜色码列表 */
    public static List<String> toLegacyList(List<Component> components) {
        if (components == null || components.isEmpty()) return List.of();
        List<String> result = new ArrayList<>(components.size());
        for (Component line : components) {
            if (line != null) {
                result.add(toLegacy(line));
            }
        }
        return result;
    }

    /** Component 列表 → 纯文本列表 */
    public static List<String> toPlainList(List<Component> components) {
        if (components == null || components.isEmpty()) return List.of();
        List<String> result = new ArrayList<>(components.size());
        for (Component line : components) {
            if (line != null) {
                result.add(toPlain(line));
            }
        }
        return result;
    }

    // ══════════════════════════════════════════
    //  物品 Meta（Paper editMeta 模式）
    // ══════════════════════════════════════════

    /** 获取物品 displayName Component */
    @Nullable
    public static Component getDisplayName(ItemStack item) {
        if (item == null || item.isEmpty()) return null;
        ItemMeta meta = item.getItemMeta();
        return meta != null ? meta.customName() : null;
    }

    /** 获取物品 displayName 为 § 颜色码字符串 */
    @Nullable
    public static String getDisplayNameAsLegacy(ItemStack item) {
        Component name = getDisplayName(item);
        return name != null ? toLegacy(name) : null;
    }

    /** 设置 displayName（Component），强制无斜体 */
    public static void setDisplayName(ItemStack item, Component name) {
        if (item == null || item.isEmpty() || name == null) return;
        item.editMeta(meta ->
                meta.customName(name.decoration(TextDecoration.ITALIC, false)));
    }

    /** 设置 displayName（& 颜色码） */
    public static void setDisplayName(ItemStack item, String legacyName) {
        if (legacyName == null) return;
        setDisplayName(item, fromAmpersand(legacyName));
    }

    /** 获取物品 lore Component 列表 */
    @Nullable
    public static List<Component> getLore(ItemStack item) {
        if (item == null || item.isEmpty()) return null;
        ItemMeta meta = item.getItemMeta();
        return meta != null ? meta.lore() : null;
    }

    /** 获取物品 lore 为 § 颜色码字符串列表 */
    @Nullable
    public static List<String> getLoreAsLegacy(ItemStack item) {
        List<Component> lore = getLore(item);
        return lore != null ? toLegacyList(lore) : null;
    }

    /** 获取物品 lore 为纯文本列表 */
    @Nullable
    public static List<String> getLoreAsPlain(ItemStack item) {
        List<Component> lore = getLore(item);
        return lore != null ? toPlainList(lore) : null;
    }

    /** 设置 lore（Component 列表），强制无斜体 */
    public static void setLore(ItemStack item, List<Component> lore) {
        if (item == null || item.isEmpty() || lore == null) return;
        List<Component> noItalic = new ArrayList<>(lore.size());
        for (Component line : lore) {
            if (line != null) {
                noItalic.add(line.decoration(TextDecoration.ITALIC, false));
            }
        }
        item.editMeta(meta -> meta.lore(noItalic));
    }

    /** 设置 lore（& 颜色码列表） */
    public static void setLoreFromAmpersand(ItemStack item, List<String> lines) {
        setLore(item, fromAmpersandList(lines));
    }

    // ══════════════════════════════════════════
    //  玩家消息
    // ══════════════════════════════════════════

    /** 发送 & 颜色码消息 */
    public static void sendMessage(Player player, String legacyMessage) {
        if (player == null || legacyMessage == null) return;
        player.sendMessage(fromAmpersand(legacyMessage));
    }

    /** 发送 Component 消息 */
    public static void sendMessage(Player player, Component message) {
        if (player == null || message == null) return;
        player.sendMessage(message);
    }

    /** 发送 ActionBar（Component） */
    public static void sendActionBar(Player player, Component message) {
        if (player == null || message == null) return;
        player.sendActionBar(message);
    }

    /** 发送 ActionBar（& 颜色码） */
    public static void sendActionBar(Player player, String legacyMessage) {
        if (player == null || legacyMessage == null) return;
        player.sendActionBar(fromAmpersand(legacyMessage));
    }

    // ══════════════════════════════════════════
    //  Entity 操作
    // ══════════════════════════════════════════

    /** 获取实体自定义名称 */
    @Nullable
    public static Component getEntityCustomName(Entity entity) {
        if (entity == null) return null;
        return entity.customName();
    }

    /** 设置实体自定义名称 */
    public static void setEntityCustomName(Entity entity, Component name) {
        if (entity == null) return;
        entity.customName(name);
    }
}
