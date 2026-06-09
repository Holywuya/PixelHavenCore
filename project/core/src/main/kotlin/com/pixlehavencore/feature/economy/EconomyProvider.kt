package com.pixlehavencore.feature.economy

import net.milkbowl.vault2.economy.AccountPermission
import net.milkbowl.vault2.economy.AsyncEconomy
import net.milkbowl.vault2.economy.Economy
import net.milkbowl.vault2.economy.EconomyResponse
import net.milkbowl.vault2.economy.MultiEconomyResponse
import net.milkbowl.vault.economy.Economy as Vault1Economy
import net.milkbowl.vault.economy.EconomyResponse as Vault1EconomyResponse
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.ServicePriority
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.ArrayList
import java.util.HashMap
import java.util.List
import java.util.Map
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture

object EconomyProvider {

    internal lateinit var economy: VaultUnlockedEconomy
        private set
    private lateinit var vault1Bridge: Vault1EconomyBridge

    fun init() {
        if (::economy.isInitialized) {
            stop()
        }
        EconomySettings.init()
        CentralBankSettings.init()
        EconomyStorageService.init()
        TaxService.init()
        CentralBankService.init()
        economy = VaultUnlockedEconomy()
        vault1Bridge = Vault1EconomyBridge()
        val plugin = Bukkit.getPluginManager().getPlugin("phcore")
        if (plugin == null) {
            warning("[经济系统] 找不到核心插件实例，无法注册经济服务")
            return
        }
        Bukkit.getServicesManager().register(Economy::class.java, economy, plugin, ServicePriority.Normal)
        Bukkit.getServicesManager().register(Vault1Economy::class.java, vault1Bridge, plugin, ServicePriority.Normal)
        info("[经济系统] 底层经济服务已注册 (VaultUnlockedAPI 2.20 + Vault1 兼容桥接, AsyncEconomy 已启用)")
    }

    fun reload() {
        EconomySettings.reload()
        CentralBankSettings.reload()
        EconomyStorageService.reload()
        TaxService.reload()
        CentralBankService.reload()
        if (!EconomySettings.enabled && this::economy.isInitialized) {
            stop()
        }
    }

    fun stop() {
        if (this::economy.isInitialized) {
            Bukkit.getServicesManager().unregister(Economy::class.java, economy)
            Bukkit.getServicesManager().unregister(Vault1Economy::class.java, vault1Bridge)
        }
        TaxService.shutdown()
        CentralBankService.stop()
        EconomyStorageService.stop()
    }
}

class VaultUnlockedEconomy : Economy {

    private val asyncEconomy = VaultUnlockedAsyncEconomy(this)

    // ─────────────────────────────────────────
    // Meta / Feature Detection
    // ─────────────────────────────────────────

    override fun isEnabled(): Boolean = EconomySettings.enabled

    override fun getName(): String = "PHCore Economy"

    override fun hasSharedAccountSupport(): Boolean = false

    override fun hasMultiCurrencySupport(): Boolean = true

    override fun supportsAsync(): Boolean = true

    override fun async(): Optional<AsyncEconomy> = Optional.of(asyncEconomy)

    override fun fractionalDigits(pluginName: String): Int = 0

    override fun fractionalDigits(pluginName: String, currency: String): Int = 0

    // ─────────────────────────────────────────
    // Format
    // ─────────────────────────────────────────

    @Deprecated("Vault legacy overload", level = DeprecationLevel.HIDDEN)
    override fun format(amount: BigDecimal): String = format("phcore", amount)

    override fun format(pluginName: String, amount: BigDecimal): String =
        format(pluginName, amount, EconomySettings.defaultCurrency)

    @Deprecated("Vault legacy overload", level = DeprecationLevel.HIDDEN)
    override fun format(amount: BigDecimal, currency: String): String = format("phcore", amount, currency)

    override fun format(pluginName: String, amount: BigDecimal, currency: String): String {
        val def = EconomySettings.getDefinition(currency)
        val scaledAmount = amount.setScale(0, RoundingMode.HALF_UP)
        val name = if (scaledAmount.abs().compareTo(BigDecimal.ONE) == 0) def.singular else def.plural
        return "${scaledAmount.toPlainString()} $name"
    }

    // ─────────────────────────────────────────
    // Currency
    // ─────────────────────────────────────────

    override fun hasCurrency(currency: String): Boolean =
        EconomySettings.getCurrencyKeys().contains(EconomySettings.resolveCurrency(currency))

    override fun getDefaultCurrency(pluginName: String): String = EconomySettings.defaultCurrency

    override fun defaultCurrencyNamePlural(pluginName: String): String =
        EconomySettings.getDefinition(EconomySettings.defaultCurrency).plural

    override fun defaultCurrencyNameSingular(pluginName: String): String =
        EconomySettings.getDefinition(EconomySettings.defaultCurrency).singular

