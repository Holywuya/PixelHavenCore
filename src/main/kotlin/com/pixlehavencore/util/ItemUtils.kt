package com.pixlehavencore.util

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.platform.util.BukkitSkull
import java.util.concurrent.ConcurrentHashMap

object ItemUtils {

    private val headCache = ConcurrentHashMap<String, ItemStack>()

    fun looksLikeLibrarySpec(spec: String): Boolean {
        return CraftEngineItemsUtil.looksLikeCeSpec(spec) || BaikirutoItemsUtil.looksLikeBkSpec(spec)
    }

    fun getItemBySpec(spec: String, player: Player? = null): ItemStack? {
        CraftEngineItemsUtil.getItemBySpec(spec, player)?.let { return it }
        return BaikirutoItemsUtil.getItemBySpec(spec)
    }

    fun resolveMaterialOrLibrary(spec: String, player: Player? = null): ItemStack? {
        getItemBySpec(spec, player)?.let { return it }
        val material = resolveMaterialOrNull(spec) ?: return null
        if (material == Material.AIR) return null
        return ItemStack(material, 1)
    }

    fun getNamespacedItemIdBySpec(spec: String): String? {
        CraftEngineItemsUtil.getItemIdBySpec(spec)?.let { return "ce:$it" }
        BaikirutoItemsUtil.getItemIdBySpec(spec)?.let { return "bai:$it" }
        return null
    }

    fun getNamespacedItemId(item: ItemStack?): String? {
        CraftEngineItemsUtil.getItemId(item)?.let { return "ce:$it" }
        BaikirutoItemsUtil.getItemId(item)?.let { return "bai:$it" }
        return null
    }

    fun matchesSpec(spec: String, item: ItemStack): Boolean {
        if (item.type == Material.AIR) return false
        val expectedId = getNamespacedItemIdBySpec(spec)
        if (expectedId != null) {
            val actualId = getNamespacedItemId(item)
            return actualId != null && expectedId == actualId
        }
        if (isHeadSpec(spec)) {
            val resolved = resolveHead(spec) ?: return false
            return resolved.isSimilar(item)
        }
        val material = resolveMaterialOrNull(spec) ?: return false
        return item.type == material
    }

    fun resolveSpec(spec: String): ItemStack? {
        if (looksLikeLibrarySpec(spec)) {
            return getItemBySpec(spec)?.clone()
        }
        if (isHeadSpec(spec)) {
            return resolveHead(spec)?.clone()
        }
        val material = resolveMaterialOrNull(spec) ?: return null
        return ItemStack(material)
    }

    fun isHeadSpec(spec: String): Boolean {
        val trimmed = spec.trim().lowercase()
        return trimmed.startsWith("head:")
    }

    fun resolveHead(spec: String, viewer: Player? = null): ItemStack? {
        if (!isHeadSpec(spec)) return null

        val raw = extractRawHeadTarget(spec, viewer) ?: return null
        if (raw.isBlank()) return null

        return headCache.computeIfAbsent(raw.lowercase()) {
            BukkitSkull.applySkull(raw)
        }.clone()
    }

    fun resolveMaterialOrNull(spec: String): Material? {
        val materialName = normalizeMaterialName(spec)
        if (materialName.isBlank()) return null
        return matchMaterial(materialName)
    }

    fun matchMaterial(raw: String?, fallback: Material? = null): Material? {
        val normalized = raw?.trim()?.takeIf { it.isNotBlank() } ?: return fallback
        return Material.matchMaterial(normalized, false)
            ?: Material.matchMaterial(normalized.uppercase(), false)
            ?: runCatching { Material.valueOf(normalized.uppercase()) }.getOrNull()
            ?: fallback
    }

    fun staticItem(material: Material, title: String, lore: List<String> = emptyList()): ItemStack {
        return ItemStack(material).apply {
            itemMeta = itemMeta?.apply {
                displayName(TextUtils.parseItem(title))
                this.lore(TextUtils.parseItemLore(lore))
            }
        }
    }

    fun namedItem(material: Material, title: String): ItemStack {
        return ItemStack(material).apply {
            itemMeta = itemMeta?.apply {
                displayName(TextUtils.parseItem(title))
            }
        }
    }

    fun clamp(item: ItemStack, amount: Int): ItemStack {
        item.amount = clampAmount(item, amount)
        return item
    }

    fun clampAmount(item: ItemStack, amount: Int): Int {
        return amount.coerceAtLeast(1).coerceAtMost(item.maxStackSize.coerceAtLeast(1))
    }

    private fun normalizeMaterialName(spec: String): String {
        val raw = spec.trim()
        val afterMaterialPrefix = if (raw.startsWith("material:", ignoreCase = true)) {
            raw.substring("material:".length).trim()
        } else {
            raw
        }
        val afterNamespace = if (afterMaterialPrefix.contains(':')) {
            afterMaterialPrefix.substringAfter(':').trim()
        } else {
            afterMaterialPrefix
        }
        return afterNamespace.replace('-', '_').replace(' ', '_').uppercase()
    }

    private fun extractRawHeadTarget(spec: String, viewer: Player?): String? {
        val trimmed = spec.trim()
        val lowered = trimmed.lowercase()

        val raw = when {
            lowered.startsWith("head:") -> trimmed.substringAfter(':').trim()
            else -> return null
        }

        if (raw.isBlank()) return null
        return when {
            raw.equals("%player_name%", ignoreCase = true) && viewer != null -> viewer.name
            raw.equals("{player}", ignoreCase = true) && viewer != null -> viewer.name
            else -> raw
        }
    }

}
