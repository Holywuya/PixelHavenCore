package com.pixlehavencore.feature.economy

import net.milkbowl.vault2.economy.AccountPermission
import net.milkbowl.vault2.economy.Economy
import net.milkbowl.vault2.economy.EconomyResponse
import org.bukkit.Bukkit
import org.bukkit.plugin.ServicePriority
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Collection
import java.util.List
import java.util.Map
import java.util.Optional
import java.util.UUID
import java.util.HashMap
import java.util.ArrayList

object EconomyProvider {

    private lateinit var economy: VaultUnlockedEconomy

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
        val plugin = Bukkit.getPluginManager().getPlugin("phcore")
        if (plugin == null) {
            warning("[经济系统] 找不到核心插件实例，无法注册经济服务")
            return
        }
        Bukkit.getServicesManager().register(Economy::class.java, economy, plugin, ServicePriority.Normal)
        info("[经济系统] 底层经济服务已注册")
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
        }
        TaxService.shutdown()
        CentralBankService.stop()
        EconomyStorageService.stop()
    }

}

class VaultUnlockedEconomy : Economy {

    override fun isEnabled(): Boolean = EconomySettings.enabled

    override fun getName(): String = "PHCore Economy"

    override fun hasSharedAccountSupport(): Boolean = false

    override fun hasMultiCurrencySupport(): Boolean = true

    override fun fractionalDigits(pluginName: String): Int = 0

    override fun fractionalDigits(pluginName: String, currency: String): Int = 0

    @Deprecated("Vault legacy overload", level = DeprecationLevel.HIDDEN)
    override fun format(amount: BigDecimal): String = format("phcore", amount)

    override fun format(pluginName: String, amount: BigDecimal): String {
        return format(pluginName, amount, EconomySettings.defaultCurrency)
    }

    @Deprecated("Vault legacy overload", level = DeprecationLevel.HIDDEN)
    override fun format(amount: BigDecimal, currency: String): String = format("phcore", amount, currency)

    override fun format(pluginName: String, amount: BigDecimal, currency: String): String {
        val def = EconomySettings.getDefinition(currency)
        val scaledAmount = amount.setScale(0, RoundingMode.HALF_UP)
        val name = if (scaledAmount.abs().compareTo(BigDecimal.ONE) == 0) def.singular else def.plural
        return "${scaledAmount.toPlainString()} $name"
    }

    override fun hasCurrency(currency: String): Boolean = EconomySettings.getCurrencyKeys().contains(EconomySettings.resolveCurrency(currency))

    override fun getDefaultCurrency(pluginName: String): String = EconomySettings.defaultCurrency

    override fun defaultCurrencyNamePlural(pluginName: String): String = EconomySettings.getDefinition(EconomySettings.defaultCurrency).plural

    override fun defaultCurrencyNameSingular(pluginName: String): String = EconomySettings.getDefinition(EconomySettings.defaultCurrency).singular

    override fun currencies(): MutableCollection<String> = ArrayList<String>(EconomySettings.getCurrencyKeys())

    @Deprecated("Vault legacy overload", level = DeprecationLevel.HIDDEN)
    override fun createAccount(accountID: UUID, name: String): Boolean = true

    override fun createAccount(accountID: UUID, name: String, player: Boolean): Boolean = true

    @Deprecated("Vault legacy overload", level = DeprecationLevel.HIDDEN)
    override fun createAccount(accountID: UUID, name: String, worldName: String): Boolean = true

    override fun createAccount(accountID: UUID, name: String, worldName: String, player: Boolean): Boolean = true

    override fun getUUIDNameMap(): MutableMap<UUID, String> = HashMap<UUID, String>()

    override fun getAccountName(accountID: UUID): Optional<String> = Optional.empty()

    override fun hasAccount(accountID: UUID): Boolean = true

    override fun hasAccount(accountID: UUID, worldName: String): Boolean = true

    override fun renameAccount(accountID: UUID, name: String): Boolean = true

    override fun renameAccount(pluginName: String, accountID: UUID, name: String): Boolean = true

    override fun deleteAccount(pluginName: String, accountID: UUID): Boolean = true

    override fun accountSupportsCurrency(pluginName: String, accountID: UUID, currency: String): Boolean = hasCurrency(currency)

    override fun accountSupportsCurrency(pluginName: String, accountID: UUID, currency: String, world: String): Boolean = hasCurrency(currency)

    @Deprecated("Vault legacy overload", level = DeprecationLevel.HIDDEN)
    override fun getBalance(pluginName: String, accountID: UUID): BigDecimal = resolveBalance(accountID, EconomySettings.defaultCurrency)

    @Deprecated("Vault legacy overload", level = DeprecationLevel.HIDDEN)
    override fun getBalance(pluginName: String, accountID: UUID, world: String): BigDecimal =
        resolveBalance(accountID, EconomySettings.defaultCurrency)

    @Deprecated("Vault legacy overload", level = DeprecationLevel.HIDDEN)
    override fun getBalance(pluginName: String, accountID: UUID, world: String, currency: String): BigDecimal {
        val resolvedCurrency = EconomySettings.resolveCurrency(currency)
        return resolveBalance(accountID, resolvedCurrency)
    }