    override fun currencies(): MutableCollection<String> = ArrayList(EconomySettings.getCurrencyKeys())

    // ─────────────────────────────────────────
    // Account Management
    // ─────────────────────────────────────────

    @Deprecated("Vault legacy overload", level = DeprecationLevel.HIDDEN)
    override fun createAccount(accountID: UUID, name: String): Boolean = true

    override fun createAccount(accountID: UUID, name: String, player: Boolean): Boolean = true

    @Deprecated("Vault legacy overload", level = DeprecationLevel.HIDDEN)
    override fun createAccount(accountID: UUID, name: String, worldName: String): Boolean = true

    override fun createAccount(accountID: UUID, name: String, worldName: String, player: Boolean): Boolean = true

    override fun getUUIDNameMap(): MutableMap<UUID, String> = HashMap()

    override fun getAccountName(accountID: UUID): Optional<String> = Optional.empty()

    override fun hasAccount(accountID: UUID): Boolean = true

    override fun hasAccount(accountID: UUID, worldName: String): Boolean = true

    override fun renameAccount(accountID: UUID, name: String): Boolean = true

    override fun renameAccount(pluginName: String, accountID: UUID, name: String): Boolean = true

    override fun deleteAccount(pluginName: String, accountID: UUID): Boolean = true

    // ─────────────────────────────────────────
    // Account Currency Support
    // ─────────────────────────────────────────

    override fun accountSupportsCurrency(pluginName: String, accountID: UUID, currency: String): Boolean =
        hasCurrency(currency)

    override fun accountSupportsCurrency(pluginName: String, accountID: UUID, currency: String, world: String): Boolean =
        hasCurrency(currency)

    // ─────────────────────────────────────────
    // Balance (替代已废弃的 getBalance)
    // ─────────────────────────────────────────

    override fun balance(pluginName: String, accountID: UUID): BigDecimal =
        resolveBalance(accountID, EconomySettings.defaultCurrency)

    override fun balance(pluginName: String, accountID: UUID, world: String): BigDecimal =
        balance(pluginName, accountID)

    override fun balance(pluginName: String, accountID: UUID, world: String, currency: String): BigDecimal =
        resolveBalance(accountID, EconomySettings.resolveCurrency(currency))

    @Deprecated("Vault legacy overload", level = DeprecationLevel.HIDDEN)
    override fun getBalance(pluginName: String, accountID: UUID): BigDecimal =
        balance(pluginName, accountID)

    @Deprecated("Vault legacy overload", level = DeprecationLevel.HIDDEN)
    override fun getBalance(pluginName: String, accountID: UUID, world: String): BigDecimal =
        balance(pluginName, accountID, world)

    @Deprecated("Vault legacy overload", level = DeprecationLevel.HIDDEN)
    override fun getBalance(pluginName: String, accountID: UUID, world: String, currency: String): BigDecimal =
        balance(pluginName, accountID, world, currency)

    // ─────────────────────────────────────────
    // has
    // ─────────────────────────────────────────

    override fun has(pluginName: String, accountID: UUID, amount: BigDecimal): Boolean =
        runCatching { resolveBalance(accountID, EconomySettings.defaultCurrency) >= amount }.getOrDefault(false)

    override fun has(pluginName: String, accountID: UUID, worldName: String, amount: BigDecimal): Boolean =
        has(pluginName, accountID, amount)

    override fun has(pluginName: String, accountID: UUID, worldName: String, currency: String, amount: BigDecimal): Boolean =
        runCatching { resolveBalance(accountID, EconomySettings.resolveCurrency(currency)) >= amount }.getOrDefault(false)

    // ─────────────────────────────────────────
    // canWithdraw — 预检扣款条件
    // ─────────────────────────────────────────

    override fun canWithdraw(pluginName: String, accountID: UUID, amount: BigDecimal): EconomyResponse =
        canWithdraw(pluginName, accountID, "", EconomySettings.defaultCurrency, amount)

    override fun canWithdraw(pluginName: String, accountID: UUID, worldName: String, amount: BigDecimal): EconomyResponse =
        canWithdraw(pluginName, accountID, worldName, EconomySettings.defaultCurrency, amount)

    override fun canWithdraw(pluginName: String, accountID: UUID, worldName: String, currency: String, amount: BigDecimal): EconomyResponse {
        val resolved = EconomySettings.resolveCurrency(currency)
        val current = resolveBalance(accountID, resolved)
        if (amount.signum() <= 0) return response(amount, current, EconomyResponse.ResponseType.SUCCESS, "")
        val reason = validateWithdraw(accountID, resolved, amount)
        return if (reason == null)
            response(amount, current, EconomyResponse.ResponseType.SUCCESS, "")
        else
            response(amount, current, EconomyResponse.ResponseType.FAILURE, reason)
    }

