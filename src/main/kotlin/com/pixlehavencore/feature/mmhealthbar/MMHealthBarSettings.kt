package com.pixlehavencore.feature.mmhealthbar

import net.kyori.adventure.bossbar.BossBar
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object MMHealthBarSettings {

    @Config("feature/mm-healthbar.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set
    var barColor: BossBar.Color = BossBar.Color.PURPLE
        private set
    var barOverlay: BossBar.Overlay = BossBar.Overlay.PROGRESS
        private set
    var titleFormat: String = "<red>{name} <white>{health}/{max_health}</white></red>"
        private set
    var damageFormat: String = " <gray>(-{damage})</gray>"
        private set
    var removeDelayTicks: Long = 60L
        private set
    var updateIntervalTicks: Long = 5L
        private set

    fun init() = reload()

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        barColor = runCatching { BossBar.Color.valueOf(config.getString("bar_color", "PURPLE")!!.uppercase()) }
            .getOrDefault(BossBar.Color.PURPLE)
        barOverlay = runCatching { BossBar.Overlay.valueOf(config.getString("bar_style", "PROGRESS")!!.uppercase()) }
            .getOrDefault(BossBar.Overlay.PROGRESS)
        titleFormat = config.getString("title_format", titleFormat) ?: titleFormat
        damageFormat = config.getString("damage_format", damageFormat) ?: damageFormat
        removeDelayTicks = config.getLong("remove_delay_ticks", 60L).coerceAtLeast(1L)
        updateIntervalTicks = config.getLong("update_interval_ticks", 5L).coerceAtLeast(1L)
    }
}
