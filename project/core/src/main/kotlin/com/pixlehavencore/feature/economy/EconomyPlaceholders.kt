package com.pixlehavencore.feature.economy

import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import taboolib.platform.compat.PlaceholderExpansion
import com.pixlehavencore.util.EconomyUtils
import java.math.BigDecimal
import java.math.RoundingMode

object EconomyPlaceholders : PlaceholderExpansion {

    override val identifier: String = "phcoreeco"

    override fun onPlaceholderRequest(player: Player?, args: String): String {
        return player?.let { resolve(it, args) } ?: "0"
    }

    override fun onPlaceholderRequest(player: OfflinePlayer?, args: String): String {
        return player?.let { resolve(it, args) } ?: "0"
    }

    private fun resolve(player: OfflinePlayer, args: String): String {
        val lower = args.lowercase()
        return when {
            lower == "enabled" -> EconomySettings.enabled.toString()
            lower == "default_currency" -> EconomySettings.defaultCurrency
            lower == "currency_list" -> EconomySettings.currencyListText()
            lower == "balance" -> EconomySettings.formatBalance(EconomyUtils.getBalance(player, null))
            lower == "balance_raw" -> EconomySettings.formatAmount(EconomyUtils.getBalance(player, null))
            lower.startsWith("balance_") -> {
                val currency = lower.removePrefix("balance_")
                if (currency.endsWith("_raw")) {
                    val cleanCurrency = currency.removeSuffix("_raw").ifBlank { EconomySettings.defaultCurrency }
                    EconomySettings.formatAmount(EconomyUtils.getBalance(player, cleanCurrency), cleanCurrency)
                } else {
                    val resolvedCurrency = currency.ifBlank { EconomySettings.defaultCurrency }
                    EconomySettings.formatBalance(EconomyUtils.getBalance(player, resolvedCurrency), resolvedCurrency)
                }
            }
            lower == "cbank_balance_raw" || lower == "c_balance_raw" ->
                formatCentralBankRaw { CentralBankService.getReserveBalance() }
            lower == "cbank_balance" || lower == "c_balance" ->
                formatCentralBank { CentralBankService.getReserveBalance() }
            lower.startsWith("cbank_balance_") || lower.startsWith("c_balance_") -> {
                val prefix = if (lower.startsWith("cbank_balance_")) "cbank_balance_" else "c_balance_"
                val suffix = lower.removePrefix(prefix)
                if (suffix.endsWith("_raw")) {
                    formatCentralBankRaw { CentralBankService.getReserveBalance() }
                } else {
                    formatCentralBank { CentralBankService.getReserveBalance() }
                }
            }
            lower == "dbank_balance" || lower == "d_balance" || lower == "cbank_executor_balance" ->
                formatCentralBank { CentralBankService.getExecutorBalance() }
            lower == "dbank_balance_raw" || lower == "d_balance_raw" || lower == "cbank_executor_balance_raw" ->
                formatCentralBankRaw { CentralBankService.getExecutorBalance() }
            lower == "cbank_reserve_rate" || lower == "reserve_rate" -> {
                if (!isCentralBankReady()) return ""
                val rate = CentralBankService.getReserveRate().multiply(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)
                "${rate.toPlainString()}%"
            }
            lower == "cbank_reserve_rate_raw" || lower == "reserve_rate_raw" -> {
                if (!isCentralBankReady()) return ""
                CentralBankService.getReserveRate().multiply(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP).toPlainString()
            }
            lower == "active_m0" ->
                formatCentralBank { CentralBankService.getActiveM0() }
            lower == "active_m0_raw" ->
                formatCentralBankRaw { CentralBankService.getActiveM0() }
            lower == "active_player_count" || lower == "active_player_count_raw" -> {
                if (!isCentralBankReady()) return ""
                CentralBankService.getActivePlayerCount().toString()
            }
            lower == "cbank_max_supply" ->
                formatCentralBank { CentralBankService.getMaxSupply() }
            lower == "cbank_max_supply_raw" ->
                formatCentralBankRaw { CentralBankService.getMaxSupply() }
            lower == "cbank_total_player_balance" ->
                formatCentralBank { CentralBankService.getTotalPlayerBalance() }
            lower == "cbank_total_player_balance_raw" ->
                formatCentralBankRaw { CentralBankService.getTotalPlayerBalance() }
            lower == "cbank_period_tax" ->
                formatCentralBank { CentralBankService.getPeriodTaxCollected() }
            lower == "cbank_period_tax_raw" ->
                formatCentralBankRaw { CentralBankService.getPeriodTaxCollected() }
            lower.startsWith("currency_name_") -> {
                val currency = lower.removePrefix("currency_name_").ifBlank { EconomySettings.defaultCurrency }
                EconomySettings.getDefinition(currency).singular
            }
            lower.startsWith("currency_plural_") -> {
                val currency = lower.removePrefix("currency_plural_").ifBlank { EconomySettings.defaultCurrency }
                EconomySettings.getDefinition(currency).plural
            }
            else -> "0"
        }
    }

