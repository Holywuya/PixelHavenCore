package com.pixlehavencore.feature.economy

import com.pixlehavencore.util.DatabaseUtils
import com.pixlehavencore.util.cancelTaskSafely
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.warning
import taboolib.expansion.MultipleHandler
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

object CentralBankService {

    // Alias for BHC-DACB: C Account / CentralBankReserve_C
    val CENTRAL_BANK_RESERVE_C_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000c1")

    // Alias for BHC-DACB: D Account / CentralBankExecutor_D
    val CENTRAL_BANK_EXECUTOR_D_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000d1")

    private val STATE_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000e1")

    private const val TABLE_NAME = "economy_central_bank_meta"
    private const val KEY_MAX_SUPPLY = "max_supply"
    private const val KEY_ACTIVE_M0 = "active_m0"
    private const val KEY_ACTIVE_PLAYER_COUNT = "active_player_count"
    private const val KEY_TOTAL_PLAYER_BALANCE = "total_player_balance"
    private const val KEY_PERIOD_TAX = "period_tax"
    private const val KEY_LAST_DORMANT_RECOVERY_AT = "last_dormant_recovery_at"
    private const val KEY_LAST_SYNC_AT = "last_sync_at"

    private const val WEEK_MILLIS = 7L * 24L * 60L * 60L * 1000L
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    private val stateLock = Any()
    private val dirty = AtomicBoolean(false)
    private var handler: MultipleHandler? = null
    private var maintenanceTask: Any? = null
    private var flushTask: Any? = null

    @Volatile
    private var maxSupply: BigDecimal = BigDecimal.ZERO

    @Volatile
    private var activeM0: BigDecimal = BigDecimal.ZERO

    @Volatile
    private var activePlayerCount: Int = 0

    @Volatile
    private var totalPlayerBalance: BigDecimal = BigDecimal.ZERO

    @Volatile
    private var periodTaxCollected: BigDecimal = BigDecimal.ZERO

    @Volatile
    private var lastDormantRecoveryAt: Long = 0L

    @Volatile
    private var lastSyncAt: Long = 0L

    @Volatile
    private var ready: Boolean = false

    fun init() {
        stop()
        if (!EconomySettings.enabled || !CentralBankSettings.enabled) {
            return
        }
        submit(async = true) {
            runCatching {
                initializeInternal()
            }.onFailure { ex ->
                warning("[经济系统] 央行初始化失败: ${ex.message}")
            }
        }
    }

    fun reload() {
        init()
    }

    fun stop() {
        maintenanceTask.cancelTaskSafely()
        flushTask.cancelTaskSafely()
        maintenanceTask = null
        flushTask = null
        persistState(force = true)
        DatabaseUtils.closeMultipleHandler(handler)
        handler = null
        ready = false
    }

    fun isReady(): Boolean {
        return ready
    }

    fun isManagedCurrency(currency: String): Boolean {
        return EconomySettings.enabled && CentralBankSettings.enabled &&
            EconomySettings.resolveCurrency(currency) == EconomySettings.defaultCurrency
    }

    fun isCentralBankAccount(accountId: UUID): Boolean {
        return accountId == CENTRAL_BANK_RESERVE_C_ACCOUNT_ID || accountId == CENTRAL_BANK_EXECUTOR_D_ACCOUNT_ID
    }

    fun isExemptAccount(accountId: UUID): Boolean {
        return accountId.toString().lowercase() in CentralBankSettings.exemptAccountIds
    }

    fun isManagedPlayerAccount(accountId: UUID, currency: String): Boolean {
        return isManagedCurrency(currency) && !isCentralBankAccount(accountId) && !isExemptAccount(accountId)
    }

    fun getReserveBalance(): BigDecimal {
        return EconomyStorageService.getBalance(CENTRAL_BANK_RESERVE_C_ACCOUNT_ID, EconomySettings.defaultCurrency)
    }

    fun getExecutorBalance(): BigDecimal {
        return EconomyStorageService.getBalance(CENTRAL_BANK_EXECUTOR_D_ACCOUNT_ID, EconomySettings.defaultCurrency)
    }

    fun getMaxSupply(): BigDecimal {
        return maxSupply
    }

    fun getActiveM0(): BigDecimal {
        return activeM0
    }

