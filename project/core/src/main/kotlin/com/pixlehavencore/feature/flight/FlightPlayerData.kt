package com.pixlehavencore.feature.flight

data class FlightPlayerData(
    val remainingSeconds: Int,
    val bonusSeconds: Int = 0,
    val manualDisable: Boolean = false
) {
    val isUnlimited: Boolean get() = remainingSeconds < 0
    val effectiveSeconds: Int get() = if (isUnlimited) Int.MAX_VALUE else remainingSeconds + bonusSeconds
}
