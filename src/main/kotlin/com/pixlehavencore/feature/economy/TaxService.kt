package com.pixlehavencore.feature.economy

import com.pixlehavencore.util.PlaceholderUtils.resolvePlaceholders
import com.pixlehavencore.util.DatabaseUtils
import com.pixlehavencore.util.EconomyUtils
import com.pixlehavencore.util.PerKeyLock
import com.pixlehavencore.util.broadcastColored
import com.pixlehavencore.util.cancelTaskSafely
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import taboolib.expansion.MultipleHandler
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object TaxService {

    private const val TABLE_NAME = "tax_income_state"
    private const val KEY_CURRENT_INCOME = "current_income"
    private const val KEY_TAX_DEBT = "tax_debt"

    private const val ERROR_NOT_INITIALIZED = "尚未初始化"
    private const val ERROR_STORAGE_UNAVAILABLE = "持久化暂不可用"
    private const val ERROR_CLOSED = "已关闭"
    private const val REASON_NEVER = "从未"

    private val incomePools = ConcurrentHashMap<UUID, BigDecimal>()
    private val taxDebts = ConcurrentHashMap<UUID, BigDecimal>()
    private val dirtyAccounts = ConcurrentHashMap.newKeySet<UUID>()
    private val accountLocks = PerKeyLock<UUID>()

    private val totalIncome = java.util.concurrent.atomic.AtomicReference(BigDecimal.ZERO)
    private val totalDueTax = java.util.concurrent.atomic.AtomicReference(BigDecimal.ZERO)
    private val totalDebt = java.util.concurrent.atomic.AtomicReference(BigDecimal.ZERO)
    private val storageDirty = AtomicBoolean(false)

    @Volatile
    private var schedulerTask: Any? = null

    @Volatile
    private var persistTask: Any? = null

    @Volatile
    private var handler: MultipleHandler? = null

    @Volatile
    private var storageReady: Boolean = false

    @Volatile
    private var storageInitInProgress: Boolean = false

    @Volatile
    private var storageLastError: String = ERROR_NOT_INITIALIZED

    @Volatile
    private var hasLoadedPersistedData: Boolean = false

    @Volatile
    private var lastSettledMarker: Long = 0L

    @Volatile
    private var lastSettlementAtEpochMillis: Long = 0L

    @Volatile
    private var lastSettlementAmount: BigDecimal = BigDecimal.ZERO

    @Volatile
    private var lastSettlementOutstandingDebt: BigDecimal = BigDecimal.ZERO

    @Volatile
    private var lastSettlementReason: String = REASON_NEVER

    @Volatile
    private var shuttingDown: Boolean = false

    private val storageLock = Any()

    fun init() {
        shuttingDown = false
        resetRuntimeState(clearLastSettlement = false)
        TaxSettings.init()
        ensureStorageInitialized()
        startPersistenceTaskIfNeeded()
        startSchedulerIfNeeded()
    }

    fun reload() {
        shuttingDown = false
        stopScheduler()
        stopPersistenceTask(flushAsync = false)
        closeStorage()
        resetRuntimeState(clearLastSettlement = false)
        TaxSettings.reload()
        ensureStorageInitialized()
        startPersistenceTaskIfNeeded()
        startSchedulerIfNeeded()
    }

    fun shutdown() {
        shuttingDown = true
        stopScheduler()
        stopPersistenceTask(flushAsync = false)
        persistAccountsIfNeeded(force = true, allowInit = false)
        closeStorage()
    }

    fun applyMenuTradeTax(amount: BigDecimal): TaxResult = computeTax(amount, IncomeScene.MENU_TRADE)

    fun applyCommandTradeTax(amount: BigDecimal): TaxResult = computeTax(amount, IncomeScene.COMMAND_TRADE)

    fun applyPlayerTradeTax(amount: BigDecimal, customRate: Double? = null): TaxResult {
        val rate = (customRate ?: TaxSettings.defaultPlayerTradeTaxRate).coerceAtLeast(0.0)
        return computeTax(amount, IncomeScene.PLAYER_TRADE, rateOverride = rate)
    }

    fun recordGenericIncome(accountId: UUID, amount: BigDecimal, currency: String = EconomySettings.defaultCurrency) {
        recordIncome(accountId, amount, currency, IncomeScene.GENERIC)
    }

    fun recordVaultIncome(pluginName: String, accountId: UUID, amount: BigDecimal, currency: String = EconomySettings.defaultCurrency) {
        if (EconomyUtils.isInternalPlugin(pluginName) || CentralBankService.isCentralBankAccount(accountId) || CentralBankService.isExemptAccount(accountId)) {
            return
        }
        recordGenericIncome(accountId, amount, currency)
    }

    fun recordCommandTradeIncome(accountId: UUID, amount: BigDecimal, currency: String = EconomySettings.defaultCurrency) {
        recordIncome(accountId, amount, currency, IncomeScene.COMMAND_TRADE)
    }

    fun recordPlayerTradeIncome(accountId: UUID, amount: BigDecimal, currency: String = EconomySettings.defaultCurrency) {
        recordIncome(accountId, amount, currency, IncomeScene.PLAYER_TRADE)
    }

    fun recordMenuTradeIncome(accountId: UUID, amount: BigDecimal, currency: String = EconomySettings.defaultCurrency) {
        recordIncome(accountId, amount, currency, IncomeScene.MENU_TRADE)
    }

    fun getPendingIncome(): BigDecimal {
        return normalizeAmount(totalIncome.get())
    }

    fun getPendingTax(): BigDecimal {
        return normalizeAmount(totalDueTax.get())
    }

    fun getPendingDebt(): BigDecimal {
        return normalizeAmount(totalDebt.get())
    }

    fun getPlayerCurrentIncome(accountId: UUID): BigDecimal {
        return normalizeAmount(incomePools[accountId] ?: BigDecimal.ZERO)
    }

    fun getPlayerTaxDebt(accountId: UUID): BigDecimal {
        return normalizeAmount(taxDebts[accountId] ?: BigDecimal.ZERO)
    }

    fun getPlayerTaxDue(accountId: UUID): BigDecimal {
        return calculateDue(getPlayerCurrentIncome(accountId), getPlayerTaxDebt(accountId))
    }

    fun getNextSettlementSeconds(): Long {
        if (!TaxSettings.settlementEnabled) {
            return -1L
        }
        val target = nextSettlementTime(LocalDateTime.now())
        val nowMillis = System.currentTimeMillis()
        val targetMillis = target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return ((targetMillis - nowMillis) / 1000L).coerceAtLeast(0L)
    }

    fun getStatusSnapshot(): TaxStatusSnapshot {
        return TaxStatusSnapshot(
            enabled = TaxSettings.enabled,
            menuTradeEnabled = TaxSettings.menuTradeEnabled,
            commandTradeEnabled = TaxSettings.commandTradeEnabled,
            playerTradeEnabled = TaxSettings.playerTradeEnabled,
            defaultPlayerTradeTaxRate = TaxSettings.defaultPlayerTradeTaxRate,
            bracketCount = TaxSettings.brackets.size,
            settlementEnabled = TaxSettings.settlementEnabled,
            settlementHour = TaxSettings.settlementHour,
            settlementMinute = TaxSettings.settlementMinute,
            settlementCheckIntervalTicks = TaxSettings.settlementCheckIntervalTicks,
            poolPersistIntervalTicks = TaxSettings.poolPersistIntervalTicks,
            nextSettlementSeconds = getNextSettlementSeconds(),
            pendingIncome = getPendingIncome(),
            pendingTax = getPendingTax(),
            pendingDebt = getPendingDebt(),
            storageReady = storageReady,
            storageInitializing = storageInitInProgress,
            storageDirty = storageDirty.get(),
            storageLastError = storageLastError,
            lastSettlementAtEpochMillis = lastSettlementAtEpochMillis,
            lastSettlementAmount = normalizeAmount(lastSettlementAmount),
            lastSettlementOutstandingDebt = normalizeAmount(lastSettlementOutstandingDebt),
            lastSettlementReason = lastSettlementReason,
        )
    }

    fun settleNow(): TaxSettleResult {
        ensureStorageInitialized()
        val accountIds = (incomePools.keys + taxDebts.keys).toSet()
        var settled = BigDecimal.ZERO
        var outstandingDebt = BigDecimal.ZERO

        accountIds.forEach { accountId ->
            synchronized(accountLocks[accountId]) {
                val currentIncome = currentIncomeOf(accountId)
                val currentDebt = currentDebtOf(accountId)
                if (currentIncome <= BigDecimal.ZERO && currentDebt <= BigDecimal.ZERO) {
                    return@synchronized
                }

                val currentDue = calculateDue(currentIncome, currentDebt)
                if (currentDue <= BigDecimal.ZERO) {
                    updateAccountStateLocked(accountId, BigDecimal.ZERO, BigDecimal.ZERO)
                    return@synchronized
                }

                val collected = collectTaxFromAccount(accountId, currentDue)
                val remainingDebt = normalizeAmount(currentDue.subtract(collected))
                updateAccountStateLocked(accountId, BigDecimal.ZERO, remainingDebt)
                settled = settled.add(collected)
                outstandingDebt = outstandingDebt.add(remainingDebt)
            }
        }

        CentralBankService.recordCollectedTax(normalizeAmount(settled))
        val normalizedSettled = normalizeAmount(settled)
        val normalizedDebt = normalizeAmount(outstandingDebt)
        val reason = when {
            normalizedSettled <= BigDecimal.ZERO && normalizedDebt <= BigDecimal.ZERO -> "EMPTY"
            normalizedDebt > BigDecimal.ZERO -> "PARTIAL"
            else -> "OK"
        }
        recordSettlementResult(normalizedSettled, normalizedDebt, reason)
        persistAccountsAsync(force = true, allowInit = true)
        return TaxSettleResult(
            success = true,
            settled = normalizedSettled,
            outstandingDebt = normalizedDebt,
            reason = reason,
        )
    }

    private fun recordIncome(accountId: UUID, amount: BigDecimal, currency: String, scene: IncomeScene) {
        val normalizedAmount = normalizeAmount(amount)
        if (!TaxSettings.enabled || normalizedAmount <= BigDecimal.ZERO || !scene.isEnabled()) {
            return
        }
        if (EconomySettings.resolveCurrency(currency) != EconomySettings.defaultCurrency) {
            return
        }
        ensureStorageInitialized()
        synchronized(accountLocks[accountId]) {
            updateAccountStateLocked(
                accountId = accountId,
                income = currentIncomeOf(accountId).add(normalizedAmount),
                debt = currentDebtOf(accountId),
            )
        }
    }

    private fun computeTax(amount: BigDecimal, scene: IncomeScene, rateOverride: Double? = null): TaxResult {
        if (!TaxSettings.enabled || !scene.isEnabled() || amount <= BigDecimal.ZERO) {
            return TaxResult(amount = amount, tax = BigDecimal.ZERO, rate = 0.0, success = true, reason = "DISABLED")
        }
        return previewTax(amount, rateOverride = rateOverride)
    }

    private fun previewTax(amount: BigDecimal, rateOverride: Double? = null): TaxResult {
        val normalizedAmount = normalizeAmount(amount)
        val rate = rateOverride ?: TaxSettings.resolveRate(normalizedAmount)
        val tax = normalizeAmount(normalizedAmount.multiply(rate.toBigDecimal()))
        if (tax <= BigDecimal.ZERO) {
            return TaxResult(amount = normalizedAmount, tax = BigDecimal.ZERO, rate = rate, success = true, reason = "NO_TAX")
        }
        return TaxResult(amount = normalizedAmount, tax = tax, rate = rate, success = true, reason = "OK")
    }

    private fun calculateDue(income: BigDecimal, debt: BigDecimal): BigDecimal {
        val normalizedIncome = normalizeAmount(income)
        val normalizedDebt = normalizeAmount(debt)
        if (normalizedIncome <= BigDecimal.ZERO && normalizedDebt <= BigDecimal.ZERO) {
            return BigDecimal.ZERO
        }
        val incomeTax = previewTax(normalizedIncome).tax
        return normalizeAmount(normalizedDebt.add(incomeTax))
    }

    private fun collectTaxFromAccount(accountId: UUID, due: BigDecimal): BigDecimal {
        val normalizedDue = normalizeAmount(due)
        if (normalizedDue <= BigDecimal.ZERO) {
            return BigDecimal.ZERO
        }
        val availableBalance = EconomyStorageService.getBalance(accountId, EconomySettings.defaultCurrency).coerceAtLeast(BigDecimal.ZERO)
        val collected = normalizedDue.min(availableBalance)
        if (collected <= BigDecimal.ZERO) {
            return BigDecimal.ZERO
        }
        return if (CentralBankService.isManagedPlayerAccount(accountId, EconomySettings.defaultCurrency)) {
            if (CentralBankService.withdrawFromPlayer(accountId, collected) == null) BigDecimal.ZERO else collected
        } else {
            if (!EconomyStorageService.has(accountId, EconomySettings.defaultCurrency, collected)) {
                BigDecimal.ZERO
            } else {
                EconomyStorageService.rawWithdraw(accountId, EconomySettings.defaultCurrency, collected, exempt = true)
                EconomyStorageService.rawDeposit(CentralBankService.CENTRAL_BANK_EXECUTOR_D_ACCOUNT_ID, EconomySettings.defaultCurrency, collected, exempt = true)
                collected
            }
        }
    }

    private fun updateAccountStateLocked(accountId: UUID, income: BigDecimal, debt: BigDecimal) {
        val previousIncome = currentIncomeOf(accountId)
        val previousDebt = currentDebtOf(accountId)
        val normalizedIncome = normalizeAmount(income)
        val normalizedDebt = normalizeAmount(debt)
        if (previousIncome == normalizedIncome && previousDebt == normalizedDebt) {
            return
        }

        val previousDue = calculateDue(previousIncome, previousDebt)
        val newDue = calculateDue(normalizedIncome, normalizedDebt)

        totalIncome.set(normalizeAmount(totalIncome.get().subtract(previousIncome).add(normalizedIncome)))
        totalDebt.set(normalizeAmount(totalDebt.get().subtract(previousDebt).add(normalizedDebt)))
        totalDueTax.set(normalizeAmount(totalDueTax.get().subtract(previousDue).add(newDue)))

        if (normalizedIncome > BigDecimal.ZERO) {
            incomePools[accountId] = normalizedIncome
        } else {
            incomePools.remove(accountId)
        }

        if (normalizedDebt > BigDecimal.ZERO) {
            taxDebts[accountId] = normalizedDebt
        } else {
            taxDebts.remove(accountId)
        }

        dirtyAccounts += accountId
        storageDirty.set(true)
    }

    private fun currentIncomeOf(accountId: UUID): BigDecimal {
        return normalizeAmount(incomePools[accountId] ?: BigDecimal.ZERO)
    }

    private fun currentDebtOf(accountId: UUID): BigDecimal {
        return normalizeAmount(taxDebts[accountId] ?: BigDecimal.ZERO)
    }

    private fun startSchedulerIfNeeded() {
        if (shuttingDown || !TaxSettings.enabled || !TaxSettings.settlementEnabled) {
            return
        }
        lastSettledMarker = currentSettlementMarker(LocalDateTime.now())
        schedulerTask = submitAsync(period = TaxSettings.settlementCheckIntervalTicks) {
            val now = LocalDateTime.now()
            val marker = currentSettlementMarker(now)
            if (marker == lastSettledMarker) {
                return@submitAsync
            }
            lastSettledMarker = marker
            val result = settleNow()
            if (!result.success) {
            warning("[税收] 定时结税失败: ${result.reason}")
                return@submitAsync
            }
            if (result.settled > BigDecimal.ZERO && TaxSettings.settlementBroadcast) {
                val message = TaxSettings.settlementBroadcastMessage
                    .resolvePlaceholders(
                        "{amount}" to EconomySettings.formatAmount(result.settled, EconomySettings.defaultCurrency),
                        "{outstanding}" to EconomySettings.formatAmount(result.outstandingDebt, EconomySettings.defaultCurrency)
                    )
                broadcastColored(message)
            }
        }
    }

    private fun startPersistenceTaskIfNeeded() {
        if (shuttingDown || !TaxSettings.enabled) {
            return
        }
        persistTask = submitAsync(period = TaxSettings.poolPersistIntervalTicks) {
            persistAccountsIfNeeded(force = false, allowInit = true)
        }
    }

    private fun stopScheduler() {
        schedulerTask.cancelTaskSafely()
        schedulerTask = null
    }

    private fun stopPersistenceTask(flushAsync: Boolean) {
        persistTask.cancelTaskSafely()
        persistTask = null
        if (flushAsync) {
            persistAccountsAsync(force = true, allowInit = true)
        }
    }

    private fun ensureStorageInitialized() {
        if (shuttingDown) {
            return
        }
        synchronized(storageLock) {
            if (handler != null || storageInitInProgress) {
                return
            }
            storageInitInProgress = true
        }

        submitAsync {
            runCatching {
                val created = DatabaseUtils.newPlayerDataHandler(TABLE_NAME, syncTick = 200L)
                handler = created
                storageReady = true
                storageLastError = ""
                if (!hasLoadedPersistedData) {
                    loadPersistedAccounts(created)
                    hasLoadedPersistedData = true
                }
            }.onFailure { ex ->
                storageReady = false
                storageLastError = ex.message ?: ex.javaClass.simpleName
                warning("[税收] 初始化收益池持久化失败: ${ex.message}")
            }

            synchronized(storageLock) {
                storageInitInProgress = false
            }
        }
    }

    private fun loadPersistedAccounts(currentHandler: MultipleHandler) {
        val loadedIncome = mutableMapOf<UUID, BigDecimal>()
        val loadedDebt = mutableMapOf<UUID, BigDecimal>()

        runCatching {
            currentHandler.database.getListByKey(KEY_CURRENT_INCOME).forEach { (user, payload) ->
                val accountId = runCatching { UUID.fromString(user) }.getOrNull() ?: return@forEach
                val income = normalizeAmount(payload.toBigDecimalOrNull() ?: BigDecimal.ZERO)
                if (income > BigDecimal.ZERO) {
                    loadedIncome[accountId] = income
                }
            }
            currentHandler.database.getListByKey(KEY_TAX_DEBT).forEach { (user, payload) ->
                val accountId = runCatching { UUID.fromString(user) }.getOrNull() ?: return@forEach
                val debt = normalizeAmount(payload.toBigDecimalOrNull() ?: BigDecimal.ZERO)
                if (debt > BigDecimal.ZERO) {
                    loadedDebt[accountId] = debt
                }
            }
        }.onFailure { ex ->
            warning("[税收] 读取收益池持久化数据失败: ${ex.message}")
        }

        (loadedIncome.keys + loadedDebt.keys).forEach { accountId ->
            synchronized(accountLocks[accountId]) {
                updateAccountStateLocked(
                    accountId = accountId,
                    income = currentIncomeOf(accountId).add(loadedIncome[accountId] ?: BigDecimal.ZERO),
                    debt = currentDebtOf(accountId).add(loadedDebt[accountId] ?: BigDecimal.ZERO),
                )
            }
        }
        if (dirtyAccounts.isNotEmpty()) {
            storageDirty.set(true)
        }
    }

    private fun persistAccountsIfNeeded(force: Boolean, allowInit: Boolean) {
        if (!force && !storageDirty.compareAndSet(true, false)) {
            return
        }

        val currentHandler = handler
        if (currentHandler == null) {
            storageDirty.set(true)
            if (allowInit && !shuttingDown) {
                ensureStorageInitialized()
            } else if (storageLastError.isBlank()) {
                storageLastError = ERROR_STORAGE_UNAVAILABLE
            }
            return
        }

        val accountIds = if (force) {
            (dirtyAccounts + incomePools.keys + taxDebts.keys).toSet()
        } else {
            dirtyAccounts.toSet()
        }
        if (accountIds.isEmpty()) {
            return
        }

        runCatching {
            accountIds.forEach { accountId ->
                persistAccount(currentHandler, accountId)
                dirtyAccounts.remove(accountId)
            }
            storageReady = true
            storageLastError = ""
        }.onFailure { ex ->
            storageDirty.set(true)
            storageReady = false
            storageLastError = ex.message ?: ex.javaClass.simpleName
            warning("[税收] 持久化收益池失败: ${ex.message}")
        }

        if (dirtyAccounts.isNotEmpty()) {
            storageDirty.set(true)
        }
    }

    private fun persistAccount(currentHandler: MultipleHandler, accountId: UUID) {
        val user = accountId.toString()
        currentHandler.database[user, KEY_CURRENT_INCOME] = currentIncomeOf(accountId).toPlainString()
        currentHandler.database[user, KEY_TAX_DEBT] = currentDebtOf(accountId).toPlainString()
    }

    private fun persistAccountsAsync(force: Boolean, allowInit: Boolean) {
        if (shuttingDown) {
            persistAccountsIfNeeded(force = force, allowInit = false)
            return
        }
        submitAsync {
            persistAccountsIfNeeded(force = force, allowInit = allowInit)
        }
    }

    private fun closeStorage() {
        DatabaseUtils.closeMultipleHandler(handler)
        handler = null
        storageReady = false
        storageInitInProgress = false
        hasLoadedPersistedData = false
        if (storageLastError.isBlank()) {
            storageLastError = ERROR_CLOSED
        }
    }

    private fun recordSettlementResult(settled: BigDecimal, outstandingDebt: BigDecimal, reason: String) {
        lastSettlementAtEpochMillis = System.currentTimeMillis()
        lastSettlementAmount = normalizeAmount(settled)
        lastSettlementOutstandingDebt = normalizeAmount(outstandingDebt)
        lastSettlementReason = reason
    }

    private fun resetRuntimeState(clearLastSettlement: Boolean) {
        incomePools.clear()
        taxDebts.clear()
        dirtyAccounts.clear()
        accountLocks.clear()
        totalIncome.set(BigDecimal.ZERO)
        totalDueTax.set(BigDecimal.ZERO)
        totalDebt.set(BigDecimal.ZERO)
        storageDirty.set(false)
        if (clearLastSettlement) {
            lastSettlementAtEpochMillis = 0L
            lastSettlementAmount = BigDecimal.ZERO
            lastSettlementOutstandingDebt = BigDecimal.ZERO
            lastSettlementReason = REASON_NEVER
        }
    }

    private fun normalizeAmount(value: BigDecimal): BigDecimal {
        return value.setScale(0, RoundingMode.HALF_UP).coerceAtLeast(BigDecimal.ZERO)
    }


    private fun currentSettlementMarker(now: LocalDateTime): Long {
        val todayTarget = now.toLocalDate().atTime(TaxSettings.settlementHour, TaxSettings.settlementMinute)
        val markerTime = if (now.isBefore(todayTarget)) todayTarget.minusDays(1) else todayTarget
        return markerTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun nextSettlementTime(now: LocalDateTime): LocalDateTime {
        val todayTarget = now.toLocalDate().atTime(TaxSettings.settlementHour, TaxSettings.settlementMinute)
        return if (now.isBefore(todayTarget)) todayTarget else todayTarget.plusDays(1)
    }

    private enum class IncomeScene {
        GENERIC,
        MENU_TRADE,
        COMMAND_TRADE,
        PLAYER_TRADE,
        ;

        fun isEnabled(): Boolean {
            return when (this) {
                GENERIC -> true
                MENU_TRADE -> TaxSettings.menuTradeEnabled
                COMMAND_TRADE -> TaxSettings.commandTradeEnabled
                PLAYER_TRADE -> TaxSettings.playerTradeEnabled
            }
        }
    }

    data class TaxResult(
        val amount: BigDecimal,
        val tax: BigDecimal,
        val rate: Double,
        val success: Boolean,
        val reason: String,
    )

    data class TaxSettleResult(
        val success: Boolean,
        val settled: BigDecimal,
        val outstandingDebt: BigDecimal,
        val reason: String,
    )

    data class TaxStatusSnapshot(
        val enabled: Boolean,
        val menuTradeEnabled: Boolean,
        val commandTradeEnabled: Boolean,
        val playerTradeEnabled: Boolean,
        val defaultPlayerTradeTaxRate: Double,
        val bracketCount: Int,
        val settlementEnabled: Boolean,
        val settlementHour: Int,
        val settlementMinute: Int,
        val settlementCheckIntervalTicks: Long,
        val poolPersistIntervalTicks: Long,
        val nextSettlementSeconds: Long,
        val pendingIncome: BigDecimal,
        val pendingTax: BigDecimal,
        val pendingDebt: BigDecimal,
        val storageReady: Boolean,
        val storageInitializing: Boolean,
        val storageDirty: Boolean,
        val storageLastError: String,
        val lastSettlementAtEpochMillis: Long,
        val lastSettlementAmount: BigDecimal,
        val lastSettlementOutstandingDebt: BigDecimal,
        val lastSettlementReason: String,
    )
}