    fun getActivePlayerCount(): Int {
        return activePlayerCount
    }

    fun getTotalPlayerBalance(): BigDecimal {
        return totalPlayerBalance
    }

    fun getReserveRate(): BigDecimal {
        val reserve = getReserveBalance()
        val denominator = reserve.add(activeM0)
        if (denominator <= BigDecimal.ZERO) {
            return BigDecimal.ZERO
        }
        return reserve.divide(denominator, 6, RoundingMode.HALF_UP)
    }

    fun getPeriodTaxCollected(): BigDecimal {
        return periodTaxCollected
    }

    fun depositToPlayer(accountId: UUID, amount: BigDecimal): BigDecimal? {
        if (amount.signum() <= 0) {
            return EconomyStorageService.getBalance(accountId, EconomySettings.defaultCurrency)
        }
        synchronized(stateLock) {
            val reserve = getReserveBalance()
            if (reserve < amount) {
                return null
            }
            val balance = EconomyStorageService.rawDeposit(accountId, EconomySettings.defaultCurrency, amount)
            EconomyStorageService.rawWithdraw(CENTRAL_BANK_RESERVE_C_ACCOUNT_ID, EconomySettings.defaultCurrency, amount)
            syncExecutorBalanceLocked()
            dirty.set(true)
            return balance
        }
    }

    fun withdrawFromPlayer(accountId: UUID, amount: BigDecimal): BigDecimal? {
        if (amount.signum() <= 0) {
            return EconomyStorageService.getBalance(accountId, EconomySettings.defaultCurrency)
        }
        synchronized(stateLock) {
            if (!EconomyStorageService.has(accountId, EconomySettings.defaultCurrency, amount)) {
                return null
            }
            val balance = EconomyStorageService.rawWithdraw(accountId, EconomySettings.defaultCurrency, amount)
            EconomyStorageService.rawDeposit(CENTRAL_BANK_RESERVE_C_ACCOUNT_ID, EconomySettings.defaultCurrency, amount)
            syncExecutorBalanceLocked()
            dirty.set(true)
            return balance
        }
    }

    fun inject(amount: BigDecimal): BigDecimal {
        synchronized(stateLock) {
            val normalized = amount.coerceAtLeast(BigDecimal.ZERO)
            if (normalized > BigDecimal.ZERO) {
                EconomyStorageService.rawDeposit(CENTRAL_BANK_RESERVE_C_ACCOUNT_ID, EconomySettings.defaultCurrency, normalized)
                maxSupply = maxSupply.add(normalized)
                syncExecutorBalanceLocked()
                dirty.set(true)
            }
            return getReserveBalance()
        }
    }

    fun drain(amount: BigDecimal): BigDecimal? {
        synchronized(stateLock) {
            val normalized = amount.coerceAtLeast(BigDecimal.ZERO)
            val reserve = getReserveBalance()
            if (normalized > reserve) {
                return null
            }
            if (normalized > BigDecimal.ZERO) {
                EconomyStorageService.rawWithdraw(CENTRAL_BANK_RESERVE_C_ACCOUNT_ID, EconomySettings.defaultCurrency, normalized)
                val currentSupply = totalPlayerBalance.add(getReserveBalance())
                maxSupply = maxSupply.subtract(normalized).coerceAtLeast(currentSupply)
                syncExecutorBalanceLocked()
                dirty.set(true)
            }
            return getReserveBalance()
        }
    }

    fun recordCollectedTax(amount: BigDecimal) {
        synchronized(stateLock) {
            periodTaxCollected = amount.coerceAtLeast(BigDecimal.ZERO)
            dirty.set(true)
        }
    }

    fun settleTaxPeriod(): BigDecimal {
        synchronized(stateLock) {
            val settled = periodTaxCollected
            periodTaxCollected = BigDecimal.ZERO
            dirty.set(true)
            return settled
        }
    }

    private fun initializeInternal() {
        handler = DatabaseUtils.newPlayerDataHandler(TABLE_NAME, syncTick = 200L)
        loadState()
        ensureReserveAccounts()
        refreshMacroState()
        scheduleFlush()
        scheduleMaintenance()
        ready = true
        info("[经济系统] 央行账本已启用，默认货币=${EconomySettings.defaultCurrency}")
    }