    // ─────────────────────────────────────────
    // canDeposit — 预检存款条件
    // ─────────────────────────────────────────

    override fun canDeposit(pluginName: String, accountID: UUID, amount: BigDecimal): EconomyResponse =
        canDeposit(pluginName, accountID, "", EconomySettings.defaultCurrency, amount)

    override fun canDeposit(pluginName: String, accountID: UUID, worldName: String, amount: BigDecimal): EconomyResponse =
        canDeposit(pluginName, accountID, worldName, EconomySettings.defaultCurrency, amount)

    override fun canDeposit(pluginName: String, accountID: UUID, worldName: String, currency: String, amount: BigDecimal): EconomyResponse {
        val resolved = EconomySettings.resolveCurrency(currency)
        val current = resolveBalance(accountID, resolved)
        if (amount.signum() <= 0) return response(amount, current, EconomyResponse.ResponseType.SUCCESS, "")
        val reason = validateDeposit(accountID, resolved, amount)
        return if (reason == null)
            response(amount, current, EconomyResponse.ResponseType.SUCCESS, "")
        else
            response(amount, current, EconomyResponse.ResponseType.FAILURE, reason)
    }

    // ─────────────────────────────────────────
    // Withdraw
    // ─────────────────────────────────────────

    @Deprecated("Vault legacy overload", level = DeprecationLevel.HIDDEN)
    override fun withdraw(pluginName: String, accountID: UUID, amount: BigDecimal): EconomyResponse =
        withdraw(pluginName, accountID, "", amount)

    override fun withdraw(pluginName: String, accountID: UUID, worldName: String, amount: BigDecimal): EconomyResponse =
        withdraw(pluginName, accountID, worldName, EconomySettings.defaultCurrency, amount)

    override fun withdraw(pluginName: String, accountID: UUID, worldName: String, currency: String, amount: BigDecimal): EconomyResponse {
        val resolvedCurrency = EconomySettings.resolveCurrency(currency)
        val currentBalance = resolveBalance(accountID, resolvedCurrency)
        if (amount.signum() <= 0) return response(amount, currentBalance, EconomyResponse.ResponseType.SUCCESS, "")

        if (CentralBankService.isCentralBankAccount(accountID) && CentralBankService.isManagedCurrency(resolvedCurrency)) {
            val balance = CentralBankService.drain(amount)
                ?: return response(amount, currentBalance, EconomyResponse.ResponseType.FAILURE, "INSUFFICIENT_FUNDS")
            TaxService.recordVaultIncome(pluginName, accountID, amount, resolvedCurrency)
            return response(amount, balance, EconomyResponse.ResponseType.SUCCESS, "")
        }
        if (CentralBankService.isManagedPlayerAccount(accountID, resolvedCurrency)) {
            val balance = CentralBankService.withdrawFromPlayer(accountID, amount)
                ?: return response(amount, currentBalance, EconomyResponse.ResponseType.FAILURE, "INSUFFICIENT_FUNDS")
            TaxService.recordVaultIncome(pluginName, accountID, amount, resolvedCurrency)
            return response(amount, balance, EconomyResponse.ResponseType.SUCCESS, "")
        }
        val balance = EconomyStorageService.tryWithdraw(accountID, resolvedCurrency, amount)
            ?: return response(amount, currentBalance, EconomyResponse.ResponseType.FAILURE, "INSUFFICIENT_FUNDS")
        return response(amount, balance, EconomyResponse.ResponseType.SUCCESS, "")
    }

    // ─────────────────────────────────────────
    // Deposit
    // ─────────────────────────────────────────

    override fun deposit(pluginName: String, accountID: UUID, amount: BigDecimal): EconomyResponse =
        deposit(pluginName, accountID, "", amount)

    override fun deposit(pluginName: String, accountID: UUID, worldName: String, amount: BigDecimal): EconomyResponse =
        deposit(pluginName, accountID, worldName, EconomySettings.defaultCurrency, amount)

    override fun deposit(pluginName: String, accountID: UUID, worldName: String, currency: String, amount: BigDecimal): EconomyResponse {
        val resolvedCurrency = EconomySettings.resolveCurrency(currency)
        val currentBalance = resolveBalance(accountID, resolvedCurrency)
        if (amount.signum() <= 0) return response(amount, currentBalance, EconomyResponse.ResponseType.SUCCESS, "")

        if (CentralBankService.isCentralBankAccount(accountID) && CentralBankService.isManagedCurrency(resolvedCurrency)) {
            val balance = CentralBankService.inject(amount)
            return response(amount, balance, EconomyResponse.ResponseType.SUCCESS, "")
        }
        if (CentralBankService.isManagedPlayerAccount(accountID, resolvedCurrency)) {
            val balance = CentralBankService.depositToPlayer(accountID, amount)
                ?: return response(amount, currentBalance, EconomyResponse.ResponseType.FAILURE, "CENTRAL_BANK_RESERVE_EXHAUSTED")
            TaxService.recordVaultIncome(pluginName, accountID, amount, resolvedCurrency)
            return response(amount, balance, EconomyResponse.ResponseType.SUCCESS, "")
        }
        val balance = EconomyStorageService.deposit(accountID, resolvedCurrency, amount)
        TaxService.recordVaultIncome(pluginName, accountID, amount, resolvedCurrency)
        return response(amount, balance, EconomyResponse.ResponseType.SUCCESS, "")
    }

