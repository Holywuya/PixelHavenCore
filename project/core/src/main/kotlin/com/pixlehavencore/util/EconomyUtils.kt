package com.pixlehavencore.util

import net.milkbowl.vault2.economy.Economy
import net.milkbowl.vault2.economy.EconomyFutures
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
    // 同步方法
    // ═══════════════════════════════════════════

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
            if (ex is Exception) BigDecimal.ZERO else throw ex
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
            if (ex is Exception) BigDecimal.ZERO else throw ex
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

    /** 预检：是否可以扣款（含央行储备金检查） */
    fun canWithdraw(player: OfflinePlayer, amount: BigDecimal, currency: String? = null): Boolean {
        if (amount.signum() <= 0) return true
        val economy = getEconomy() ?: return false
        val resolvedCurrency = currency?.trim().orEmpty()
        return economy.canWithdraw(DEFAULT_PLUGIN, player.uniqueId, "", resolvedCurrency, amount)
            .transactionSuccess()
    }

    /** 预检：是否可以存款 */
    fun canDeposit(player: OfflinePlayer, amount: BigDecimal, currency: String? = null): Boolean {
        if (amount.signum() <= 0) return true
        val economy = getEconomy() ?: return false
        val resolvedCurrency = currency?.trim().orEmpty()
        return economy.canDeposit(DEFAULT_PLUGIN, player.uniqueId, "", resolvedCurrency, amount)
            .transactionSuccess()
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
        }.getOrElse { ex ->
            if (ex is Exception) false else throw ex
        }
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
        }.getOrElse { ex ->
            if (ex is Exception) false else throw ex
        }
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

    /** 原子设置余额（使用 Vault2 set 接口，央行托管账户走标准路径，普通账户单次 rawSetBalance） */
    fun setBalance(player: OfflinePlayer, amount: BigDecimal, currency: String? = null): net.milkbowl.vault2.economy.EconomyResponse {
        val economy = getEconomy() ?: return net.milkbowl.vault2.economy.EconomyResponse(
            BigDecimal.ZERO, BigDecimal.ZERO,
            net.milkbowl.vault2.economy.EconomyResponse.ResponseType.FAILURE, "Economy not available"
        )
        return if (currency.isNullOrBlank()) {
            economy.set(DEFAULT_PLUGIN, player.uniqueId, amount)
        } else {
            economy.set(DEFAULT_PLUGIN, player.uniqueId, "", currency.trim(), amount)
        }
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
        }.getOrElse { ex ->
            if (ex is Exception) false else throw ex
        }
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
        }.getOrElse { ex ->
            if (ex is Exception) false else throw ex
        }
    }

    // ═══════════════════════════════════════════
    // 异步方法 (EconomyFutures 自动回退同步)
    // ═══════════════════════════════════════════

    fun getBalanceAsync(player: OfflinePlayer, currency: String? = null): CompletableFuture<BigDecimal> {
        val economy = getEconomy() ?: return CompletableFuture.completedFuture(BigDecimal.ZERO)
        return if (currency.isNullOrBlank())
            EconomyFutures.balance(economy, DEFAULT_PLUGIN, player.uniqueId)
        else
            EconomyFutures.balance(economy, DEFAULT_PLUGIN, player.uniqueId, "", currency.trim())
    }

    fun getBalanceAsync(accountId: UUID, currency: String? = null): CompletableFuture<BigDecimal> {
        val economy = getEconomy() ?: return CompletableFuture.completedFuture(BigDecimal.ZERO)
        return if (currency.isNullOrBlank())
            EconomyFutures.balance(economy, DEFAULT_PLUGIN, accountId)
        else
            EconomyFutures.balance(economy, DEFAULT_PLUGIN, accountId, "", currency.trim())
    }

    fun hasAsync(player: OfflinePlayer, amount: BigDecimal, currency: String? = null): CompletableFuture<Boolean> {
        if (amount.signum() <= 0) return CompletableFuture.completedFuture(true)
        val economy = getEconomy() ?: return CompletableFuture.completedFuture(false)
        return if (currency.isNullOrBlank())
            EconomyFutures.has(economy, DEFAULT_PLUGIN, player.uniqueId, amount)
        else
            EconomyFutures.has(economy, DEFAULT_PLUGIN, player.uniqueId, "", currency.trim(), amount)
    }

    fun withdrawAsync(player: OfflinePlayer, amount: BigDecimal, currency: String? = null): CompletableFuture<Boolean> {
        if (amount.signum() <= 0) return CompletableFuture.completedFuture(true)
        val economy = getEconomy() ?: return CompletableFuture.completedFuture(false)
        return if (currency.isNullOrBlank())
            EconomyFutures.withdraw(economy, DEFAULT_PLUGIN, player.uniqueId, amount)
                .thenApply { it.transactionSuccess() }
        else
            EconomyFutures.withdraw(economy, DEFAULT_PLUGIN, player.uniqueId, "", currency.trim(), amount)
                .thenApply { it.transactionSuccess() }
    }

    fun depositAsync(player: OfflinePlayer, amount: BigDecimal, pluginName: String = DEFAULT_PLUGIN, currency: String? = null): CompletableFuture<Boolean> {
        if (amount.signum() <= 0) return CompletableFuture.completedFuture(true)
        val economy = getEconomy() ?: return CompletableFuture.completedFuture(false)
        return if (currency.isNullOrBlank())
            EconomyFutures.deposit(economy, pluginName, player.uniqueId, amount)
                .thenApply { it.transactionSuccess() }
        else
            EconomyFutures.deposit(economy, pluginName, player.uniqueId, "", currency.trim(), amount)
                .thenApply { it.transactionSuccess() }
    }

    // ═══════════════════════════════════════════
    // 内部
    // ═══════════════════════════════════════════

    private val economyProvider = supplierLazy<Unit, Economy?> {
        Bukkit.getServicesManager().getRegistration(Economy::class.java)?.provider
    }

    private fun getEconomy(): Economy? = economyProvider[Unit]
}
