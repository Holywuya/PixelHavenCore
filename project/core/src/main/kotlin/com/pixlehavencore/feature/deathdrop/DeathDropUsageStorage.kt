package com.pixlehavencore.feature.deathdrop

import com.pixlehavencore.util.DatabaseUtils
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import taboolib.expansion.MultipleHandler
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object DeathDropUsageStorage {

    private const val TABLE_NAME = "death_drop_usage"
    private const val KEY_USED_PREFIX = "used:"
    private const val KEY_BONUS_PREFIX = "bonus:"

    @Volatile
    private var handler: MultipleHandler? = null
    private val shuttingDown = AtomicBoolean(false)

    private val cache = ConcurrentHashMap<String, UsageRecord>()

    class UsageRecord(used: Int = 0, bonus: Int = 0) {
        val used: java.util.concurrent.atomic.AtomicInteger = java.util.concurrent.atomic.AtomicInteger(used)
        val bonus: java.util.concurrent.atomic.AtomicInteger = java.util.concurrent.atomic.AtomicInteger(bonus)
    }

    fun init() {
        shuttingDown.set(false)
        reload()
    }

    fun reload() {
        if (shuttingDown.get()) {
            return
        }
        submitAsync {
            close()
            if (!DeathDropSettings.enabled) {
                return@submitAsync
            }
            runCatching {
                handler = DatabaseUtils.newPlayerDataHandler(TABLE_NAME, syncTick = 200L)
            }.onFailure { ex ->
                warning("[DeathDropUsage] 初始化 PlayerDatabase 失败: ${ex.message}")
                close()
            }
        }
    }

    fun close() {
        shuttingDown.set(true)
        DatabaseUtils.closeMultipleHandler(handler)
        handler = null
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
        // Folia: 缓存未命中时返回默认值（0次使用/0奖励），异步预热缓存，
        // 避免在 PlayerDeathEvent 实体线程上同步读数据库
        val newRecord = UsageRecord()
        val actual = cache.putIfAbsent(cacheKey, newRecord)
        if (actual != null) return actual
        submitAsync {
            val loaded = loadRecord(player, dateKey)
            cache[cacheKey] = loaded
        }
        return newRecord
    }

    private fun loadRecord(player: UUID, dateKey: String): UsageRecord {
        val currentHandler = handler ?: return UsageRecord()
        return runCatching {
            val user = player.toString()
            val used = currentHandler.database[user, KEY_USED_PREFIX + dateKey]?.toIntOrNull() ?: 0
            val bonus = currentHandler.database[user, KEY_BONUS_PREFIX + dateKey]?.toIntOrNull() ?: 0
            UsageRecord(used = used, bonus = bonus)
        }.getOrElse { ex ->
            warning("[DeathDropUsage] 读取玩家数据失败($player): ${ex.message}")
            UsageRecord()
        }
    }

    private fun saveRecordAsync(player: UUID, dateKey: String, record: UsageRecord) {
        val currentHandler = handler ?: return
        if (shuttingDown.get()) {
            saveRecordSync(currentHandler, player, dateKey, record)
            return
        }
        submitAsync {
            if (shuttingDown.get()) return@submitAsync
            runCatching {
                saveRecordSync(currentHandler, player, dateKey, record)
            }.onFailure { ex ->
                warning("[DeathDropUsage] 保存玩家数据失败($player): ${ex.message}")
            }
        }
    }

    private fun saveRecordSync(currentHandler: MultipleHandler, player: UUID, dateKey: String, record: UsageRecord) {
        val user = player.toString()
        currentHandler.database[user, KEY_USED_PREFIX + dateKey] = record.used.get().toString()
        currentHandler.database[user, KEY_BONUS_PREFIX + dateKey] = record.bonus.get().toString()
    }

    private fun cacheKey(player: UUID, dateKey: String): String {
        return "${player}|$dateKey"
    }

    private fun todayKey(): String {
        return java.time.LocalDate.now().toString()
    }
}
