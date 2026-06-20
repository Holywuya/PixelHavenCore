package com.pixlehavencore.feature.playtime

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object PlaytimeSettings {

    @Config("feature/playtime.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    var autoSaveTicks: Long = 200L
        private set

    var papiEnabled: Boolean = true
        private set

    var defaultFormat: String = "readable"
        private set

    var leaderboardMaxLimit: Int = 100
        private set

    var leaderboardDefaultLimit: Int = 10
        private set

    var cleanupDefaultDays: Int = 90
        private set

    var cleanupBatchSize: Int = 50
        private set

    var dailyResetTime: String = "00:00"
        private set

    var weeklyResetDay: Int = 1
        private set

    var monthlyResetDay: Int = 1
        private set

    fun init() = reload()

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        autoSaveTicks = config.getLong("auto-save-ticks", 200L).coerceAtLeast(20L)
        papiEnabled = config.getBoolean("papi.enabled", true)
        defaultFormat = config.getString("papi.default-format")?.lowercase()?.takeIf {
            it in setOf("readable", "seconds", "minutes", "hours")
        } ?: "readable"
        leaderboardMaxLimit = config.getInt("leaderboard.max-limit", 100).coerceIn(1, 100)
        leaderboardDefaultLimit = config.getInt("leaderboard.default-limit", 10).coerceIn(1, leaderboardMaxLimit)
        cleanupDefaultDays = config.getInt("cleanup.default-days", 90).coerceAtLeast(1)
        cleanupBatchSize = config.getInt("cleanup.batch-size", 50).coerceAtLeast(1)
        dailyResetTime = config.getString("resetSchedule.daily")?.takeIf { isValidTime(it) } ?: "00:00"
        weeklyResetDay = config.getInt("resetSchedule.weekly-day", 1).coerceIn(1, 7)
        monthlyResetDay = config.getInt("resetSchedule.monthly-day", 1).coerceIn(1, 28)
    }

    fun formatSeconds(seconds: Long): String {
        if (seconds <= 0) return "0秒"
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        val parts = mutableListOf<String>()
        if (days > 0) parts += "${days}天"
        if (hours > 0) parts += "${hours}小时"
        if (minutes > 0) parts += "${minutes}分钟"
        if (parts.isEmpty()) parts += "${seconds}秒"
        return parts.joinToString("")
    }

    fun formatSecondsCompact(seconds: Long): String {
        if (seconds <= 0) return "0m"
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        val parts = mutableListOf<String>()
        if (days > 0) parts += "${days}d"
        if (hours > 0) parts += "${hours}h"
        if (minutes > 0) parts += "${minutes}m"
        if (parts.isEmpty()) parts += "${seconds}s"
        return parts.joinToString("")
    }

    fun resolveFormat(formatHint: String?): String {
        val normalized = formatHint?.lowercase()?.trim()
        return normalized?.takeIf { it in setOf("readable", "seconds", "minutes", "hours") } ?: defaultFormat
    }

    fun formatByType(seconds: Long, format: String): String {
        return when (format) {
            "seconds" -> seconds.toString()
            "minutes" -> (seconds / 60).toString()
            "hours" -> String.format("%.1f", seconds / 3600.0)
            else -> formatSeconds(seconds)
        }
    }

    private fun isValidTime(time: String): Boolean {
        val parts = time.split(":")
        if (parts.size != 2) return false
        val hour = parts[0].toIntOrNull() ?: return false
        val minute = parts[1].toIntOrNull() ?: return false
        return hour in 0..23 && minute in 0..59
    }
}
