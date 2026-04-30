package com.pixlehavencore.util

import io.lumine.mythic.bukkit.MythicBukkit
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning

/**
 * MythicMobs 物品创建工具类。
 *
 * 支持的 spec 前缀格式：
 *   - mm:物品ID
 *   - mythic:物品ID
 *   - mythicmobs:物品ID
 */
object MythicItemsUtil {

    private const val MM_PLUGIN_NAME = "MythicMobs"

    // 懒加载：首次访问时检测一次 MythicMobs 是否可用，之后不再重复检测
    private val available: Boolean by lazy { detectMythicMobs() }

    private fun detectMythicMobs(): Boolean {
        val plugin = Bukkit.getPluginManager().getPlugin(MM_PLUGIN_NAME)
        if (plugin == null || !plugin.isEnabled) {
            info("[ItemLibrary] 未检测到 MythicMobs，跳过 MM 物品集成")
            return false
        }
        return runCatching {
            // 尝试访问 ItemManager 以验证 API 可用
            MythicBukkit.inst().itemManager != null
            info("[ItemLibrary] 已接入 MythicMobs 物品库")
            true
        }.onFailure { ex ->
            warning("[ItemLibrary] MythicMobs 物品接入失败: ${ex.message}")
        }.getOrDefault(false)
    }

    fun isAvailable(): Boolean = available

    /**
     * 判断指定 ID 的 MythicMobs 物品是否已注册。
     */
    fun hasItem(itemId: String): Boolean {
        if (!available) return false
        val id = itemId.trim()
        if (id.isEmpty()) return false
        return runCatching {
            MythicBukkit.inst().itemManager.getItem(id).isPresent
        }.getOrDefault(false)
    }

    /**
     * 按 ID 获取 MythicMobs 物品实例。
     * @param itemId MythicMobs 物品 ID
     * @param amount 数量，默认 1
     */
    fun getItem(itemId: String, amount: Int = 1): ItemStack? {
        if (!available) return null
        val id = itemId.trim()
        if (id.isEmpty()) return null
        return runCatching {
            MythicBukkit.inst().itemManager.getItemStack(id, amount)
        }.onFailure {
            warning("[ItemLibrary] 获取 MM 物品失败（ID=$id）: ${it.message}")
        }.getOrNull()
    }

    /**
     * 解析 spec 字符串并返回物品。
     * 支持 mm:xxx / mythic:xxx / mythicmobs:xxx 格式。
     */
    fun getItemBySpec(spec: String, amount: Int = 1): ItemStack? {
        val mmId = extractMmId(spec) ?: return null
        return getItem(mmId, amount)
    }

    /**
     * 仅提取 spec 中的 MM ID，不实例化物品。
     */
    fun getItemIdBySpec(spec: String): String? = extractMmId(spec)

    /**
     * 快速判断一个字符串是否是 MM 物品 spec。
     */
    fun looksLikeMmSpec(spec: String): Boolean = extractMmId(spec) != null

    private fun extractMmId(spec: String): String? {
        val raw = spec.trim()
        if (raw.isBlank()) return null
        val lowered = raw.lowercase()
        val prefix = when {
            lowered.startsWith("mm:") -> "mm:"
            lowered.startsWith("mythic:") -> "mythic:"
            lowered.startsWith("mythicmobs:") -> "mythicmobs:"
            else -> null
        } ?: return null
        val id = raw.substring(prefix.length).trim()
        return id.takeIf { it.isNotBlank() }
    }
}
