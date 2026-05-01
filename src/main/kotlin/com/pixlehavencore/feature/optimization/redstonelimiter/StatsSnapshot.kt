package com.pixlehavencore.feature.optimization.redstonelimiter

data class StatsSnapshot(
    val totalBlocked: Long,
    val currentTracked: Int,
    val enabledWorlds: Set<String>,
)
