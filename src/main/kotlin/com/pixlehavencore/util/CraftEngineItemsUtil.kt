package com.pixlehavencore.util

import net.momirealms.craftengine.bukkit.api.BukkitAdaptors
import net.momirealms.craftengine.bukkit.api.CraftEngineItems
import net.momirealms.craftengine.core.util.Key
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning

object CraftEngineItemsUtil {

    private const val CRAFT_ENGINE_PLUGIN_NAME = "CraftEngine"

    private val available: Boolean by lazy { detectCraftEngine() }

    private fun detectCraftEngine(): Boolean {
        val plugin = Bukkit.getPluginManager().getPlugin(CRAFT_ENGINE_PLUGIN_NAME)
        if (plugin == null || !plugin.isEnabled) {
            info("[ItemLibrary] 未检测到 CraftEngine，跳过 CE 集成")
            return false
        }
        return runCatching {
            CraftEngineItems.loadedItems()
            true
        }.onFailure { ex ->
            warning("[ItemLibrary] CraftEngine 接入失败: ${ex.message}")
        }.getOrDefault(false).also {
            if (it) {
                info("[ItemLibrary] 已接入 CraftEngine 物品库")
            }
        }
    }

    fun isAvailable(): Boolean = available

    fun looksLikeCeSpec(spec: String): Boolean = extractCeKey(spec) != null

    fun getItemIdBySpec(spec: String): String? = extractCeKey(spec)?.asString()

    fun getItem(itemId: String, player: Player? = null, amount: Int = 1): ItemStack? {
        if (!available) return null
        val key = parseKey(itemId) ?: return null
        return runCatching {
            val customItem = CraftEngineItems.byId(key) ?: return@runCatching null
            val count = amount.coerceAtLeast(1)
            if (player != null) {
                val adapted = BukkitAdaptors.adapt(player)
                if (adapted != null) {
                    return@runCatching customItem.buildItemStack(adapted, count)
                }
            }
            customItem.buildItemStack(count)
        }.onFailure { ex ->
            warning("[ItemLibrary] 获取 CE 物品失败（ID=$itemId）: ${ex.message}")
        }.getOrNull()
    }

    fun getItemBySpec(spec: String, player: Player? = null, amount: Int = 1): ItemStack? {
        val itemId = getItemIdBySpec(spec) ?: return null
        return getItem(itemId, player, amount)
    }

    fun getItemId(itemStack: ItemStack?): String? {
        if (!available || itemStack == null) return null
        return runCatching {
            CraftEngineItems.getCustomItemId(itemStack)
        }.getOrNull()?.asString()?.takeIf { it.isNotBlank() }
    }

    private fun extractCeKey(spec: String): Key? {
        val raw = spec.trim()
        if (raw.isBlank()) return null
        val lowered = raw.lowercase()
        val prefix = when {
            lowered.startsWith("ce:") -> "ce:"
            lowered.startsWith("craftengine:") -> "craftengine:"
            lowered.startsWith("craft-engine:") -> "craft-engine:"
            else -> null
        } ?: return null
        return parseKey(raw.substring(prefix.length).trim())
    }

    private fun parseKey(raw: String): Key? {
        val id = raw.trim()
        if (id.isBlank()) return null
        return Key.ce(id)
    }
}
