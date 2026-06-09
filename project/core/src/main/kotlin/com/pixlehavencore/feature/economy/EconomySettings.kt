package com.pixlehavencore.feature.economy

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import java.math.BigDecimal
import java.math.RoundingMode

object EconomySettings {

    @Config("feature/economy/economy.yml")
    private lateinit var config: Configuration

    data class CurrencyDefinition(
        val key: String,
        val singular: String,
        val plural: String,
        val fractionalDigits: Int,
    )

    var enabled: Boolean = true
        private set

    var defaultCurrency: String = "coin"
        private set

    var autoSaveTicks: Long = 100L
        private set

    var currencies: Map<String, CurrencyDefinition> = linkedMapOf()
        private set

    fun init() = reload()

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        defaultCurrency = normalizeCurrency(config.getString("defaultCurrency") ?: "coin")
        autoSaveTicks = config.getLong("storage.autoSaveTicks", 100L).coerceAtLeast(20L)
        currencies = loadCurrencies()
        if (!currencies.containsKey(defaultCurrency)) {
            currencies = linkedMapOf(defaultCurrency to fallbackDefinition(defaultCurrency)).apply {
                putAll(currencies)
            }
        }
    }

    fun resolveCurrency(currency: String?): String {
        val normalized = normalizeCurrency(currency ?: "")
        return if (normalized.isBlank()) defaultCurrency else normalized
    }

    fun getDefinition(currency: String): CurrencyDefinition {
        val key = resolveCurrency(currency)
        return currencies[key] ?: fallbackDefinition(key)
    }

    fun getCurrencyKeys(): Set<String> {
        return currencies.keys
    }

    fun formatCurrencyName(currency: String, amount: java.math.BigDecimal): String {
        val definition = getDefinition(currency)
        val name = if (amount.abs().compareTo(java.math.BigDecimal.ONE) == 0) definition.singular else definition.plural
        return name
    }

    fun effectiveFractionalDigits(currency: String): Int {
        return 0
    }

    fun formatAmount(amount: BigDecimal, currency: String? = null): String {
        val scaled = amount.setScale(0, RoundingMode.HALF_UP)
        return scaled.toPlainString()
    }

    fun formatBalance(amount: BigDecimal, currency: String? = null): String {
        val resolvedCurrency = currency ?: defaultCurrency
        val definition = getDefinition(resolvedCurrency)
        val scaledAmount = amount.setScale(0, RoundingMode.HALF_UP)
        val label = if (scaledAmount.abs().compareTo(BigDecimal.ONE) == 0) definition.singular else definition.plural
        return "${scaledAmount.toPlainString()} $label"
    }

    fun currencyListText(): String {
        return getCurrencyKeys().joinToString(",")
    }

    private fun loadCurrencies(): Map<String, CurrencyDefinition> {
        val section = config.getConfigurationSection("currencies") ?: return linkedMapOf()
        val result = linkedMapOf<String, CurrencyDefinition>()
        section.getKeys(false).forEach { key ->
            val normalized = normalizeCurrency(key)
            val path = "currencies.$key"
            result[normalized] = CurrencyDefinition(
                key = normalized,
                singular = config.getString("$path.singular") ?: normalized,
                plural = config.getString("$path.plural") ?: "${normalized}s",
                fractionalDigits = 0
            )
        }
        return result
    }

    private fun fallbackDefinition(key: String): CurrencyDefinition {
        return CurrencyDefinition(
            key = key,
            singular = key,
            plural = "${key}s",
            fractionalDigits = 0,
        )
    }

    private fun normalizeCurrency(value: String): String {
        return value.trim().lowercase()
    }

    fun normalizeAmount(value: BigDecimal): BigDecimal {
        return value.setScale(0, RoundingMode.HALF_UP).coerceAtLeast(BigDecimal.ZERO)
    }
}
