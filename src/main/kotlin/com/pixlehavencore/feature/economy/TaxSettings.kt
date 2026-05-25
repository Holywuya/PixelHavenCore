package com.pixlehavencore.feature.economy

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import java.math.BigDecimal
import java.math.RoundingMode

object TaxSettings {

    @Config("feature/economy/tax.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    var menuTradeEnabled: Boolean = true
        private set

    var commandTradeEnabled: Boolean = true
        private set

    var playerTradeEnabled: Boolean = true
        private set

    var defaultPlayerTradeTaxRate: Double = 0.0
        private set

    var settlementEnabled: Boolean = true
        private set

    var settlementHour: Int = 4
        private set

    var settlementMinute: Int = 0
        private set

    var settlementCheckIntervalTicks: Long = 200L
        private set

    var poolPersistIntervalTicks: Long = 40L
        private set

    var settlementBroadcast: Boolean = true
        private set

    var settlementBroadcastMessage: String = "&6[税收] 本期税款统一结算完成，累计税额: &f{amount}"
        private set

    var brackets: List<TaxBracket> = emptyList()
        private set

    var useMarginalRate: Boolean = true
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        menuTradeEnabled = config.getBoolean("scenes.menu-trade", true)
        commandTradeEnabled = config.getBoolean("scenes.command-trade", true)
        playerTradeEnabled = config.getBoolean("scenes.player-trade", true)
        defaultPlayerTradeTaxRate = config.getDouble("player-trade.default-tax-rate", 0.0).coerceAtLeast(0.0)
        settlementEnabled = config.getBoolean("settlement.enabled", true)
        settlementHour = config.getInt("settlement.time.hour", 4).coerceIn(0, 23)
        settlementMinute = config.getInt("settlement.time.minute", 0).coerceIn(0, 59)
        settlementCheckIntervalTicks = config.getLong("settlement.check-interval-ticks", 200L).coerceAtLeast(20L)
        poolPersistIntervalTicks = config.getLong("settlement.pool-persist-interval-ticks", 40L).coerceAtLeast(20L)
        settlementBroadcast = config.getBoolean("settlement.broadcast.enabled", true)
        settlementBroadcastMessage = config.getString("settlement.broadcast.message")
            ?: "&6[税收] 本期税款统一结算完成，累计税额: &f{amount}"
        brackets = loadBrackets()
        useMarginalRate = config.getBoolean("use-marginal-rate", true)
    }

    fun resolveRate(amount: BigDecimal): Double {
        if (amount <= BigDecimal.ZERO) {
            return 0.0
        }
        return brackets.lastOrNull { amount >= it.min }?.rate ?: 0.0
    }

    fun computeMarginalTax(amount: BigDecimal): BigDecimal {
        if (amount <= BigDecimal.ZERO || brackets.isEmpty()) return BigDecimal.ZERO
        var totalTax = BigDecimal.ZERO
        for (i in brackets.indices) {
            val floor = brackets[i].min
            val ceiling = if (i + 1 < brackets.size) brackets[i + 1].min else null
            if (amount <= floor) break // 升序排列，后续税档 floor 更大，无需继续
            val taxable = if (ceiling != null) {
                amount.coerceAtMost(ceiling).subtract(floor)
            } else {
                amount.subtract(floor)
            }.coerceAtLeast(BigDecimal.ZERO)
            totalTax = totalTax.add(taxable.multiply(BigDecimal.valueOf(brackets[i].rate)))
        }
        return totalTax.setScale(0, RoundingMode.HALF_UP).coerceAtLeast(BigDecimal.ZERO)
    }

    private fun loadBrackets(): List<TaxBracket> {
        val section = config.getConfigurationSection("tax-brackets") ?: return emptyList()
        return section.getKeys(false).mapNotNull { key ->
            val node = section.getConfigurationSection(key) ?: return@mapNotNull null
            TaxBracket(
                min = node.getDouble("min", 0.0).coerceAtLeast(0.0).toBigDecimal(),
                rate = node.getDouble("rate", 0.0).coerceAtLeast(0.0)
            )
        }.sortedBy { it.min }
    }

    data class TaxBracket(
        val min: BigDecimal,
        val rate: Double
    )
}
