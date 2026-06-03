package com.pixlehavencore.util

import net.milkbowl.vault2.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import taboolib.common.util.supplierLazy
import java.math.BigDecimal
import java.util.UUID

object EconomyUtils {

    private const val DEFAULT_PLUGIN = "phcore"
    private const val INTERNAL_PLUGIN = "phcore-internal"

    fun isAvailable(): Boolean {
        return getEconomy() != null
    }

    fun getBalance(player: OfflinePlayer, currency: String? = null): BigDecimal {
        return runCatching {
            val economy = getEconomy() ?: return BigDecimal.ZERO
            val resolvedCurrency = currency?.trim().orEmpty()
            if (resolvedCurrency.isBlank()) {
                economy.balance(DEFAULT_PLUGIN, player.uniqueId)
            } else {
                economy.balance(DEFAULT_PLUGIN, player.uniqueId, "", resolvedCurrency)
            }
        }.getOrElse { ex ->
            if (ex is Exception) {
                BigDecimal.ZERO
            } else {
                throw ex  // 重新抛出非 Exception 的 Throwable（如 OOM、StackOverflow）
            }
        }
    }

    fun getBalance(accountId: UUID, currency: String? = null): BigDecimal {
        return runCatching {
            val economy = getEconomy() ?: return BigDecimal.ZERO
            val resolvedCurrency = currency?.trim().orEmpty()
            if (resolvedCurrency.isBlank()) {
                economy.balance(DEFAULT_PLUGIN, accountId)
            } else {
                economy.balance(DEFAULT_PLUGIN, accountId, "", resolvedCurrency)
            }
        }.getOrElse { ex ->
            if (ex is Exception) {
                BigDecimal.ZERO
            } else {
                throw ex  // 重新抛出非 Exception 的 Throwable（如 OOM、StackOverflow）
            }
        }
    }

    fun has(player: OfflinePlayer, amount: BigDecimal, currency: String? = null): Boolean {
        if (amount.signum() <= 0) return true
        val economy = getEconomy() ?: return false
        return if (currency.isNullOrBlank()) {
            economy.has(DEFAULT_PLUGIN, player.uniqueId, amount)
        } else {
            economy.has(DEFAULT_PLUGIN, player.uniqueId, "", currency.trim(), amount)
        }
    }

    fun has(accountId: UUID, amount: BigDecimal, currency: String? = null): Boolean {
        if (amount.signum() <= 0) return true
        val economy = getEconomy() ?: return false
        return if (currency.isNullOrBlank()) {
            economy.has(DEFAULT_PLUGIN, accountId, amount)
        } else {
            economy.has(DEFAULT_PLUGIN, accountId, "", currency.trim(), amount)
        }
    }

    fun withdraw(player: OfflinePlayer, amount: BigDecimal, currency: String? = null): Boolean {
        if (amount.signum() <= 0) return true
        return runCatching {
            val economy = getEconomy() ?: return false
            val resolvedCurrency = currency?.trim().orEmpty()
            if (resolvedCurrency.isBlank()) {
                economy.withdraw(DEFAULT_PLUGIN, player.uniqueId, amount).transactionSuccess()
            } else {
                economy.withdraw(DEFAULT_PLUGIN, player.uniqueId, "", resolvedCurrency, amount).transactionSuccess()
            }
        }.getOrDefault(false)
    }

    fun withdraw(accountId: UUID, amount: BigDecimal, currency: String? = null): Boolean {
        if (amount.signum() <= 0) return true
        return runCatching {
            val economy = getEconomy() ?: return false
            val resolvedCurrency = currency?.trim().orEmpty()
            if (resolvedCurrency.isBlank()) {
                economy.withdraw(DEFAULT_PLUGIN, accountId, amount).transactionSuccess()
            } else {
                economy.withdraw(DEFAULT_PLUGIN, accountId, "", resolvedCurrency, amount).transactionSuccess()
            }
        }.getOrDefault(false)
    }

    fun deposit(player: OfflinePlayer, amount: BigDecimal, currency: String? = null): Boolean {
        return deposit(player, amount, currency, DEFAULT_PLUGIN)
    }

    fun depositInternal(player: OfflinePlayer, amount: BigDecimal, currency: String? = null): Boolean {
        return deposit(player, amount, currency, INTERNAL_PLUGIN)
    }

    fun deposit(accountId: UUID, amount: BigDecimal, currency: String? = null): Boolean {
        return deposit(accountId, amount, currency, DEFAULT_PLUGIN)
    }

    fun depositInternal(accountId: UUID, amount: BigDecimal, currency: String? = null): Boolean {
        return deposit(accountId, amount, currency, INTERNAL_PLUGIN)
    }

    fun isInternalPlugin(pluginName: String): Boolean {
        return pluginName.trim().equals(INTERNAL_PLUGIN, ignoreCase = true)
    }

    private fun deposit(player: OfflinePlayer, amount: BigDecimal, currency: String?, pluginName: String): Boolean {
        if (amount.signum() <= 0) return true
        return runCatching {
            val economy = getEconomy() ?: return false
            val resolvedCurrency = currency?.trim().orEmpty()
            if (resolvedCurrency.isBlank()) {
                economy.deposit(pluginName, player.uniqueId, amount).transactionSuccess()
            } else {
                economy.deposit(pluginName, player.uniqueId, "", resolvedCurrency, amount).transactionSuccess()
            }
        }.getOrDefault(false)
    }

    private fun deposit(accountId: UUID, amount: BigDecimal, currency: String?, pluginName: String): Boolean {
        if (amount.signum() <= 0) return true
        return runCatching {
            val economy = getEconomy() ?: return false
            val resolvedCurrency = currency?.trim().orEmpty()
            if (resolvedCurrency.isBlank()) {
                economy.deposit(pluginName, accountId, amount).transactionSuccess()
            } else {
                economy.deposit(pluginName, accountId, "", resolvedCurrency, amount).transactionSuccess()
            }
        }.getOrDefault(false)
    }

    private val economyProvider = supplierLazy<Unit, Economy?> {
        Bukkit.getServicesManager().getRegistration(Economy::class.java)?.provider
    }

    private fun getEconomy(): Economy? {
        return economyProvider[Unit]
    }
}
