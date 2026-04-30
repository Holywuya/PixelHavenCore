package com.pixlehavencore.feature.economy

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import java.math.BigDecimal

object CentralBankSettings {

    @Config("feature/economy/central-bank.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    var expectedBalance: BigDecimal = BigDecimal("10000")
        private set

    var bufferMultiplier: BigDecimal = BigDecimal("2.0")
        private set

    var activeThresholdDays: Int = 14
        private set

    var dormantThresholdDays: Int = 60
        private set

    var dormantRecoveryRate: BigDecimal = BigDecimal("0.01")
        private set

    var syncIntervalMinutes: Long = 30L
        private set

    var exemptAccountIds: Set<String> = emptySet()
        private set

    fun init() = reload()

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        expectedBalance = config.getString("expected-balance")?.toBigDecimalOrNull()
            ?.coerceAtLeast(BigDecimal.ONE)
            ?: BigDecimal("10000")
        bufferMultiplier = config.getString("buffer-multiplier")?.toBigDecimalOrNull()
            ?.coerceAtLeast(BigDecimal.ONE)
            ?: BigDecimal("2.0")
        activeThresholdDays = config.getInt("active-threshold-days", 14).coerceAtLeast(1)
        dormantThresholdDays = config.getInt("dormant-threshold-days", 60).coerceAtLeast(activeThresholdDays)
        dormantRecoveryRate = config.getString("dormant-recovery-rate")?.toBigDecimalOrNull()
            ?.coerceIn(BigDecimal.ZERO, BigDecimal.ONE)
            ?: BigDecimal("0.01")
        syncIntervalMinutes = config.getLong("sync-interval-minutes", 30L).coerceAtLeast(1L)
        exemptAccountIds = config.getStringList("exempt-account-ids")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
    }
}
