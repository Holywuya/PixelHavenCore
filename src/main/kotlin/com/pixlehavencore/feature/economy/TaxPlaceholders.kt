package com.pixlehavencore.feature.economy

import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import taboolib.platform.compat.PlaceholderExpansion

object TaxPlaceholders : PlaceholderExpansion {

    override val identifier: String = "phcoretax"

    override fun onPlaceholderRequest(player: Player?, args: String): String {
        return resolve(player?.uniqueId, args)
    }

    override fun onPlaceholderRequest(player: OfflinePlayer?, args: String): String {
        return resolve(player?.uniqueId, args)
    }

    private fun resolve(playerId: java.util.UUID?, args: String): String {
        return when (args.lowercase()) {
            "enabled" -> TaxSettings.enabled.toString()
            "current_income", "current_total_income" -> playerId?.let { EconomySettings.formatAmount(TaxService.getPlayerCurrentIncome(it), EconomySettings.defaultCurrency) } ?: "0"
            "current_income_raw", "current_total_income_raw" -> playerId?.let { EconomySettings.formatAmount(TaxService.getPlayerCurrentIncome(it), EconomySettings.defaultCurrency) } ?: "0"
            "tax_due", "pending_due_tax" -> playerId?.let { EconomySettings.formatAmount(TaxService.getPlayerTaxDue(it), EconomySettings.defaultCurrency) } ?: "0"
            "tax_due_raw", "pending_due_tax_raw" -> playerId?.let { EconomySettings.formatAmount(TaxService.getPlayerTaxDue(it), EconomySettings.defaultCurrency) } ?: "0"
            "tax_debt", "pending_tax_debt" -> playerId?.let { EconomySettings.formatAmount(TaxService.getPlayerTaxDebt(it), EconomySettings.defaultCurrency) } ?: "0"
            "tax_debt_raw", "pending_tax_debt_raw" -> playerId?.let { EconomySettings.formatAmount(TaxService.getPlayerTaxDebt(it), EconomySettings.defaultCurrency) } ?: "0"
            "pending_income", "total_income" -> EconomySettings.formatAmount(TaxService.getPendingIncome(), EconomySettings.defaultCurrency)
            "pending_income_raw", "total_income_raw" -> EconomySettings.formatAmount(TaxService.getPendingIncome(), EconomySettings.defaultCurrency)
            "pending", "pending_tax" -> EconomySettings.formatAmount(TaxService.getPendingTax(), EconomySettings.defaultCurrency)
            "pending_raw", "pending_tax_raw" -> EconomySettings.formatAmount(TaxService.getPendingTax(), EconomySettings.defaultCurrency)
            "next_settle_seconds", "next_settlement_seconds" -> TaxService.getNextSettlementSeconds().toString()
            else -> "0"
        }
    }
}
