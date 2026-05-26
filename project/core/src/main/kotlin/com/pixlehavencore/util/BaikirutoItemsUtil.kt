package com.pixlehavencore.util

import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.core.Baikiruto
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import java.util.Collections

object BaikirutoItemsUtil {

    private const val BK_PLUGIN_NAME = "Baikiruto"

    private val available: Boolean by lazy { detectBaikiruto() }

    private fun detectBaikiruto(): Boolean {
        val plugin = Bukkit.getPluginManager().getPlugin(BK_PLUGIN_NAME)
        if (plugin == null || !plugin.isEnabled) {
            info("[ItemLibrary] 未检测到 Baikiruto，跳过 BK 集成")
            return false
        }
        return runCatching {
            Baikiruto.api()
            true
        }.onFailure { ex ->
            warning("[ItemLibrary] Baikiruto 接入失败: ${ex.message}")
        }.getOrDefault(false).also {
            if (it) {
                info("[ItemLibrary] 已接入 Baikiruto 物品库")
            }
        }
    }

    fun isAvailable(): Boolean = available

    fun looksLikeBkSpec(spec: String): Boolean = extractBkId(spec) != null

    fun getItemIdBySpec(spec: String): String? = extractBkId(spec)

    fun getItem(itemId: String): ItemStack? {
        if (!available) return null
        val id = itemId.trim()
        if (id.isBlank()) return null
        return runCatching {
            Baikiruto.api().buildItem(id, Collections.emptyMap<String, Any>())
        }.onFailure { ex ->
            warning("[ItemLibrary] 获取 BK 物品失败: ${ex.message}")
        }.getOrNull()
    }

    fun getItemBySpec(spec: String): ItemStack? {
        val itemId = extractBkId(spec) ?: return null
        return getItem(itemId)
    }

    fun getItemId(itemStack: ItemStack?): String? {
        if (!available || itemStack == null) return null
        return runCatching {
            Baikiruto.api().getItemId(itemStack)
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun extractBkId(spec: String): String? {
        val raw = spec.trim()
        if (raw.isBlank()) return null
        val lowered = raw.lowercase()
        val prefix = when {
            lowered.startsWith("bai:") -> "bai:"
            lowered.startsWith("bk:") -> "bk:"
            lowered.startsWith("baikiruto:") -> "baikiruto:"
            lowered.startsWith("ni:") -> "ni:"
            lowered.startsWith("neigeitems:") -> "neigeitems:"
            lowered.startsWith("neige:") -> "neige:"
            else -> null
        } ?: return null
        val id = raw.substring(prefix.length).trim()
        return id.takeIf { it.isNotBlank() }
    }
}