    // ─────────────────────────────────────────
    // Transfer — 原子转账带回滚
    // ─────────────────────────────────────────

    override fun transfer(pluginName: String, from: UUID, to: UUID, amount: BigDecimal): MultiEconomyResponse =
        transfer(pluginName, from, to, "", amount)

    override fun transfer(pluginName: String, from: UUID, to: UUID, worldName: String, amount: BigDecimal): MultiEconomyResponse =
        transfer(pluginName, from, to, worldName, EconomySettings.defaultCurrency, amount)

    override fun transfer(pluginName: String, from: UUID, to: UUID, worldName: String, currency: String, amount: BigDecimal): MultiEconomyResponse {
        val resp = MultiEconomyResponse(amount, EconomyResponse.ResponseType.SUCCESS, "")
        if (amount.signum() <= 0) {
            resp.addBalance(from, resolveBalance(from, EconomySettings.resolveCurrency(currency)))
            resp.addBalance(to, resolveBalance(to, EconomySettings.resolveCurrency(currency)))
            return resp
        }
        val withdrawResp = withdraw(pluginName, from, worldName, currency, amount)
        if (withdrawResp.type != EconomyResponse.ResponseType.SUCCESS) {
            return MultiEconomyResponse(amount, withdrawResp.type, withdrawResp.errorMessage)
        }
        val depositResp = deposit(pluginName, to, worldName, currency, withdrawResp.amount)
        if (depositResp.type != EconomyResponse.ResponseType.SUCCESS) {
            deposit(pluginName, from, worldName, currency, amount)
            return MultiEconomyResponse(amount, depositResp.type, depositResp.errorMessage)
        }
        resp.addBalance(from, withdrawResp.balance)
        resp.addBalance(to, depositResp.balance)
        return resp
    }

    // ─────────────────────────────────────────
    // Shared Accounts (不支持)
    // ─────────────────────────────────────────

    override fun createSharedAccount(pluginName: String, accountID: UUID, name: String, owner: UUID): Boolean = false

    @Deprecated("Replaced by accountsWithOwnerOf")
    override fun accountsOwnedBy(pluginName: String, accountID: UUID): MutableList<String> = ArrayList()

    @Deprecated("Replaced by accountsWithMembershipTo")
    override fun accountsMemberOf(pluginName: String, accountID: UUID): MutableList<String> = ArrayList()

    @Deprecated("Replaced by accountsWithAccessTo")
    override fun accountsAccessTo(pluginName: String, accountID: UUID, vararg permissions: AccountPermission): MutableList<String> = ArrayList()

    override fun isAccountOwner(pluginName: String, accountID: UUID, uuid: UUID): Boolean = false

    override fun setOwner(pluginName: String, accountID: UUID, uuid: UUID): Boolean = false

    override fun isAccountMember(pluginName: String, accountID: UUID, uuid: UUID): Boolean = false

    override fun addAccountMember(pluginName: String, accountID: UUID, uuid: UUID): Boolean = false

    override fun addAccountMember(pluginName: String, accountID: UUID, uuid: UUID, vararg initialPermissions: AccountPermission): Boolean = false

    override fun removeAccountMember(pluginName: String, accountID: UUID, uuid: UUID): Boolean = false

    override fun hasAccountPermission(pluginName: String, accountID: UUID, uuid: UUID, permission: AccountPermission): Boolean = false

    override fun updateAccountPermission(pluginName: String, accountID: UUID, uuid: UUID, permission: AccountPermission, value: Boolean): Boolean = false

    // ─────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────

    internal fun resolveBalance(accountID: UUID, currency: String): BigDecimal {
        if (CentralBankService.isManagedCurrency(currency) && CentralBankService.isCentralBankAccount(accountID)) {
            return if (accountID == CentralBankService.CENTRAL_BANK_EXECUTOR_D_ACCOUNT_ID) {
                CentralBankService.getExecutorBalance()
            } else {
                CentralBankService.getReserveBalance()
            }
        }
        return EconomyStorageService.getBalance(accountID, currency)
    }

