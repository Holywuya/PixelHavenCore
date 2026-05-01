package com.pixlehavencore.feature.playtime

import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import taboolib.platform.compat.PlaceholderExpansion

object PlaytimePlaceholders : PlaceholderExpansion {

    override val identifier: String = "phcorept"

    override fun onPlaceholderRequest(player: Player?, args: String): String {
        if (!PlaytimeSettings.enabled || !PlaytimeSettings.papiEnabled) return ""
        return player?.let { resolve(it.uniqueId, args) } ?: ""
    }

    override fun onPlaceholderRequest(player: OfflinePlayer?, args: String): String {
        if (!PlaytimeSettings.enabled || !PlaytimeSettings.papiEnabled) return ""
        return player?.let { resolve(it.uniqueId, args) } ?: ""
    }

    private fun resolve(playerUuid: java.util.UUID, args: String): String {
        val lower = args.lowercase()
        val data = PlaytimeStorage.getData(playerUuid)
        val sessionSeconds = PlaytimeStorage.getSessionDuration(playerUuid)

        return when {
            lower == "total" -> PlaytimeSettings.formatByType(data?.totalSeconds ?: 0L, "readable")
            lower == "total_seconds" -> (data?.totalSeconds ?: 0L).toString()
            lower == "total_minutes" -> ((data?.totalSeconds ?: 0L) / 60).toString()
            lower == "total_hours" -> String.format("%.1f", (data?.totalSeconds ?: 0L) / 3600.0)

            lower == "today" -> PlaytimeSettings.formatByType(data?.todaySeconds ?: 0L, "readable")
            lower == "today_seconds" -> (data?.todaySeconds ?: 0L).toString()
            lower == "today_minutes" -> ((data?.todaySeconds ?: 0L) / 60).toString()
            lower == "today_hours" -> String.format("%.1f", (data?.todaySeconds ?: 0L) / 3600.0)

            lower == "week" -> PlaytimeSettings.formatByType(data?.weekSeconds ?: 0L, "readable")
            lower == "week_seconds" -> (data?.weekSeconds ?: 0L).toString()
            lower == "week_minutes" -> ((data?.weekSeconds ?: 0L) / 60).toString()
            lower == "week_hours" -> String.format("%.1f", (data?.weekSeconds ?: 0L) / 3600.0)

            lower == "month" -> PlaytimeSettings.formatByType(data?.monthSeconds ?: 0L, "readable")
            lower == "month_seconds" -> (data?.monthSeconds ?: 0L).toString()
            lower == "month_minutes" -> ((data?.monthSeconds ?: 0L) / 60).toString()
            lower == "month_hours" -> String.format("%.1f", (data?.monthSeconds ?: 0L) / 3600.0)

            lower == "session" || lower == "session_formatted" -> PlaytimeSettings.formatSeconds(sessionSeconds)
            lower == "session_seconds" -> sessionSeconds.toString()

            lower == "enabled" -> PlaytimeSettings.enabled.toString()

            else -> ""
        }
    }
}
