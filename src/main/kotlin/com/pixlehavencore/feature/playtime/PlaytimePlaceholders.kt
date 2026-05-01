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
        val total = (data?.totalSeconds ?: 0L) + sessionSeconds
        val today = (data?.todaySeconds ?: 0L) + sessionSeconds
        val week = (data?.weekSeconds ?: 0L) + sessionSeconds
        val month = (data?.monthSeconds ?: 0L) + sessionSeconds

        return when {
            lower == "total" -> PlaytimeSettings.formatByType(total, "readable")
            lower == "total_seconds" -> total.toString()
            lower == "total_minutes" -> (total / 60).toString()
            lower == "total_hours" -> String.format("%.1f", total / 3600.0)

            lower == "today" -> PlaytimeSettings.formatByType(today, "readable")
            lower == "today_seconds" -> today.toString()
            lower == "today_minutes" -> (today / 60).toString()
            lower == "today_hours" -> String.format("%.1f", today / 3600.0)

            lower == "week" -> PlaytimeSettings.formatByType(week, "readable")
            lower == "week_seconds" -> week.toString()
            lower == "week_minutes" -> (week / 60).toString()
            lower == "week_hours" -> String.format("%.1f", week / 3600.0)

            lower == "month" -> PlaytimeSettings.formatByType(month, "readable")
            lower == "month_seconds" -> month.toString()
            lower == "month_minutes" -> (month / 60).toString()
            lower == "month_hours" -> String.format("%.1f", month / 3600.0)

            lower == "session" || lower == "session_formatted" -> PlaytimeSettings.formatSeconds(sessionSeconds)
            lower == "session_seconds" -> sessionSeconds.toString()

            lower == "enabled" -> PlaytimeSettings.enabled.toString()

            else -> ""
        }
    }
}