    private fun validateWithdraw(accountID: UUID, currency: String, amount: BigDecimal): String? {
        if (CentralBankService.isCentralBankAccount(accountID) && CentralBankService.isManagedCurrency(currency)) {
            val reserve = resolveBalance(accountID, currency)
            if (reserve < amount) return "CENTRAL_BANK_RESERVE_EXHAUSTED"
            return null
        }
        if (CentralBankService.isManagedPlayerAccount(accountID, currency)) {
            if (!EconomyStorageService.has(accountID, currency, amount)) return "INSUFFICIENT_FUNDS"
            val execBal = CentralBankService.getExecutorBalance()
            if (execBal < amount && execBal + CentralBankService.getReserveBalance() < amount)
                return "CENTRAL_BANK_RESERVE_EXHAUSTED"
            return null
        }
        if (!EconomyStorageService.has(accountID, currency, amount)) return "INSUFFICIENT_FUNDS"
        return null
    }

    private fun validateDeposit(accountID: UUID, currency: String, amount: BigDecimal): String? {
        if (CentralBankService.isManagedPlayerAccount(accountID, currency)) {
            val execBal = CentralBankService.getExecutorBalance()
            if (execBal < amount && execBal + CentralBankService.getReserveBalance() < amount)
                return "CENTRAL_BANK_RESERVE_EXHAUSTED"
        }
        return null
    }

    private fun response(amount: BigDecimal, balance: BigDecimal, type: EconomyResponse.ResponseType, message: String): EconomyResponse {
        return EconomyResponse(amount, balance, type, message)
    }
}

// ─────────────────────────────────────────────
// AsyncEconomy — VaultUnlockedAPI 2.20 异步支持
// ─────────────────────────────────────────────

class VaultUnlockedAsyncEconomy(private val sync: VaultUnlockedEconomy) : AsyncEconomy {

    // ── Account Management ──────────────────

    override fun createAccount(accountID: UUID, name: String, player: Boolean): CompletableFuture<Boolean> =
        completedFuture(true)

    override fun createAccount(accountID: UUID, name: String, worldName: String, player: Boolean): CompletableFuture<Boolean> =
        completedFuture(true)

    override fun getUUIDNameMap(): CompletableFuture<MutableMap<UUID, String>> =
        CompletableFuture.completedFuture<MutableMap<UUID, String>>(HashMap())

    override fun getAccountName(accountID: UUID): CompletableFuture<Optional<String>> =
        CompletableFuture.completedFuture(Optional.empty())

    override fun hasAccount(accountID: UUID): CompletableFuture<Boolean> =
        completedFuture(true)

    override fun hasAccount(accountID: UUID, worldName: String): CompletableFuture<Boolean> =
        completedFuture(true)

    override fun renameAccount(pluginName: String, accountID: UUID, name: String): CompletableFuture<Boolean> =
        completedFuture(true)

    override fun deleteAccount(pluginName: String, accountID: UUID): CompletableFuture<Boolean> =
        completedFuture(true)

    // ── Currency ────────────────────────────

    override fun accountSupportsCurrency(pluginName: String, accountID: UUID, currency: String): CompletableFuture<Boolean> =
        supplyAsync { EconomySettings.getCurrencyKeys().contains(EconomySettings.resolveCurrency(currency)) }

    override fun accountSupportsCurrency(pluginName: String, accountID: UUID, currency: String, world: String): CompletableFuture<Boolean> =
        supplyAsync { EconomySettings.getCurrencyKeys().contains(EconomySettings.resolveCurrency(currency)) }

    // ── Balance ─────────────────────────────

    override fun balance(pluginName: String, accountID: UUID): CompletableFuture<BigDecimal> =
        supplyAsync { sync.resolveBalance(accountID, EconomySettings.defaultCurrency) }

    override fun balance(pluginName: String, accountID: UUID, world: String): CompletableFuture<BigDecimal> =
        balance(pluginName, accountID)

    override fun balance(pluginName: String, accountID: UUID, world: String, currency: String): CompletableFuture<BigDecimal> =
        supplyAsync { sync.resolveBalance(accountID, EconomySettings.resolveCurrency(currency)) }

    // ── has ─────────────────────────────────

    override fun has(pluginName: String, accountID: UUID, amount: BigDecimal): CompletableFuture<Boolean> =
        supplyAsync { sync.resolveBalance(accountID, EconomySettings.defaultCurrency) >= amount }

    override fun has(pluginName: String, accountID: UUID, world: String, amount: BigDecimal): CompletableFuture<Boolean> =
        has(pluginName, accountID, amount)

    override fun has(pluginName: String, accountID: UUID, world: String, currency: String, amount: BigDecimal): CompletableFuture<Boolean> =
        supplyAsync { sync.resolveBalance(accountID, EconomySettings.resolveCurrency(currency)) >= amount }

    // ── Transactions (delegate to sync) ─────

    override fun set(pluginName: String, accountID: UUID, amount: BigDecimal): CompletableFuture<EconomyResponse> =
        supplyAsync { sync.set(pluginName, accountID, amount) }