    private fun isCentralBankReady(): Boolean {
        return EconomySettings.enabled && CentralBankService.isReady()
    }

    private fun formatCentralBank(supplier: () -> BigDecimal): String {
        if (!isCentralBankReady()) return ""
        val currency = EconomySettings.defaultCurrency
        if (EconomySettings.resolveCurrency(currency) != currency) return "0"
        return EconomySettings.formatBalance(supplier(), currency)
    }

    private fun formatCentralBankRaw(supplier: () -> BigDecimal): String {
        if (!isCentralBankReady()) return ""
        val currency = EconomySettings.defaultCurrency
        if (EconomySettings.resolveCurrency(currency) != currency) return "0"
        return EconomySettings.formatAmount(supplier(), currency)
    }
}

object EconomyCentralBankAliasPlaceholders : PlaceholderExpansion {

    override val identifier: String = "eco"

    override fun onPlaceholderRequest(player: Player?, args: String): String {
        return player?.let { resolve(it, args) } ?: ""
    }

    override fun onPlaceholderRequest(player: OfflinePlayer?, args: String): String {
        return player?.let { resolve(it, args) } ?: ""
    }

    private fun resolve(player: OfflinePlayer, args: String): String {
        val lower = args.lowercase()
        return when {
            lower == "cbank_balance_raw" || (lower.startsWith("cbank_balance_") && lower.endsWith("_raw")) ->
                resolveCurrencyMetric(lower, "cbank_balance", withUnit = false)
            lower == "cbank_balance" || lower.startsWith("cbank_balance_") ->
                resolveCurrencyMetric(lower, "cbank_balance", withUnit = true)
            lower == "cbank_reserve_rate" || lower.startsWith("cbank_reserve_rate_") -> {
                if (!CentralBankService.isReady()) return ""
                CentralBankService.getReserveRate().multiply(BigDecimal(100))
                    .setScale(2, RoundingMode.HALF_UP).let {
                        if (lower.endsWith("_raw")) "${it.toPlainString()}" else "${it.toPlainString()}%"
                    }
            }
            lower == "active_m0" || lower.startsWith("active_m0_") ->
                resolveCurrencyMetric(lower, "active_m0", withUnit = !lower.endsWith("_raw"))
            else -> ""
        }
    }

    private fun resolveCurrencyMetric(args: String, prefix: String, withUnit: Boolean): String {
        if (!CentralBankService.isReady()) return ""
        val currency = EconomySettings.defaultCurrency
        if (EconomySettings.resolveCurrency(currency) != currency) return "0"
        val value = when (prefix) {
            "cbank_balance" -> CentralBankService.getReserveBalance()
            "active_m0" -> CentralBankService.getActiveM0()
            else -> return "0"
        }
        return if (withUnit) {
            EconomySettings.formatBalance(value, currency)
        } else {
            EconomySettings.formatAmount(value, currency)
        }
    }
}
