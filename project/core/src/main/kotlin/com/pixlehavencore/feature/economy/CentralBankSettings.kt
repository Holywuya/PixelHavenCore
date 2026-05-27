package com.pixlehavencore.feature.economy

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import java.math.BigDecimal

object CentralBankSettings {

    @Config("feature/economy/central-bank.yml")
    private lateinit var config: Configuration

    data class InactivityWeight(val days: Int, val weight: BigDecimal)

    enum class SupplyMode { FIXED, MANAGED }

    var supplyMode: SupplyMode = SupplyMode.MANAGED
        private set

    var allowAutoContraction: Boolean = true
        private set

    var inactivityWeights: List<InactivityWeight> = emptyList()
        private set

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

    var maxNegativeReserve: Long = -1L
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
        maxNegativeReserve = config.getLong("max-negative-reserve", -1L)
        supplyMode = runCatching {
            SupplyMode.valueOf((config.getString("supply-mode", "MANAGED") ?: "MANAGED").uppercase())
        }.getOrDefault(SupplyMode.MANAGED)
        allowAutoContraction = config.getBoolean("allow-auto-contraction", true)
        inactivityWeights = config.getMapList("inactivity-weights")
            .mapNotNull { map ->
                val days = (map["days"] as? Int)?.coerceAtLeast(1) ?: return@mapNotNull null
                val w = (map["weight"] as? Number)
                    ?.let { BigDecimal(it.toString()) }
                    ?.coerceIn(BigDecimal.ZERO, BigDecimal.ONE)
                    ?: return@mapNotNull null
                InactivityWeight(days, w)
            }
            .sortedByDescending { it.days }
            .ifEmpty {
                listOf(
                    InactivityWeight(14, BigDecimal("0.7")),
                    InactivityWeight(30, BigDecimal("0.3")),
                    InactivityWeight(60, BigDecimal("0.1")),
                )
            }
    }
}