    override fun set(pluginName: String, accountID: UUID, world: String, amount: BigDecimal): CompletableFuture<EconomyResponse> =
        supplyAsync { sync.set(pluginName, accountID, world, amount) }

    override fun set(pluginName: String, accountID: UUID, world: String, currency: String, amount: BigDecimal): CompletableFuture<EconomyResponse> =
        supplyAsync { sync.set(pluginName, accountID, world, currency, amount) }

    override fun transfer(pluginName: String, from: UUID, to: UUID, amount: BigDecimal): CompletableFuture<MultiEconomyResponse> =
        supplyAsync { sync.transfer(pluginName, from, to, amount) }

    override fun transfer(pluginName: String, from: UUID, to: UUID, worldName: String, amount: BigDecimal): CompletableFuture<MultiEconomyResponse> =
        supplyAsync { sync.transfer(pluginName, from, to, worldName, amount) }

    override fun transfer(pluginName: String, from: UUID, to: UUID, worldName: String, currency: String, amount: BigDecimal): CompletableFuture<MultiEconomyResponse> =
        supplyAsync { sync.transfer(pluginName, from, to, worldName, currency, amount) }

    override fun canWithdraw(pluginName: String, accountID: UUID, amount: BigDecimal): CompletableFuture<EconomyResponse> =
        supplyAsync { sync.canWithdraw(pluginName, accountID, amount) }

    override fun canWithdraw(pluginName: String, accountID: UUID, world: String, amount: BigDecimal): CompletableFuture<EconomyResponse> =
        supplyAsync { sync.canWithdraw(pluginName, accountID, world, amount) }

    override fun canWithdraw(pluginName: String, accountID: UUID, world: String, currency: String, amount: BigDecimal): CompletableFuture<EconomyResponse> =
        supplyAsync { sync.canWithdraw(pluginName, accountID, world, currency, amount) }

    override fun withdraw(pluginName: String, accountID: UUID, amount: BigDecimal): CompletableFuture<EconomyResponse> =
        supplyAsync { sync.withdraw(pluginName, accountID, "", amount) }

    override fun withdraw(pluginName: String, accountID: UUID, world: String, amount: BigDecimal): CompletableFuture<EconomyResponse> =
        supplyAsync { sync.withdraw(pluginName, accountID, world, amount) }

    override fun withdraw(pluginName: String, accountID: UUID, world: String, currency: String, amount: BigDecimal): CompletableFuture<EconomyResponse> =
        supplyAsync { sync.withdraw(pluginName, accountID, world, currency, amount) }

    override fun canDeposit(pluginName: String, accountID: UUID, amount: BigDecimal): CompletableFuture<EconomyResponse> =
        supplyAsync { sync.canDeposit(pluginName, accountID, "", amount) }

    override fun canDeposit(pluginName: String, accountID: UUID, world: String, amount: BigDecimal): CompletableFuture<EconomyResponse> =
        supplyAsync { sync.canDeposit(pluginName, accountID, world, amount) }

    override fun canDeposit(pluginName: String, accountID: UUID, world: String, currency: String, amount: BigDecimal): CompletableFuture<EconomyResponse> =
        supplyAsync { sync.canDeposit(pluginName, accountID, world, currency, amount) }

    override fun deposit(pluginName: String, accountID: UUID, amount: BigDecimal): CompletableFuture<EconomyResponse> =
        supplyAsync { sync.deposit(pluginName, accountID, "", amount) }

    override fun deposit(pluginName: String, accountID: UUID, world: String, amount: BigDecimal): CompletableFuture<EconomyResponse> =
        supplyAsync { sync.deposit(pluginName, accountID, world, amount) }

    override fun deposit(pluginName: String, accountID: UUID, world: String, currency: String, amount: BigDecimal): CompletableFuture<EconomyResponse> =
        supplyAsync { sync.deposit(pluginName, accountID, world, currency, amount) }

    // ── Shared Accounts ─────────────────────

    override fun createSharedAccount(pluginName: String, accountID: UUID, name: String, owner: UUID): CompletableFuture<Boolean> =
        completedFuture(false)

    override fun accountsWithOwnerOf(pluginName: String, accountID: UUID): CompletableFuture<MutableList<UUID>> =
        CompletableFuture.completedFuture<MutableList<UUID>>(ArrayList())

    override fun accountsWithMembershipTo(pluginName: String, accountID: UUID): CompletableFuture<MutableList<UUID>> =
        CompletableFuture.completedFuture<MutableList<UUID>>(ArrayList())

    override fun accountsWithAccessTo(pluginName: String, accountID: UUID, vararg permissions: AccountPermission): CompletableFuture<MutableList<UUID>> =
        CompletableFuture.completedFuture<MutableList<UUID>>(ArrayList())

    override fun isAccountOwner(pluginName: String, accountID: UUID, uuid: UUID): CompletableFuture<Boolean> =
        completedFuture(false)

