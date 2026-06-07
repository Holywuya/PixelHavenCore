package com.pixlehavencore.feature.title

import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import taboolib.platform.compat.PlaceholderExpansion
import java.util.UUID

object TitlePlaceholders : PlaceholderExpansion {

    override val identifier: String = "phcoretitle"

    override fun onPlaceholderRequest(player: Player?, args: String): String {
        if (!TitleSettings.enabled || !TitleSettings.papiEnabled) return ""
        return player?.let { resolve(it.uniqueId, args) } ?: ""
    }

    override fun onPlaceholderRequest(player: OfflinePlayer?, args: String): String {
        if (!TitleSettings.enabled || !TitleSettings.papiEnabled) return ""
        return player?.let { resolve(it.uniqueId, args) } ?: ""
    }

    private fun resolve(uuid: UUID, args: String): String {
        val lower = args.lowercase()
        val state = TitleStorage.getData(uuid)

        return when {
            lower == "active" -> {
                val titleId = state?.activeTitleId ?: return ""
                val def = TitleSettings.getTitle(titleId) ?: return ""
                def.displayName
            }
            lower == "active_raw" -> state?.activeTitleId ?: ""
            lower == "count" -> {
                val now = System.currentTimeMillis()
                val dbCount = state?.ownedTitles?.count { !it.isExpired(now) } ?: 0
                val permCount = countPermissionTitles(uuid)
                (dbCount + permCount).toString()
            }
            lower.startsWith("has_") -> {
                val titleId = lower.removePrefix("has_")
                val def = TitleSettings.getTitle(titleId)
                val has = if (def != null && def.permission.isNotBlank()) {
                    org.bukkit.Bukkit.getPlayer(uuid)?.hasPermission(def.permission) ?: false
                } else {
                    state?.ownedTitles?.any { it.titleId == titleId && !it.isExpired() } ?: false
                }
                has.toString()
            }
            lower.startsWith("category_") -> {
                val category = lower.removePrefix("category_")
                val now = System.currentTimeMillis()
                val dbCount = state?.ownedTitles
                    ?.filter { !it.isExpired(now) }
                    ?.count { entry ->
                        val def = TitleSettings.getTitle(entry.titleId)
                        def?.category.equals(category, ignoreCase = true)
                    } ?: 0
                val permCount = countPermissionTitlesByCategory(uuid, category)
                (dbCount + permCount).toString()
            }
            lower.startsWith("rarity_") -> {
                val rarity = lower.removePrefix("rarity_")
                val now = System.currentTimeMillis()
                val dbCount = state?.ownedTitles
                    ?.filter { !it.isExpired(now) }
                    ?.count { entry ->
                        val def = TitleSettings.getTitle(entry.titleId)
                        def?.rarity.equals(rarity, ignoreCase = true)
                    } ?: 0
                val permCount = countPermissionTitlesByRarity(uuid, rarity)
                (dbCount + permCount).toString()
            }
            else -> ""
        }
    }

    private fun countPermissionTitles(uuid: UUID): Int {
        val player = org.bukkit.Bukkit.getPlayer(uuid) ?: return 0
        return TitleSettings.getAllTitles().count { def ->
            def.permission.isNotBlank() && player.hasPermission(def.permission)
        }
    }

    private fun countPermissionTitlesByCategory(uuid: UUID, category: String): Int {
        val player = org.bukkit.Bukkit.getPlayer(uuid) ?: return 0
        return TitleSettings.getAllTitles().count { def ->
            def.permission.isNotBlank() &&
            def.category.equals(category, ignoreCase = true) &&
            player.hasPermission(def.permission)
        }
    }

    private fun countPermissionTitlesByRarity(uuid: UUID, rarity: String): Int {
        val player = org.bukkit.Bukkit.getPlayer(uuid) ?: return 0
        return TitleSettings.getAllTitles().count { def ->
            def.permission.isNotBlank() &&
            def.rarity.equals(rarity, ignoreCase = true) &&
            player.hasPermission(def.permission)
        }
    }
}