    override fun has(pluginName: String, accountID: UUID, amount: BigDecimal): Boolean {
        return runCatching {
            resolveBalance(accountID, EconomySettings.defaultCurrency) >= amount
        }.getOrDefault(false)
    }

    override fun has(pluginName: String, accountID: UUID, worldName: String, amount: BigDecimal): Boolean = has(pluginName, accountID, amount)

    override fun has(pluginName: String, accountID: UUID, worldName: String, currency: String, amount: BigDecimal): Boolean {
        return runCatching {
            val resolvedCurrency = EconomySettings.resolveCurrency(currency)
            resolveBalance(accountID, resolvedCurrency) >= amount
        }.getOrDefault(false)
    }

    @Deprecated("Vault legacy overload", level = DeprecationLevel.HIDDEN)
    override fun withdraw(pluginName: String, accountID: UUID, amount: BigDecimal): EconomyResponse = withdraw(pluginName, accountID, "", amount)

    override fun withdraw(pluginName: String, accountID: UUID, worldName: String, amount: BigDecimal): EconomyResponse =
        withdraw(pluginName, accountID, worldName, EconomySettings.defaultCurrency, amount)

    override fun withdraw(pluginName: String, accountID: UUID, worldName: String, currency: String, amount: BigDecimal): EconomyResponse {
        val resolvedCurrency = EconomySettings.resolveCurrency(currency)
        val currentBalance = resolveBalance(accountID, resolvedCurrency)
        if (amount.signum() <= 0) return response(amount, currentBalance, EconomyResponse.ResponseType.SUCCESS, "")
        if (CentralBankService.isCentralBankAccount(accountID) && CentralBankService.isManagedCurrency(resolvedCurrency)) {
            val balance = CentralBankService.drain(amount)
                ?: return response(amount, currentBalance, EconomyResponse.ResponseType.FAILURE, "INSUFFICIENT_FUNDS")
            return response(amount, balance, EconomyResponse.ResponseType.SUCCESS, "")
        }
        if (CentralBankService.isManagedPlayerAccount(accountID, resolvedCurrency)) {
            val balance = CentralBankService.withdrawFromPlayer(accountID, amount)
                ?: return response(amount, currentBalance, EconomyResponse.ResponseType.FAILURE, "INSUFFICIENT_FUNDS")
            return response(amount, balance, EconomyResponse.ResponseType.SUCCESS, "")
        }
        // 使用原子化的 tryWithdraw 避免 TOCTOU 竞态条件
        val balance = EconomyStorageService.tryWithdraw(accountID, resolvedCurrency, amount)
            ?: return response(amount, currentBalance, EconomyResponse.ResponseType.FAILURE, "INSUFFICIENT_FUNDS")
        return response(amount, balance, EconomyResponse.ResponseType.SUCCESS, "")
    }

    override fun deposit(pluginName: String, accountID: UUID, amount: BigDecimal): EconomyResponse = deposit(pluginName, accountID, "", amount)

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

    private fun resolveBalance(accountID: UUID, currency: String): BigDecimal {
        if (CentralBankService.isManagedCurrency(currency) && CentralBankService.isCentralBankAccount(accountID)) {
            return if (accountID == CentralBankService.CENTRAL_BANK_EXECUTOR_D_ACCOUNT_ID) {
                CentralBankService.getExecutorBalance()
            } else {
                CentralBankService.getReserveBalance()
            }
        }
        return EconomyStorageService.getBalance(accountID, currency)
    }

    override fun createSharedAccount(pluginName: String, accountID: UUID, name: String, owner: UUID): Boolean = false

    override fun accountsOwnedBy(pluginName: String, accountID: UUID): MutableList<String> = ArrayList<String>()

    override fun accountsMemberOf(pluginName: String, accountID: UUID): MutableList<String> = ArrayList<String>()

    override fun accountsAccessTo(pluginName: String, accountID: UUID, vararg permissions: AccountPermission): MutableList<String> = ArrayList<String>()

    override fun isAccountOwner(pluginName: String, accountID: UUID, uuid: UUID): Boolean = false

    override fun setOwner(pluginName: String, accountID: UUID, uuid: UUID): Boolean = false

    override fun isAccountMember(pluginName: String, accountID: UUID, uuid: UUID): Boolean = false

    override fun addAccountMember(pluginName: String, accountID: UUID, uuid: UUID): Boolean = false

    override fun addAccountMember(pluginName: String, accountID: UUID, uuid: UUID, vararg initialPermissions: AccountPermission): Boolean = false

    override fun removeAccountMember(pluginName: String, accountID: UUID, uuid: UUID): Boolean = false

    override fun hasAccountPermission(pluginName: String, accountID: UUID, uuid: UUID, permission: AccountPermission): Boolean = false

    override fun updateAccountPermission(pluginName: String, accountID: UUID, uuid: UUID, permission: AccountPermission, value: Boolean): Boolean = false

    private fun response(amount: BigDecimal, balance: BigDecimal, type: EconomyResponse.ResponseType, message: String): EconomyResponse {
        return EconomyResponse(amount, balance, type, message)
    }
}