    override fun setOwner(pluginName: String, accountID: UUID, uuid: UUID): CompletableFuture<Boolean> =
        completedFuture(false)

    override fun isAccountMember(pluginName: String, accountID: UUID, uuid: UUID): CompletableFuture<Boolean> =
        completedFuture(false)

    override fun addAccountMember(pluginName: String, accountID: UUID, uuid: UUID): CompletableFuture<Boolean> =
        completedFuture(false)

    override fun addAccountMember(pluginName: String, accountID: UUID, uuid: UUID, vararg initialPermissions: AccountPermission): CompletableFuture<Boolean> =
        completedFuture(false)

    override fun removeAccountMember(pluginName: String, accountID: UUID, uuid: UUID): CompletableFuture<Boolean> =
        completedFuture(false)

    override fun hasAccountPermission(pluginName: String, accountID: UUID, uuid: UUID, permission: AccountPermission): CompletableFuture<Boolean> =
        completedFuture(false)

    override fun updateAccountPermission(pluginName: String, accountID: UUID, uuid: UUID, permission: AccountPermission, value: Boolean): CompletableFuture<Boolean> =
        completedFuture(false)

    // ── Helpers ─────────────────────────────

    private fun <T> supplyAsync(action: () -> T): CompletableFuture<T> =
        CompletableFuture.supplyAsync(action)

    private fun <T> completedFuture(value: T): CompletableFuture<T> =
        CompletableFuture.completedFuture(value)
}

// ─────────────────────────────────────────────
// Vault1EconomyBridge — 兼容 Vault1 旧版 API
// ─────────────────────────────────────────────

class Vault1EconomyBridge : Vault1Economy {

    private val sync: VaultUnlockedEconomy
        get() = EconomyProvider.economy

    // ── Meta ────────────────────────────────────
    override fun isEnabled(): Boolean = EconomySettings.enabled
    override fun getName(): String = "PHCore Economy"
    override fun hasBankSupport(): Boolean = false
    override fun fractionalDigits(): Int = 0

    // ── Format / Currency ───────────────────────
    override fun format(amount: Double): String =
        sync.format("phcore", BigDecimal.valueOf(amount))

    override fun currencyNamePlural(): String =
        EconomySettings.getDefinition(EconomySettings.defaultCurrency).plural

    override fun currencyNameSingular(): String =
        EconomySettings.getDefinition(EconomySettings.defaultCurrency).singular

    // ── Account ─────────────────────────────────
    override fun hasAccount(playerName: String): Boolean = true
    override fun hasAccount(playerName: String, worldName: String): Boolean = true
    override fun hasAccount(player: OfflinePlayer): Boolean = true
    override fun hasAccount(player: OfflinePlayer, worldName: String): Boolean = true

    override fun createPlayerAccount(playerName: String): Boolean = true
    override fun createPlayerAccount(playerName: String, worldName: String): Boolean = true
    override fun createPlayerAccount(player: OfflinePlayer): Boolean = true
    override fun createPlayerAccount(player: OfflinePlayer, worldName: String): Boolean = true

    // ── Balance ─────────────────────────────────
    override fun getBalance(playerName: String): Double {
        val uuid = Bukkit.getOfflinePlayer(playerName).uniqueId
        return sync.resolveBalance(uuid, EconomySettings.defaultCurrency).toDouble()
    }

    override fun getBalance(playerName: String, worldName: String): Double =
        getBalance(playerName)

    override fun getBalance(player: OfflinePlayer): Double =
        sync.resolveBalance(player.uniqueId, EconomySettings.defaultCurrency).toDouble()

    override fun getBalance(player: OfflinePlayer, worldName: String): Double =
        getBalance(player)

    // ── has ─────────────────────────────────────
    override fun has(playerName: String, amount: Double): Boolean {
        val uuid = Bukkit.getOfflinePlayer(playerName).uniqueId
        return sync.resolveBalance(uuid, EconomySettings.defaultCurrency) >= BigDecimal.valueOf(amount)
    }

    override fun has(playerName: String, worldName: String, amount: Double): Boolean =
        has(playerName, amount)

    override fun has(player: OfflinePlayer, amount: Double): Boolean =
        sync.resolveBalance(player.uniqueId, EconomySettings.defaultCurrency) >= BigDecimal.valueOf(amount)

    override fun has(player: OfflinePlayer, worldName: String, amount: Double): Boolean =
        has(player, amount)

    // ── Withdraw ────────────────────────────────
    override fun withdrawPlayer(playerName: String, amount: Double): Vault1EconomyResponse {
        val player = Bukkit.getOfflinePlayer(playerName)
        return withdrawPlayer(player, amount)
    }

    override fun withdrawPlayer(playerName: String, worldName: String, amount: Double): Vault1EconomyResponse =
        withdrawPlayer(playerName, amount)

