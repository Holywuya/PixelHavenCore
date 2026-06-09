package com.pixlehavencore.util

import net.milkbowl.vault2.economy.Economy
import net.milkbowl.vault2.economy.EconomyFutures
import net.milkbowl.vault2.economy.EconomyResponse
import net.milkbowl.vault2.economy.MultiEconomyResponse
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import taboolib.common.util.supplierLazy
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CompletableFuture

object EconomyUtils {

    private const val DEFAULT_PLUGIN = "phcore"
    private const val INTERNAL_PLUGIN = "phcore-internal"

    fun isAvailable(): Boolean = getEconomy() != null

    // ═══════════════════════════════════════════
    // 同步方法 — OfflinePlayer 主实现，UUID 委托
    // ═══════════════════════════════════════════

    fun getBalance(player: OfflinePlayer, currency: String? = null): BigDecimal =
        getBalance(player.uniqueId, currency?.trim().orEmpty())

    fun getBalance(accountId: UUID, rawCurrency: String? = null): BigDecimal {
        val currency = rawCurrency?.trim().orEmpty()
        return runCatching {
            val economy = getEconomy() ?: return BigDecimal.ZERO
            if (currency.isBlank()) economy.balance(DEFAULT_PLUGIN, accountId)
            else economy.balance(DEFAULT_PLUGIN, accountId, "", currency)
        }.getOrElse { ex -> if (ex is Exception) BigDecimal.ZERO else throw ex }
    }

    fun has(player: OfflinePlayer, amount: BigDecimal, currency: String? = null): Boolean =
        has(player.uniqueId, amount, currency?.trim().orEmpty())

    fun has(accountId: UUID, amount: BigDecimal, rawCurrency: String? = null): Boolean {
        if (amount.signum() <= 0) return true
        val economy = getEconomy() ?: return false
        val currency = rawCurrency?.trim().orEmpty()
        return if (currency.isBlank()) economy.has(DEFAULT_PLUGIN, accountId, amount)
        else economy.has(DEFAULT_PLUGIN, accountId, "", currency, amount)
    }

    fun canWithdraw(player: OfflinePlayer, amount: BigDecimal, currency: String? = null): Boolean {
        if (amount.signum() <= 0) return true
        val economy = getEconomy() ?: return false
        val c = currency?.trim().orEmpty()
        return economy.canWithdraw(DEFAULT_PLUGIN, player.uniqueId, "", c, amount).transactionSuccess()
    }

    fun canDeposit(player: OfflinePlayer, amount: BigDecimal, currency: String? = null): Boolean {
        if (amount.signum() <= 0) return true
        val economy = getEconomy() ?: return false
        val c = currency?.trim().orEmpty()
        return economy.canDeposit(DEFAULT_PLUGIN, player.uniqueId, "", c, amount).transactionSuccess()
    }

    fun withdraw(player: OfflinePlayer, amount: BigDecimal, currency: String? = null): Boolean =
        withdraw(player.uniqueId, amount, currency?.trim().orEmpty())

    fun withdraw(accountId: UUID, amount: BigDecimal, rawCurrency: String? = null): Boolean {
        if (amount.signum() <= 0) return true
        val currency = rawCurrency?.trim().orEmpty()
        return runCatching {
            val economy = getEconomy() ?: return false
            if (currency.isBlank()) economy.withdraw(DEFAULT_PLUGIN, accountId, amount).transactionSuccess()
            else economy.withdraw(DEFAULT_PLUGIN, accountId, "", currency, amount).transactionSuccess()
        }.getOrElse { ex -> if (ex is Exception) false else throw ex }
    }

    fun deposit(player: OfflinePlayer, amount: BigDecimal, currency: String? = null): Boolean =
        deposit(player.uniqueId, amount, currency?.trim().orEmpty(), DEFAULT_PLUGIN)

    fun depositInternal(player: OfflinePlayer, amount: BigDecimal, currency: String? = null): Boolean =
        deposit(player.uniqueId, amount, currency?.trim().orEmpty(), INTERNAL_PLUGIN)

    fun deposit(accountId: UUID, amount: BigDecimal, currency: String? = null): Boolean =
        deposit(accountId, amount, currency, DEFAULT_PLUGIN)

    fun depositInternal(accountId: UUID, amount: BigDecimal, currency: String? = null): Boolean =
        deposit(accountId, amount, currency, INTERNAL_PLUGIN)

    /** Vault2 原子转账（含自动回滚） */
    fun transfer(from: OfflinePlayer, to: OfflinePlayer, amount: BigDecimal, currency: String? = null): MultiEconomyResponse {
        val economy = getEconomy()
            ?: return MultiEconomyResponse(BigDecimal.ZERO, EconomyResponse.ResponseType.FAILURE, "Economy not available")
        return if (currency.isNullOrBlank())
            economy.transfer(DEFAULT_PLUGIN, from.uniqueId, to.uniqueId, amount)
        else
            economy.transfer(DEFAULT_PLUGIN, from.uniqueId, to.uniqueId, "", currency.trim(), amount)
    }

