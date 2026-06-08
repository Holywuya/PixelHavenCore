package com.pixlehavencore.feature.deathdrop

import com.pixlehavencore.util.DataStore
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object DeathDropUsageStorage {

    private const val TABLE_NAME = "death_drop_usage"
    private const val KEY_USED_PREFIX = "used:"
    private const val KEY_BONUS_PREFIX = "bonus:"

    private val store = DataStore(TABLE_NAME)

    private val cache = ConcurrentHashMap<String, UsageRecord>()

    class UsageRecord(used: Int = 0, bonus: Int = 0) {
        val used: java.util.concurrent.atomic.AtomicInteger = java.util.concurrent.atomic.AtomicInteger(used)
        val bonus: java.util.concurrent.atomic.AtomicInteger = java.util.concurrent.atomic.AtomicInteger(bonus)
    }

    fun init() {
        store.init()
    }

    fun reload() {
        store.reload()
    }

    fun close() {
        store.close()
        cache.clear()
    }

    fun getUsedToday(player: UUID): Int {
        return getRecord(player, todayKey()).used.get()
    }

    fun consumeKeep(player: UUID): Int {
        val dateKey = todayKey()
        val record = getRecord(player, dateKey)
        val newUsed = record.used.incrementAndGet()
        saveRecordAsync(player, dateKey, record)
        return newUsed
    }

    fun getBonusToday(player: UUID): Int {
        return getRecord(player, todayKey()).bonus.get()
    }

    fun addBonusToday(player: UUID, amount: Int): Int {
        val dateKey = todayKey()
        val record = getRecord(player, dateKey)
        val newBonus = record.bonus.addAndGet(amount)
        saveRecordAsync(player, dateKey, record)
        return newBonus
    }

    fun setBonusToday(player: UUID, amount: Int): Int {
        val dateKey = todayKey()
        val record = getRecord(player, dateKey)
        record.bonus.set(amount)
        saveRecordAsync(player, dateKey, record)
        return amount
    }

    private fun getRecord(player: UUID, dateKey: String): UsageRecord {
        val cacheKey = cacheKey(player, dateKey)
        cache[cacheKey]?.let { return it }
        val newRecord = UsageRecord()
        val actual = cache.putIfAbsent(cacheKey, newRecord)
        if (actual != null) return actual
        submitAsync {
            val loaded = loadRecord(player, dateKey)
            val existing = cache[cacheKey] ?: return@submitAsync
            existing.used.set(loaded.used.get())
            existing.bonus.set(loaded.bonus.get())
        }
        return newRecord
    }

    private fun loadRecord(player: UUID, dateKey: String): UsageRecord {
        return runCatching {
            val user = player.toString()
            val used = store.get(user, KEY_USED_PREFIX + dateKey)?.toIntOrNull() ?: 0
            val bonus = store.get(user, KEY_BONUS_PREFIX + dateKey)?.toIntOrNull() ?: 0
            UsageRecord(used = used, bonus = bonus)
        }.getOrElse { ex ->
            warning("[DeathDropUsage] 读取玩家数据失败($player): ${ex.message}")
            UsageRecord()
        }
    }

    private fun saveRecordAsync(player: UUID, dateKey: String, record: UsageRecord) {
        if (store.isShuttingDown()) {
            saveRecordSync(player, dateKey, record)
            return
        }
        submitAsync {
            if (store.isShuttingDown()) return@submitAsync
            runCatching {
                saveRecordSync(player, dateKey, record)
            }.onFailure { ex ->
                warning("[DeathDropUsage] 保存玩家数据失败($player): ${ex.message}")
            }
        }
    }

    private fun saveRecordSync(player: UUID, dateKey: String, record: UsageRecord) {
        val user = player.toString()
        store.set(user, KEY_USED_PREFIX + dateKey, record.used.get().toString())
        store.set(user, KEY_BONUS_PREFIX + dateKey, record.bonus.get().toString())
    }

    private fun cacheKey(player: UUID, dateKey: String): String {
        return "${player}|$dateKey"
    }

    private fun todayKey(): String {
        return java.time.LocalDate.now().toString()
    }
}