    override fun withdrawPlayer(player: OfflinePlayer, amount: Double): Vault1EconomyResponse {
        val bdAmount = BigDecimal.valueOf(amount).setScale(0, RoundingMode.HALF_UP)
        val resp = sync.withdraw("phcore", player.uniqueId, "", EconomySettings.defaultCurrency, bdAmount)
        return v1Response(resp)
    }

    override fun withdrawPlayer(player: OfflinePlayer, worldName: String, amount: Double): Vault1EconomyResponse =
        withdrawPlayer(player, amount)

    // ── Deposit ─────────────────────────────────
    override fun depositPlayer(playerName: String, amount: Double): Vault1EconomyResponse {
        val player = Bukkit.getOfflinePlayer(playerName)
        return depositPlayer(player, amount)
    }

    override fun depositPlayer(playerName: String, worldName: String, amount: Double): Vault1EconomyResponse =
        depositPlayer(playerName, amount)

    override fun depositPlayer(player: OfflinePlayer, amount: Double): Vault1EconomyResponse {
        val bdAmount = BigDecimal.valueOf(amount).setScale(0, RoundingMode.HALF_UP)
        val resp = sync.deposit("phcore", player.uniqueId, "", EconomySettings.defaultCurrency, bdAmount)
        return v1Response(resp)
    }

    override fun depositPlayer(player: OfflinePlayer, worldName: String, amount: Double): Vault1EconomyResponse =
        depositPlayer(player, amount)

    // ── Banks (不支持) ──────────────────────────
    override fun bankBalance(name: String): Vault1EconomyResponse =
        Vault1EconomyResponse(0.0, 0.0, Vault1EconomyResponse.ResponseType.NOT_IMPLEMENTED, "")

    override fun bankHas(name: String, amount: Double): Vault1EconomyResponse =
        Vault1EconomyResponse(0.0, 0.0, Vault1EconomyResponse.ResponseType.NOT_IMPLEMENTED, "")

    override fun bankWithdraw(name: String, amount: Double): Vault1EconomyResponse =
        Vault1EconomyResponse(0.0, 0.0, Vault1EconomyResponse.ResponseType.NOT_IMPLEMENTED, "")

    override fun bankDeposit(name: String, amount: Double): Vault1EconomyResponse =
        Vault1EconomyResponse(0.0, 0.0, Vault1EconomyResponse.ResponseType.NOT_IMPLEMENTED, "")

    override fun isBankOwner(name: String, playerName: String): Vault1EconomyResponse =
        Vault1EconomyResponse(0.0, 0.0, Vault1EconomyResponse.ResponseType.NOT_IMPLEMENTED, "")

    override fun isBankOwner(name: String, player: OfflinePlayer): Vault1EconomyResponse =
        Vault1EconomyResponse(0.0, 0.0, Vault1EconomyResponse.ResponseType.NOT_IMPLEMENTED, "")

    override fun isBankMember(name: String, playerName: String): Vault1EconomyResponse =
        Vault1EconomyResponse(0.0, 0.0, Vault1EconomyResponse.ResponseType.NOT_IMPLEMENTED, "")

    override fun isBankMember(name: String, player: OfflinePlayer): Vault1EconomyResponse =
        Vault1EconomyResponse(0.0, 0.0, Vault1EconomyResponse.ResponseType.NOT_IMPLEMENTED, "")

    override fun getBanks(): MutableList<String> = ArrayList()

    override fun createBank(name: String, playerName: String): Vault1EconomyResponse =
        Vault1EconomyResponse(0.0, 0.0, Vault1EconomyResponse.ResponseType.NOT_IMPLEMENTED, "")

    override fun createBank(name: String, player: OfflinePlayer): Vault1EconomyResponse =
        Vault1EconomyResponse(0.0, 0.0, Vault1EconomyResponse.ResponseType.NOT_IMPLEMENTED, "")

    override fun deleteBank(name: String): Vault1EconomyResponse =
        Vault1EconomyResponse(0.0, 0.0, Vault1EconomyResponse.ResponseType.NOT_IMPLEMENTED, "")

    // ── Helpers ─────────────────────────────────
    private fun v1Response(resp: EconomyResponse): Vault1EconomyResponse {
        val type = when (resp.type) {
            EconomyResponse.ResponseType.SUCCESS -> Vault1EconomyResponse.ResponseType.SUCCESS
            EconomyResponse.ResponseType.FAILURE -> Vault1EconomyResponse.ResponseType.FAILURE
            EconomyResponse.ResponseType.NOT_IMPLEMENTED -> Vault1EconomyResponse.ResponseType.NOT_IMPLEMENTED
        }
        return Vault1EconomyResponse(resp.amount.toDouble(), resp.balance.toDouble(), type, resp.errorMessage)
    }
}