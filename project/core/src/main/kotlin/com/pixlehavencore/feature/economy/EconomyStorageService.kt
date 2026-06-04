package com.pixlehavencore.feature.economy

import com.pixlehavencore.util.DatabaseUtils
import com.pixlehavencore.util.PerKeyLock
import com.pixlehavencore.util.cancelTaskSafely
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import taboolib.expansion.MultipleHandler
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object EconomyStorageService {

    fun interface BalanceChangeListener {
        fun onBalanceChange(accountId: UUID, delta: BigDecimal, exempt: Boolean)
    }

    @Volatile
    private var balanceChangeListener: BalanceChangeListener? = null

    fun setBalanceChangeListener(listener: BalanceChangeListener?) {
        balanceChangeListener = listener
    }

    private const val TABLE_NAME = "economy_accounts"
    private const val KEY_BALANCES = "balances"
    private const val KEY_LAST_SEEN_AT = "last_seen_at"

    private val stateLock = Any()
    private val dirtyAccounts = ConcurrentHashMap.newKeySet<UUID>()
    private val balances = ConcurrentHashMap<UUID, ConcurrentHashMap<String, BigDecimal>>()
    private val lastSeenAt = ConcurrentHashMap<UUID, Long>()
    private val accountLocks = PerKeyLock<UUID>()

    @Volatile
    private var handler: MultipleHandler? = null
    private var flushTask: Any? = null
    @Volatile
    private var ready: Boolean = false
    private val shuttingDown = AtomicBoolean(false)

    fun init() {
        shuttingDown.set(false)
        reload()
    }

    fun reload() {
        submitAsync {
            reloadInternal()
        }
    }

    fun stop() {
        shuttingDown.set(true)
        shutdownInternal()
    }

    fun getBalance(accountId: UUID, currency: String): BigDecimal {
        if (!ready) return BigDecimal.ZERO
        val resolved = EconomySettings.resolveCurrency(currency)
        return balances[accountId]?.get(resolved) ?: BigDecimal.ZERO
    }

    fun getLastSeenAt(accountId: UUID): Long {
        return lastSeenAt[accountId] ?: 0L
    }

    fun markSeen(accountId: UUID, seenAt: Long = System.currentTimeMillis()) {
        lastSeenAt.compute(accountId) { _, current ->
            val previous = current ?: 0L
            maxOf(previous, seenAt)
        }
        dirtyAccounts += accountId
    }

    fun snapshotBalances(currency: String): Map<UUID, BigDecimal> {
        val resolved = EconomySettings.resolveCurrency(currency)
        return (balances.keys + lastSeenAt.keys).associateWith { accountId ->
            balances[accountId]?.get(resolved) ?: BigDecimal.ZERO
        }
    }

    fun snapshotLastSeenAt(accountIds: Collection<UUID>): Map<UUID, Long> {
        return accountIds.associateWith { getLastSeenAt(it) }
    }

    fun has(accountId: UUID, currency: String, amount: BigDecimal): Boolean {
        if (!ready) return false
        if (amount.signum() <= 0) return true
        synchronized(accountLocks[accountId]) {
            return getBalance(accountId, currency) >= amount
        }
    }

    fun deposit(accountId: UUID, currency: String, amount: BigDecimal): BigDecimal {
        return rawDeposit(accountId, currency, amount)
    }

    fun rawDeposit(accountId: UUID, currency: String, amount: BigDecimal, exempt: Boolean = false): BigDecimal {
        val result: BigDecimal
        var notifyDelta = BigDecimal.ZERO
        var shouldNotify = false
        synchronized(accountLocks[accountId]) {
            val resolved = EconomySettings.resolveCurrency(currency)
            val map = balances.computeIfAbsent(accountId) { ConcurrentHashMap() }
            val normalizedAmount = normalizeAmount(amount)
            val before = map[resolved] ?: BigDecimal.ZERO
            val raw = map.merge(resolved, normalizedAmount) { old, new -> old.add(new) } ?: normalizedAmount
            val balance = correctAmount(accountId, resolved, raw)
            map[resolved] = balance
            dirtyAccounts += accountId
            result = balance
            if (!exempt && resolved == EconomySettings.defaultCurrency && balanceChangeListener != null) {
                notifyDelta = balance.subtract(before)
                shouldNotify = notifyDelta != BigDecimal.ZERO
            }
        }
        if (shouldNotify) {
            balanceChangeListener?.onBalanceChange(accountId, notifyDelta, false)
        }
        return result
    }

    fun withdraw(accountId: UUID, currency: String, amount: BigDecimal): BigDecimal {
        return rawWithdraw(accountId, currency, amount)
    }

    /**
     * 原子化的尝试扣款操作，在同一个锁内完成余额检查和扣款
     * @return 扣款后的余额，如果余额不足返回 null
     */
    fun tryWithdraw(accountId: UUID, currency: String, amount: BigDecimal): BigDecimal? {
        val result: BigDecimal
        var notifyDelta = BigDecimal.ZERO
        var shouldNotify = false
        synchronized(accountLocks[accountId]) {
            val resolved = EconomySettings.resolveCurrency(currency)
            val map = balances.computeIfAbsent(accountId) { ConcurrentHashMap() }
            val normalizedAmount = normalizeAmount(amount)
            val before = map[resolved] ?: BigDecimal.ZERO
            // 在锁内检查余额是否充足
            if (before < normalizedAmount) {
                return null
            }
            val updated = before.subtract(normalizedAmount)
            val raw = if (updated.signum() <= 0) BigDecimal.ZERO else updated
            val balance = correctAmount(accountId, resolved, raw)
            map[resolved] = balance
            dirtyAccounts += accountId
            result = balance
            if (resolved == EconomySettings.defaultCurrency && balanceChangeListener != null) {
                notifyDelta = balance.subtract(before)
                shouldNotify = notifyDelta != BigDecimal.ZERO
            }
        }
        if (shouldNotify) {
            balanceChangeListener?.onBalanceChange(accountId, notifyDelta, false)
        }
        return result
    }

    fun rawWithdraw(accountId: UUID, currency: String, amount: BigDecimal, exempt: Boolean = false): BigDecimal {
        val result: BigDecimal
        var notifyDelta = BigDecimal.ZERO
        var shouldNotify = false
        synchronized(accountLocks[accountId]) {
            val resolved = EconomySettings.resolveCurrency(currency)
            val map = balances.computeIfAbsent(accountId) { ConcurrentHashMap() }
            val normalizedAmount = normalizeAmount(amount)
            val before = map[resolved] ?: BigDecimal.ZERO
            val updated = before.subtract(normalizedAmount)
            val raw = if (updated.signum() <= 0) BigDecimal.ZERO else updated
            val balance = correctAmount(accountId, resolved, raw)
            map[resolved] = balance
            dirtyAccounts += accountId
            result = balance
            if (!exempt && resolved == EconomySettings.defaultCurrency && balanceChangeListener != null) {
                notifyDelta = balance.subtract(before)
                shouldNotify = notifyDelta != BigDecimal.ZERO
            }
        }
        if (shouldNotify) {
            balanceChangeListener?.onBalanceChange(accountId, notifyDelta, false)
        }
        return result
    }

    fun rawSetBalance(accountId: UUID, currency: String, amount: BigDecimal): BigDecimal {
        synchronized(accountLocks[accountId]) {
            val resolved = EconomySettings.resolveCurrency(currency)
            val normalized = normalizeAmount(amount)
            val balance = correctAmount(accountId, resolved, normalized)
            val map = balances.computeIfAbsent(accountId) { ConcurrentHashMap() }
            map[resolved] = balance
            dirtyAccounts += accountId
            return balance
        }
    }

    fun close() {
        stop()
    }

    private fun scheduleFlush() {
        if (shuttingDown.get()) {
            return
        }
        flushTask.cancelTaskSafely()
        flushTask = submitAsync(period = EconomySettings.autoSaveTicks) {
            flushAll()
        }
    }

    private fun flushAll() {
        val ids = dirtyAccounts.toList()
        if (ids.isEmpty()) return
        val currentHandler = handler ?: return
        var failedCount = 0
        ids.forEach { accountId ->
            runCatching {
                persistAccount(currentHandler, accountId)
                dirtyAccounts.remove(accountId)
            }.onFailure { ex ->
                failedCount++
                warning("[经济系统] 保存账户 $accountId 失败: ${ex.message}")
            }
        }
        if (failedCount > 0) {
            warning("[经济系统] 本次保存完成，$failedCount 个账户保存失败")
        }
    }

    private fun persistAccount(currentHandler: MultipleHandler, accountId: UUID) {
        val map = balances[accountId]
        val user = accountId.toString()
        val payload = map?.entries
            ?.sortedBy { it.key }
            ?.joinToString(";") { (currency, amount) -> "$currency=${amount.toPlainString()}" }
            ?: ""
        currentHandler.database[user, KEY_BALANCES] = payload
        currentHandler.database[user, KEY_LAST_SEEN_AT] = getLastSeenAt(accountId).toString()
    }

    private fun loadAll() {
        val currentHandler = handler ?: return
        runCatching {
            val rows = currentHandler.database.getListByKey(KEY_BALANCES)
            rows.forEach { (user, payload) ->
                val accountId = runCatching { UUID.fromString(user) }.getOrNull() ?: return@forEach
                val parsed = parseBalances(payload)
                if (parsed.isEmpty()) {
                    return@forEach
                }
                val map = balances.computeIfAbsent(accountId) { ConcurrentHashMap() }
                parsed.forEach { (currency, amount) ->
                    map[EconomySettings.resolveCurrency(currency)] = amount
                }
            }

            val currentTime = System.currentTimeMillis()
            val seenRows = currentHandler.database.getListByKey(KEY_LAST_SEEN_AT)
            seenRows.forEach { (user, payload) ->
                val accountId = runCatching { UUID.fromString(user) }.getOrNull() ?: return@forEach
                val seenAt = payload.toLongOrNull()?.coerceAtLeast(0L) ?: return@forEach
                lastSeenAt[accountId] = seenAt
            }
            balances.keys.forEach { accountId ->
                if (lastSeenAt.putIfAbsent(accountId, currentTime) == null) {
                    dirtyAccounts += accountId
                }
            }
        }.onFailure { ex ->
            warning("[经济系统] 读取存储失败: ${ex.message}")
        }
    }

    private fun reloadInternal() {
        synchronized(stateLock) {
            if (shuttingDown.get()) {
                return
            }
            shutdownInternal()
            if (!EconomySettings.enabled) {
                return
            }
            runCatching {
                handler = DatabaseUtils.newPlayerDataHandler(TABLE_NAME, syncTick = 200L)
                loadAll()
                scheduleFlush()
                ready = true
            }.onFailure { ex ->
                warning("[经济系统] 初始化失败: ${ex.message}")
                shutdownInternal()
            }
        }
    }

    private fun shutdownInternal() {
        synchronized(stateLock) {
            flushTask.cancelTaskSafely()
            flushTask = null
            if (handler != null) {
                flushAll()
            }
            DatabaseUtils.closeMultipleHandler(handler)
            handler = null
            balances.clear()
            lastSeenAt.clear()
            dirtyAccounts.clear()
            accountLocks.clear()
            balanceChangeListener = null
            ready = false
        }
    }

    private fun parseBalances(payload: String): Map<String, BigDecimal> {
        if (payload.isBlank()) {
            return emptyMap()
        }
        val result = linkedMapOf<String, BigDecimal>()
        payload.split(';').forEach { entry ->
            val trimmed = entry.trim()
            if (trimmed.isBlank()) {
                return@forEach
            }
            val idx = trimmed.indexOf('=')
            if (idx <= 0 || idx >= trimmed.length - 1) {
                return@forEach
            }
            val currency = trimmed.substring(0, idx).trim().lowercase()
            val rawAmount = trimmed.substring(idx + 1).trim().toBigDecimalOrNull() ?: return@forEach
            result[currency] = normalizeAmount(rawAmount)
        }
        return result
    }

    private fun normalizeAmount(value: BigDecimal): BigDecimal {
        return value.setScale(0, RoundingMode.HALF_UP).coerceAtLeast(BigDecimal.ZERO)
    }

    private fun correctAmount(accountId: UUID, currency: String, amount: BigDecimal): BigDecimal {
        return if (amount.scale() > 0) normalizeAmount(amount) else amount
    }
}
