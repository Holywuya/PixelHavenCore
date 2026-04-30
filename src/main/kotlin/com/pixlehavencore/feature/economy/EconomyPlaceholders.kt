package com.pixlehavencore.feature.economy

import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import taboolib.platform.compat.PlaceholderExpansion
import com.pixlehavencore.util.EconomyUtils
import java.math.BigDecimal

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
            lower == "cbank_balance_raw" || lower == "c_balance_raw" -> resolveAdminMetric(player, EconomySettings.defaultCurrency) { CentralBankService.getReserveBalance() }
            lower == "cbank_balance" || lower == "c_balance" -> resolveAdminMetric(player, EconomySettings.defaultCurrency) { CentralBankService.getReserveBalance() }
            lower.startsWith("cbank_balance_") || lower.startsWith("c_balance_") -> {
                val prefix = if (lower.startsWith("cbank_balance_")) "cbank_balance_" else "c_balance_"
                val currency = lower.removePrefix(prefix).removeSuffix("_raw").ifBlank { EconomySettings.defaultCurrency }
                resolveAdminMetric(player, currency) { CentralBankService.getReserveBalance() }
            }
            lower == "dbank_balance" || lower == "d_balance" || lower == "cbank_executor_balance" ->
                resolveAdminMetric(player, EconomySettings.defaultCurrency) { CentralBankService.getExecutorBalance() }
            lower == "dbank_balance_raw" || lower == "d_balance_raw" || lower == "cbank_executor_balance_raw" ->
                resolveAdminMetric(player, EconomySettings.defaultCurrency) { CentralBankService.getExecutorBalance() }
            lower == "cbank_reserve_rate" || lower == "reserve_rate" ->
                resolveAdminMetric(player, EconomySettings.defaultCurrency) { CentralBankService.getReserveRate() }
            lower == "cbank_reserve_rate_raw" || lower == "reserve_rate_raw" ->
                resolveAdminMetric(player, EconomySettings.defaultCurrency) { CentralBankService.getReserveRate() }
            lower == "active_m0" -> resolveAdminMetric(player, EconomySettings.defaultCurrency) { CentralBankService.getActiveM0() }
            lower == "active_m0_raw" -> resolveAdminMetric(player, EconomySettings.defaultCurrency) { CentralBankService.getActiveM0() }
            lower == "active_player_count" -> {
                if (!canReadCentralBank(player)) "" else CentralBankService.getActivePlayerCount().toString()
            }
            lower == "active_player_count_raw" -> {
                if (!canReadCentralBank(player)) "" else CentralBankService.getActivePlayerCount().toString()
            }
            lower == "cbank_max_supply" -> resolveAdminMetric(player, EconomySettings.defaultCurrency) { CentralBankService.getMaxSupply() }
            lower == "cbank_max_supply_raw" -> resolveAdminMetric(player, EconomySettings.defaultCurrency) { CentralBankService.getMaxSupply() }
            lower == "cbank_total_player_balance" -> resolveAdminMetric(player, EconomySettings.defaultCurrency) { CentralBankService.getTotalPlayerBalance() }
            lower == "cbank_total_player_balance_raw" -> resolveAdminMetric(player, EconomySettings.defaultCurrency) { CentralBankService.getTotalPlayerBalance() }
            lower == "cbank_period_tax" -> resolveAdminMetric(player, EconomySettings.defaultCurrency) { CentralBankService.getPeriodTaxCollected() }
            lower == "cbank_period_tax_raw" -> resolveAdminMetric(player, EconomySettings.defaultCurrency) { CentralBankService.getPeriodTaxCollected() }
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

    private fun canReadCentralBank(player: OfflinePlayer): Boolean {
        return player is Player && (player.hasPermission("phcore.admin") || player.hasPermission("phcore.economy.admin") || player.hasPermission("eco.admin.cbank"))
    }

    private fun resolveAdminMetric(player: OfflinePlayer, currency: String, supplier: () -> BigDecimal): String {
        if (!canReadCentralBank(player)) {
            return ""
        }
        if (EconomySettings.resolveCurrency(currency) != EconomySettings.defaultCurrency) {
            return "0"
        }
        return EconomySettings.formatAmount(supplier(), EconomySettings.defaultCurrency)
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
        if (player !is Player || (!player.hasPermission("phcore.admin") && !player.hasPermission("phcore.economy.admin") && !player.hasPermission("eco.admin.cbank"))) {
            return ""
        }
        val lower = args.lowercase()
        return when {
            lower == "cbank_balance_raw" || lower.startsWith("cbank_balance_") && lower.endsWith("_raw") ->
                resolveCurrencyMetric(lower, "cbank_balance") { CentralBankService.getReserveBalance() }
            lower == "cbank_balance" || lower.startsWith("cbank_balance_") ->
                resolveCurrencyMetric(lower, "cbank_balance") { CentralBankService.getReserveBalance() }
            lower == "cbank_reserve_rate" || lower.startsWith("cbank_reserve_rate_") ->
                resolveCurrencyMetric(lower, "cbank_reserve_rate") { CentralBankService.getReserveRate() }
            lower == "active_m0" || lower.startsWith("active_m0_") ->
                resolveCurrencyMetric(lower, "active_m0") { CentralBankService.getActiveM0() }
            else -> ""
        }
    }

    private fun resolveCurrencyMetric(args: String, prefix: String, supplier: () -> BigDecimal): String {
        val currency = if (args == prefix || args == "${prefix}_raw") {
            EconomySettings.defaultCurrency
        } else {
            args.removePrefix("${prefix}_").removeSuffix("_raw").ifBlank { EconomySettings.defaultCurrency }
        }
        if (EconomySettings.resolveCurrency(currency) != EconomySettings.defaultCurrency) {
            return "0"
        }
        return EconomySettings.formatAmount(supplier(), EconomySettings.defaultCurrency)
    }
}
