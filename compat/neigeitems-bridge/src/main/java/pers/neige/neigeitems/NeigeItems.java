package pers.neige.neigeitems;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * NeigeItems 兼容桥插件入口。
 *
 * 目标：
 * 1) 以插件名 "NeigeItems" 被其他插件识别。
 * 2) 提供最常见的 NI API 类与方法，将请求转发到 Baikiruto。
 */
public final class NeigeItems extends JavaPlugin {

    private static NeigeItems instance;

    public static NeigeItems getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        Plugin bk = Bukkit.getPluginManager().getPlugin("Baikiruto");
        if (bk == null || !bk.isEnabled()) {
            getLogger().warning("[NeigeItems-Bridge] 未检测到 Baikiruto，已禁用兼容桥。请先安装并启用 Baikiruto。");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("[NeigeItems-Bridge] 已启用，所有 NI API 请求将映射至 Baikiruto。\n"
            + "[NeigeItems-Bridge] 提示：请勿与真实 NeigeItems 同时安装。\n");
    }

    @Override
    public void onDisable() {
        if (instance == this) {
            instance = null;
        }
    }
}
