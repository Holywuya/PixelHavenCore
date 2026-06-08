package com.pixlehavencore.feature.flight

data class FlightPlayerData(
    val baseSeconds: Int,
    val permanentBonus: Int = 0,
    val manualDisable: Boolean = false
) {
    val isUnlimited: Boolean get() = baseSeconds < 0
    val effectiveSeconds: Int get() = if (isUnlimited) Int.MAX_VALUE else {
        val sum = baseSeconds.toLong() + permanentBonus.toLong()
        sum.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }
}