    private fun ensureReserveAccounts() {
        synchronized(stateLock) {
            syncExecutorBalanceLocked()
            dirty.set(true)
        }
    }

    private fun scheduleMaintenance() {
        val periodTicks = Duration.ofMinutes(CentralBankSettings.syncIntervalMinutes).toMillis() / 50L
        maintenanceTask = submit(async = true, delay = periodTicks.coerceAtLeast(20L), period = periodTicks.coerceAtLeast(20L)) {
            runCatching {
                refreshMacroState()
                persistState(force = false)
            }.onFailure { ex ->
                warning("[经济系统] 央行巡检失败: ${ex.message}")
            }
        }
    }

    private fun scheduleFlush() {
        flushTask.cancelTaskSafely()
        flushTask = submit(async = true, period = EconomySettings.autoSaveTicks) {
            persistState(force = false)
        }
    }

    private fun refreshMacroState() {
        val currency = EconomySettings.defaultCurrency
        val rawBalances = EconomyStorageService.snapshotBalances(currency)
        val lastSeenSnapshot = EconomyStorageService.snapshotLastSeenAt(rawBalances.keys)

        val now = System.currentTimeMillis()
        val dormantThreshold = now - Duration.ofDays(CentralBankSettings.dormantThresholdDays.toLong()).toMillis()

        // 单次遍历计算所有统计值（优化：避免多次 filter + fold）
        var totalBalance = BigDecimal.ZERO
        var activeBalance = BigDecimal.ZERO
        var activeCount = 0
        var weightedCount = BigDecimal.ZERO
        val eligibleBalances = linkedMapOf<UUID, BigDecimal>()

        for ((accountId, balance) in rawBalances) {
            if (isCentralBankAccount(accountId) || isExemptAccount(accountId)) continue
            eligibleBalances[accountId] = balance
            totalBalance = totalBalance.add(balance)
            val seenAt = lastSeenSnapshot[accountId] ?: 0L
            val inactiveDays = if (seenAt > 0L) ((now - seenAt) / DAY_MILLIS).toInt() else Int.MAX_VALUE
            val weight = resolveInactivityWeight(inactiveDays)
            if (weight >= BigDecimal.ONE) {
                activeCount++
                activeBalance = activeBalance.add(balance)
            }
            weightedCount = weightedCount.add(weight)
        }

        val expectedActive = weightedCount.coerceAtLeast(BigDecimal.ONE)
        val theoreticalSupply = CentralBankSettings.expectedBalance
            .multiply(CentralBankSettings.bufferMultiplier)
            .multiply(expectedActive)
            .setScale(0, RoundingMode.HALF_UP)

        synchronized(stateLock) {
            activePlayerCount = activeCount
            activeM0 = activeBalance
            totalPlayerBalance = totalBalance

            val currentTotalSupply = totalBalance.add(getReserveBalance())
            if (getReserveBalance() <= BigDecimal.ZERO && maxSupply <= BigDecimal.ZERO && theoreticalSupply > currentTotalSupply) {
                val bootstrap = theoreticalSupply.subtract(currentTotalSupply)
                if (bootstrap > BigDecimal.ZERO) {
                    EconomyStorageService.rawDeposit(CENTRAL_BANK_RESERVE_C_ACCOUNT_ID, currency, bootstrap)
                }
                maxSupply = theoreticalSupply
            } else if (theoreticalSupply > currentTotalSupply) {
                val expansion = theoreticalSupply.subtract(currentTotalSupply)
                if (expansion > BigDecimal.ZERO) {
                    EconomyStorageService.rawDeposit(CENTRAL_BANK_RESERVE_C_ACCOUNT_ID, currency, expansion)
                }
                maxSupply = maxSupply.coerceAtLeast(theoreticalSupply)
            } else {
                maxSupply = maxSupply.coerceAtLeast(currentTotalSupply)
            }

            val shouldRecoverDormant = lastDormantRecoveryAt <= 0L || now - lastDormantRecoveryAt >= WEEK_MILLIS
            if (shouldRecoverDormant) {
                recoverDormantBalances(eligibleBalances, lastSeenSnapshot, dormantThreshold)
                lastDormantRecoveryAt = now
            }

            syncExecutorBalanceLocked()
            lastSyncAt = now
            dirty.set(true)
        }
    }

