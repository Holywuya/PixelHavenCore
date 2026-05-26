package com.pixlehavencore.feature.optimization.entityclearer

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object EntityClearerSettings {

    @Config("feature/optimization/entity-clearer.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    var scanIntervalSeconds: Long = 30L
        private set

    var countdownSeconds: Set<Long> = setOf(10L, 5L)
        private set

    var countdownMessage: String = "&e[实体清理] &7将在 &f{seconds} &7秒后执行清理。"
        private set

    var cycleSummaryMessage: String = "&a[实体清理] &7本轮清理完成，共清理 &f{count} &7个实体。"
        private set

    var itemsEnabled: Boolean = true
        private set

    var mobsEnabled: Boolean = true
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        scanIntervalSeconds = config.getLong("scan-interval-seconds", 1200L).coerceAtLeast(60L)
        countdownSeconds = config.getIntegerList("countdown-seconds")
            .map { it.toLong() }
            .filter { it > 0L }
            .toSet()
            .ifEmpty { setOf(10L, 5L) }
        countdownMessage = config.getString("countdown-message") ?: countdownMessage
        cycleSummaryMessage = config.getString("cycle-summary-message") ?: cycleSummaryMessage
        itemsEnabled = config.getBoolean("items.enabled", true)
        mobsEnabled = config.getBoolean("mobs.enabled", true)
    }
}