    /** 原子设置余额 */
    fun setBalance(player: OfflinePlayer, amount: BigDecimal, currency: String? = null): EconomyResponse {
        val economy = getEconomy()
            ?: return EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, EconomyResponse.ResponseType.FAILURE, "Economy not available")
        return if (currency.isNullOrBlank()) economy.set(DEFAULT_PLUGIN, player.uniqueId, amount)
        else economy.set(DEFAULT_PLUGIN, player.uniqueId, "", currency.trim(), amount)
    }

    fun isInternalPlugin(pluginName: String): Boolean =
        pluginName.trim().equals(INTERNAL_PLUGIN, ignoreCase = true)

    private fun deposit(accountId: UUID, amount: BigDecimal, rawCurrency: String?, pluginName: String): Boolean {
        if (amount.signum() <= 0) return true
        val currency = rawCurrency?.trim().orEmpty()
        return runCatching {
            val economy = getEconomy() ?: return false
            if (currency.isBlank()) economy.deposit(pluginName, accountId, amount).transactionSuccess()
            else economy.deposit(pluginName, accountId, "", currency, amount).transactionSuccess()
        }.getOrElse { ex -> if (ex is Exception) false else throw ex }
    }

    // ═══════════════════════════════════════════
    // 异步方法 (EconomyFutures 自动回退同步)
    // ═══════════════════════════════════════════

    fun getBalanceAsync(player: OfflinePlayer, currency: String? = null): CompletableFuture<BigDecimal> =
        getBalanceAsync(player.uniqueId, currency?.trim().orEmpty())

    fun getBalanceAsync(accountId: UUID, rawCurrency: String? = null): CompletableFuture<BigDecimal> {
        val economy = getEconomy() ?: return CompletableFuture.completedFuture(BigDecimal.ZERO)
        val currency = rawCurrency?.trim().orEmpty()
        return if (currency.isBlank()) EconomyFutures.balance(economy, DEFAULT_PLUGIN, accountId)
        else EconomyFutures.balance(economy, DEFAULT_PLUGIN, accountId, "", currency)
    }

    fun hasAsync(player: OfflinePlayer, amount: BigDecimal, currency: String? = null): CompletableFuture<Boolean> =
        hasAsync(player.uniqueId, amount, currency?.trim().orEmpty())

    fun hasAsync(accountId: UUID, amount: BigDecimal, rawCurrency: String? = null): CompletableFuture<Boolean> {
        if (amount.signum() <= 0) return CompletableFuture.completedFuture(true)
        val economy = getEconomy() ?: return CompletableFuture.completedFuture(false)
        val currency = rawCurrency?.trim().orEmpty()
        return if (currency.isBlank()) EconomyFutures.has(economy, DEFAULT_PLUGIN, accountId, amount)
        else EconomyFutures.has(economy, DEFAULT_PLUGIN, accountId, "", currency, amount)
    }

    fun withdrawAsync(player: OfflinePlayer, amount: BigDecimal, currency: String? = null): CompletableFuture<Boolean> =
        withdrawAsync(player.uniqueId, amount, currency?.trim().orEmpty())

    fun withdrawAsync(accountId: UUID, amount: BigDecimal, rawCurrency: String? = null): CompletableFuture<Boolean> {
        if (amount.signum() <= 0) return CompletableFuture.completedFuture(true)
        val economy = getEconomy() ?: return CompletableFuture.completedFuture(false)
        val currency = rawCurrency?.trim().orEmpty()
        return (if (currency.isBlank()) EconomyFutures.withdraw(economy, DEFAULT_PLUGIN, accountId, amount)
                else EconomyFutures.withdraw(economy, DEFAULT_PLUGIN, accountId, "", currency, amount))
            .thenApply { it.transactionSuccess() }
    }

    fun depositAsync(player: OfflinePlayer, amount: BigDecimal, pluginName: String = DEFAULT_PLUGIN, currency: String? = null): CompletableFuture<Boolean> =
        depositAsync(player.uniqueId, amount, pluginName, currency?.trim().orEmpty())

    fun depositAsync(accountId: UUID, amount: BigDecimal, pluginName: String = DEFAULT_PLUGIN, rawCurrency: String? = null): CompletableFuture<Boolean> {
        if (amount.signum() <= 0) return CompletableFuture.completedFuture(true)
        val economy = getEconomy() ?: return CompletableFuture.completedFuture(false)
        val currency = rawCurrency?.trim().orEmpty()
        return (if (currency.isBlank()) EconomyFutures.deposit(economy, pluginName, accountId, amount)
                else EconomyFutures.deposit(economy, pluginName, accountId, "", currency, amount))
            .thenApply { it.transactionSuccess() }
    }

    // ═══════════════════════════════════════════
    private val economyProvider = supplierLazy<Unit, Economy?> {
        Bukkit.getServicesManager().getRegistration(Economy::class.java)?.provider
    }
    private fun getEconomy(): Economy? = economyProvider[Unit]
}