    private fun recoverDormantBalances(
        accountBalances: Map<UUID, BigDecimal>,
        lastSeenSnapshot: Map<UUID, Long>,
        dormantThreshold: Long,
    ) {
        var recovered = BigDecimal.ZERO
        accountBalances.forEach { (accountId, balance) ->
            val seenAt = lastSeenSnapshot[accountId] ?: 0L
            if (seenAt <= 0L || seenAt > dormantThreshold || balance <= BigDecimal.ZERO) {
                return@forEach
            }
            val amount = balance.multiply(CentralBankSettings.dormantRecoveryRate)
                .setScale(0, RoundingMode.HALF_UP)
            if (amount <= BigDecimal.ZERO || !EconomyStorageService.has(accountId, EconomySettings.defaultCurrency, amount)) {
                return@forEach
            }
            EconomyStorageService.rawWithdraw(accountId, EconomySettings.defaultCurrency, amount)
            recovered = recovered.add(amount)
        }
        if (recovered > BigDecimal.ZERO) {
            EconomyStorageService.rawDeposit(CENTRAL_BANK_RESERVE_C_ACCOUNT_ID, EconomySettings.defaultCurrency, recovered)
        }
    }

    private fun resolveInactivityWeight(inactiveDays: Int): BigDecimal {
        if (inactiveDays < CentralBankSettings.activeThresholdDays) return BigDecimal.ONE
        for (entry in CentralBankSettings.inactivityWeights) {
            if (inactiveDays >= entry.days) return entry.weight
        }
        return BigDecimal.ONE
    }

    private fun syncExecutorBalanceLocked() {
        val reserve = getReserveBalance()
        EconomyStorageService.rawSetBalance(CENTRAL_BANK_EXECUTOR_D_ACCOUNT_ID, EconomySettings.defaultCurrency, reserve)
    }

    private fun loadState() {
        val currentHandler = handler ?: return
        val account = STATE_ACCOUNT_ID.toString()
        maxSupply = loadAndCorrect("maxSupply", currentHandler.database[account, KEY_MAX_SUPPLY])
        activeM0 = loadAndCorrect("activeM0", currentHandler.database[account, KEY_ACTIVE_M0])
        activePlayerCount = currentHandler.database[account, KEY_ACTIVE_PLAYER_COUNT]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        totalPlayerBalance = loadAndCorrect("totalPlayerBalance", currentHandler.database[account, KEY_TOTAL_PLAYER_BALANCE])
        periodTaxCollected = loadAndCorrect("periodTaxCollected", currentHandler.database[account, KEY_PERIOD_TAX])
        lastDormantRecoveryAt = currentHandler.database[account, KEY_LAST_DORMANT_RECOVERY_AT]?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        lastSyncAt = currentHandler.database[account, KEY_LAST_SYNC_AT]?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
    }

    private fun loadAndCorrect(label: String, raw: String?): BigDecimal {
        val rawValue = raw?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        return rawValue.setScale(0, RoundingMode.HALF_UP).coerceAtLeast(BigDecimal.ZERO)
    }

    private fun persistState(force: Boolean) {
        if (!force && !dirty.compareAndSet(true, false)) {
            return
        }
        val currentHandler = handler ?: return
        val account = STATE_ACCOUNT_ID.toString()
        runCatching {
            currentHandler.database[account, KEY_MAX_SUPPLY] = maxSupply.toPlainString()
            currentHandler.database[account, KEY_ACTIVE_M0] = activeM0.toPlainString()
            currentHandler.database[account, KEY_ACTIVE_PLAYER_COUNT] = activePlayerCount.toString()
            currentHandler.database[account, KEY_TOTAL_PLAYER_BALANCE] = totalPlayerBalance.toPlainString()
            currentHandler.database[account, KEY_PERIOD_TAX] = periodTaxCollected.toPlainString()
            currentHandler.database[account, KEY_LAST_DORMANT_RECOVERY_AT] = lastDormantRecoveryAt.toString()
            currentHandler.database[account, KEY_LAST_SYNC_AT] = lastSyncAt.toString()
        }.onFailure { ex ->
            dirty.set(true)
            warning("[经济系统] 央行状态持久化失败: ${ex.message}")
        }
    }

}
