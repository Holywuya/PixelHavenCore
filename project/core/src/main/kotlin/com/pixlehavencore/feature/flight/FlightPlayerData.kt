package com.pixlehavencore.feature.flight

data class FlightPlayerData(
    val remainingSeconds: Int,
    val bonusSeconds: Int = 0,
    val manualDisable: Boolean = false
) {
    val isUnlimited: Boolean get() = remainingSeconds < 0
    val effectiveSeconds: Int get() = if (isUnlimited) Int.MAX_VALUE else {
        // 防止整数溢出
        val sum = remainingSeconds.toLong() + bonusSeconds.toLong()
        sum.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }
}
