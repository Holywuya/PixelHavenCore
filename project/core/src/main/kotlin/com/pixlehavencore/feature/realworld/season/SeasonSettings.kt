package com.pixlehavencore.feature.realworld.season

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object SeasonSettings {

    @Config("feature/realworld/season.yml")
    private lateinit var config: Configuration

    var durationDays: Int = 7
        private set
    var transitionProgress: Double = 0.1
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        durationDays = config.getInt("duration-days", 7).coerceAtLeast(1)
        transitionProgress = config.getDouble("transition-progress", 0.1).coerceIn(0.0, 1.0)
    }
}