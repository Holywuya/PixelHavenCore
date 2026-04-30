package com.pixlehavencore.feature.economy

import com.pixlehavencore.util.DatabaseUtils
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import taboolib.expansion.MultipleHandler
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object EconomyStorageService {

    private const val TABLE_NAME = "economy_accounts"
    private const val KEY_BALANCES = "balances"
    private const val KEY_LAST_SEEN_AT = "last_seen_at"

    private val stateLock = Any()
    private val dirtyAccounts = ConcurrentHashMap.newKeySet<UUID>()
    private val balances = ConcurrentHashMap<UUID, ConcurrentHashMap<String, BigDecimal>>()
    private val lastSeenAt = ConcurrentHashMap<UUID, Long>()
    private val accountLocks = ConcurrentHashMap<UUID, Any>()

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
        if (amount.signum() <= 0) return true
        synchronized(lockFor(accountId)) {
            return getBalance(accountId, currency) >= amount
        }
    }

    fun deposit(accountId: UUID, currency: String, amount: BigDecimal): BigDecimal {
        return rawDeposit(accountId, currency, amount)
    }

    fun rawDeposit(accountId: UUID, currency: String, amount: BigDecimal): BigDecimal {
        synchronized(lockFor(accountId)) {
            val resolved = EconomySettings.resolveCurrency(currency)
            val map = balances.computeIfAbsent(accountId) { ConcurrentHashMap() }
            val normalizedAmount = normalizeAmount(amount)
            val raw = map.merge(resolved, normalizedAmount) { old, new -> old.add(new) } ?: normalizedAmount
            val balance = correctAmount(accountId, resolved, raw)
            map[resolved] = balance
            dirtyAccounts += accountId
            return balance
        }
    }

    fun withdraw(accountId: UUID, currency: String, amount: BigDecimal): BigDecimal {
        return rawWithdraw(accountId, currency, amount)
    }

    fun rawWithdraw(accountId: UUID, currency: String, amount: BigDecimal): BigDecimal {
        synchronized(lockFor(accountId)) {
            val resolved = EconomySettings.resolveCurrency(currency)
            val map = balances.computeIfAbsent(accountId) { ConcurrentHashMap() }
            val normalizedAmount = normalizeAmount(amount)
            val updated = map[resolved]?.subtract(normalizedAmount) ?: BigDecimal.ZERO.subtract(normalizedAmount)
            val raw = if (updated.signum() <= 0) BigDecimal.ZERO else updated
            val balance = correctAmount(accountId, resolved, raw)
            map[resolved] = balance
            dirtyAccounts += accountId
            return balance
        }
    }

    fun rawSetBalance(accountId: UUID, currency: String, amount: BigDecimal): BigDecimal {
        synchronized(lockFor(accountId)) {
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
        invokeCancel(flushTask)
        flushTask = submitAsync(period = EconomySettings.autoSaveTicks) {
            flushAll()
        }
    }

    private fun flushAll() {
        val ids = dirtyAccounts.toList()
        if (ids.isEmpty()) return
        val currentHandler = handler ?: return
        runCatching {
            ids.forEach { accountId ->
                persistAccount(currentHandler, accountId)
                dirtyAccounts.remove(accountId)
            }
        }.onFailure { ex ->
            warning("[经济系统] 保存失败: ${ex.message}")
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

    private fun invokeCancel(task: Any?) {
        if (task == null) return
        runCatching { task.javaClass.methods.firstOrNull { it.name == "cancel" && it.parameterTypes.isEmpty() }?.invoke(task) }
    }

    private fun lockFor(accountId: UUID): Any {
        return accountLocks.computeIfAbsent(accountId) { Any() }
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
            invokeCancel(flushTask)
            flushTask = null
            if (handler != null) {
                flushAll()
            }
            handler = null
            balances.clear()
            lastSeenAt.clear()
            dirtyAccounts.clear()
            accountLocks.clear()
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
