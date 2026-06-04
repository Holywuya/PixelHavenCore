package com.pixlehavencore.feature.playtime

import com.pixlehavencore.util.DatabaseUtils
import com.pixlehavencore.util.PerKeyLock
import com.pixlehavencore.util.cancelTaskSafely
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import taboolib.expansion.MultipleHandler
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class PlaytimeData(
    val playerUuid: UUID,
    val playerName: String,
    val totalSeconds: Long,
    val todaySeconds: Long,
    val weekSeconds: Long,
    val monthSeconds: Long,
    val lastLoginTime: Long,
    val lastLogoutTime: Long,
    val updatedAt: Long
)

data class LeaderboardEntry(
    val rank: Int,
    val playerName: String,
    val playtimeSeconds: Long,
    val playtimeFormatted: String
)

object PlaytimeStorage {

    private const val TABLE_NAME = "playtime_data"
    private const val KEY_TOTAL = "total_seconds"
    private const val KEY_TODAY = "today_seconds"
    private const val KEY_WEEK = "week_seconds"
    private const val KEY_MONTH = "month_seconds"
    private const val KEY_LAST_LOGIN = "last_login"
    private const val KEY_LAST_LOGOUT = "last_logout"
    private const val KEY_PLAYER_NAME = "player_name"

    private val stateLock = Any()
    private val dataCache = ConcurrentHashMap<UUID, PlaytimeData>()
    private val sessionCache = ConcurrentHashMap<UUID, Long>()
    private val dirtyPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val dataLocks = PerKeyLock<UUID>()

    @Volatile
    private var handler: MultipleHandler? = null
    private var flushTask: Any? = null
    @Volatile
    private var ready: Boolean = false

    fun init() {
        reload()
    }

    fun reload() {
        submitAsync {
            reloadInternal()
        }
    }

    fun stop() {
        shutdownInternal()
    }

    fun isReady(): Boolean = ready

    fun getData(playerUuid: UUID): PlaytimeData? = dataCache[playerUuid]

    fun getSessionStart(playerUuid: UUID): Long? = sessionCache[playerUuid]

    fun getSessionDuration(playerUuid: UUID, currentTime: Long = System.currentTimeMillis()): Long {
        val start = sessionCache[playerUuid] ?: return 0L
        return ((currentTime - start) / 1000).coerceAtLeast(0L)
    }

    fun startSession(playerUuid: UUID, playerName: String, loginTime: Long) {
        synchronized(dataLocks[playerUuid]) {
            val existing = dataCache[playerUuid]
            val updated = (existing ?: PlaytimeData(
                playerUuid = playerUuid,
                playerName = playerName,
                totalSeconds = 0L,
                todaySeconds = 0L,
                weekSeconds = 0L,
                monthSeconds = 0L,
                lastLoginTime = loginTime,
                lastLogoutTime = 0L,
                updatedAt = loginTime
            )).copy(
                playerName = playerName,
                lastLoginTime = loginTime,
                updatedAt = loginTime
            )
            dataCache[playerUuid] = updated
            sessionCache[playerUuid] = loginTime
            dirtyPlayers += playerUuid
        }
    }

    fun endSession(playerUuid: UUID, logoutTime: Long) {
        synchronized(dataLocks[playerUuid]) {
            val start = sessionCache.remove(playerUuid) ?: return
            val sessionSeconds = ((logoutTime - start) / 1000).coerceAtLeast(0L)
            val existing = dataCache[playerUuid] ?: return
            val updated = existing.copy(
                totalSeconds = existing.totalSeconds + sessionSeconds,
                todaySeconds = existing.todaySeconds + sessionSeconds,
                weekSeconds = existing.weekSeconds + sessionSeconds,
                monthSeconds = existing.monthSeconds + sessionSeconds,
                lastLogoutTime = logoutTime,
                updatedAt = logoutTime
            )
            dataCache[playerUuid] = updated
            dirtyPlayers += playerUuid
        }
    }

    fun preloadPlayer(playerUuid: UUID, playerName: String, callback: () -> Unit = {}) {
        if (dataCache.containsKey(playerUuid)) {
            callback()
            return
        }
        submitAsync {
            loadPlayerData(playerUuid, playerName)
            submit { callback() }
        }
    }

    fun getAllData(): Map<UUID, PlaytimeData> = dataCache.toMap()

    fun resetDailyStats() {
        submitAsync {
            val keys = dataCache.keys.toList()
            keys.forEach { uuid ->
                synchronized(dataLocks[uuid]) {
                    val existing = dataCache[uuid] ?: return@forEach
                    dataCache[uuid] = existing.copy(todaySeconds = 0L, updatedAt = System.currentTimeMillis())
                    dirtyPlayers += uuid
                }
            }
            info("[在线时长] 已重置所有玩家的今日统计。")
        }
    }

    fun resetWeeklyStats() {
        submitAsync {
            val keys = dataCache.keys.toList()
            keys.forEach { uuid ->
                synchronized(dataLocks[uuid]) {
                    val existing = dataCache[uuid] ?: return@forEach
                    dataCache[uuid] = existing.copy(weekSeconds = 0L, updatedAt = System.currentTimeMillis())
                    dirtyPlayers += uuid
                }
            }
            info("[在线时长] 已重置所有玩家的本周统计。")
        }
    }

    fun resetMonthlyStats() {
        submitAsync {
            val keys = dataCache.keys.toList()
            keys.forEach { uuid ->
                synchronized(dataLocks[uuid]) {
                    val existing = dataCache[uuid] ?: return@forEach
                    dataCache[uuid] = existing.copy(monthSeconds = 0L, updatedAt = System.currentTimeMillis())
                    dirtyPlayers += uuid
                }
            }
            info("[在线时长] 已重置所有玩家的本月统计。")
        }
    }

    fun queryLeaderboard(type: String, limit: Int, callback: (List<LeaderboardEntry>) -> Unit) {
        submitAsync {
            val maxLimit = PlaytimeSettings.leaderboardMaxLimit
            val effectiveLimit = limit.coerceIn(1, maxLimit)
            val entries = dataCache.values
                .sortedByDescending { data ->
                    when (type.lowercase()) {
                        "today" -> data.todaySeconds
                        "week" -> data.weekSeconds
                        "month" -> data.monthSeconds
                        else -> data.totalSeconds
                    }
                }
                .take(effectiveLimit)
                .mapIndexed { index, data ->
                    val seconds = when (type.lowercase()) {
                        "today" -> data.todaySeconds
                        "week" -> data.weekSeconds
                        "month" -> data.monthSeconds
                        else -> data.totalSeconds
                    }
                    LeaderboardEntry(
                        rank = index + 1,
                        playerName = data.playerName,
                        playtimeSeconds = seconds,
                        playtimeFormatted = PlaytimeSettings.formatSeconds(seconds)
                    )
                }
            submit { callback(entries) }
        }
    }

    fun cleanupOldData(days: Int, callback: (Int) -> Unit) {
        submitAsync {
            val threshold = System.currentTimeMillis() - days.toLong() * 86400 * 1000
            val toRemove = dataCache.entries
                .filter { it.value.lastLogoutTime > 0 && it.value.lastLogoutTime < threshold }
                .map { it.key }
            var removed = 0
            val currentHandler = handler
            if (currentHandler != null) {
                toRemove.forEach { uuid ->
                    synchronized(dataLocks[uuid]) {
                        dataCache.remove(uuid)
                        sessionCache.remove(uuid)
                        dirtyPlayers.remove(uuid)
                    }
                    dataLocks.remove(uuid)
                    runCatching {
                        val user = uuid.toString()
                        currentHandler.database[user, KEY_TOTAL] = ""
                        currentHandler.database[user, KEY_TODAY] = ""
                        currentHandler.database[user, KEY_WEEK] = ""
                        currentHandler.database[user, KEY_MONTH] = ""
                        currentHandler.database[user, KEY_LAST_LOGIN] = ""
                        currentHandler.database[user, KEY_LAST_LOGOUT] = ""
                        currentHandler.database[user, KEY_PLAYER_NAME] = ""
                    }
                    removed++
                }
            }
            info("[在线时长] 清理完成，共删除 $removed 条数据。")
            submit { callback(removed) }
        }
    }

    fun cleanupPreview(days: Int): List<Pair<UUID, String>> {
        val threshold = System.currentTimeMillis() - days.toLong() * 86400 * 1000
        return dataCache.entries
            .filter { it.value.lastLogoutTime > 0 && it.value.lastLogoutTime < threshold }
            .map { it.key to it.value.playerName }
    }

    private fun loadPlayerData(playerUuid: UUID, playerName: String) {
        val currentHandler = handler ?: return
        val user = playerUuid.toString()
        runCatching {
            val total = (currentHandler.database[user, KEY_TOTAL] as? String)?.toLongOrNull() ?: 0L
            val today = (currentHandler.database[user, KEY_TODAY] as? String)?.toLongOrNull() ?: 0L
            val week = (currentHandler.database[user, KEY_WEEK] as? String)?.toLongOrNull() ?: 0L
            val month = (currentHandler.database[user, KEY_MONTH] as? String)?.toLongOrNull() ?: 0L
            val lastLogin = (currentHandler.database[user, KEY_LAST_LOGIN] as? String)?.toLongOrNull() ?: 0L
            val lastLogout = (currentHandler.database[user, KEY_LAST_LOGOUT] as? String)?.toLongOrNull() ?: 0L
            val name = (currentHandler.database[user, KEY_PLAYER_NAME] as? String)?.takeIf { it.isNotBlank() } ?: playerName
            synchronized(dataLocks[playerUuid]) {
                dataCache[playerUuid] = PlaytimeData(
                    playerUuid = playerUuid,
                    playerName = name,
                    totalSeconds = total,
                    todaySeconds = today,
                    weekSeconds = week,
                    monthSeconds = month,
                    lastLoginTime = lastLogin,
                    lastLogoutTime = lastLogout,
                    updatedAt = System.currentTimeMillis()
                )
            }
        }.onFailure { ex ->
            warning("[在线时长] 加载玩家 $playerName 数据失败: ${ex.message}")
            warning("[在线时长] 玩家数据将无法持久化，请检查数据库配置！")
            // 不创建默认数据，避免覆盖玩家已有的数据
        }
    }

    private fun persistPlayer(currentHandler: MultipleHandler, playerUuid: UUID) {
        val data = dataCache[playerUuid] ?: return
        val user = playerUuid.toString()
        currentHandler.database[user, KEY_TOTAL] = data.totalSeconds.toString()
        currentHandler.database[user, KEY_TODAY] = data.todaySeconds.toString()
        currentHandler.database[user, KEY_WEEK] = data.weekSeconds.toString()
        currentHandler.database[user, KEY_MONTH] = data.monthSeconds.toString()
        currentHandler.database[user, KEY_LAST_LOGIN] = data.lastLoginTime.toString()
        currentHandler.database[user, KEY_LAST_LOGOUT] = data.lastLogoutTime.toString()
        currentHandler.database[user, KEY_PLAYER_NAME] = data.playerName
    }

    private fun loadAll() {
        val currentHandler = handler ?: return
        runCatching {
            val totalMap = currentHandler.database.getListByKey(KEY_TOTAL)
            val todayMap = currentHandler.database.getListByKey(KEY_TODAY)
            val weekMap = currentHandler.database.getListByKey(KEY_WEEK)
            val monthMap = currentHandler.database.getListByKey(KEY_MONTH)
            val loginMap = currentHandler.database.getListByKey(KEY_LAST_LOGIN)
            val logoutMap = currentHandler.database.getListByKey(KEY_LAST_LOGOUT)
            val nameMap = currentHandler.database.getListByKey(KEY_PLAYER_NAME)
            val allUsers = totalMap.keys + todayMap.keys + weekMap.keys +
                monthMap.keys + loginMap.keys + logoutMap.keys + nameMap.keys
            allUsers.forEach { user ->
                val uuid = runCatching { UUID.fromString(user) }.getOrNull() ?: return@forEach
                dataCache[uuid] = PlaytimeData(
                    playerUuid = uuid,
                    playerName = (nameMap[user] as? String)?.takeIf { it.isNotBlank() } ?: user.takeLast(8),
                    totalSeconds = (totalMap[user] as? String)?.toLongOrNull() ?: 0L,
                    todaySeconds = (todayMap[user] as? String)?.toLongOrNull() ?: 0L,
                    weekSeconds = (weekMap[user] as? String)?.toLongOrNull() ?: 0L,
                    monthSeconds = (monthMap[user] as? String)?.toLongOrNull() ?: 0L,
                    lastLoginTime = (loginMap[user] as? String)?.toLongOrNull() ?: 0L,
                    lastLogoutTime = (logoutMap[user] as? String)?.toLongOrNull() ?: 0L,
                    updatedAt = System.currentTimeMillis()
                )
            }
            info("[在线时长] 已加载 ${dataCache.size} 条玩家数据。")
        }.onFailure { ex ->
            warning("[在线时长] 批量加载数据失败: ${ex.message}")
        }
    }

    private fun accumulateAndFlushAll() {
        val ids = dirtyPlayers.toList()
        if (ids.isEmpty()) return
        val currentHandler = handler ?: return
        val now = System.currentTimeMillis()
        ids.forEach { uuid ->
            val sessionStart = sessionCache[uuid] ?: return@forEach
            synchronized(dataLocks[uuid]) {
                val delta = ((now - sessionStart) / 1000).coerceAtLeast(0L)
                if (delta > 0) {
                    val existing = dataCache[uuid] ?: return@forEach
                    dataCache[uuid] = existing.copy(
                        totalSeconds = existing.totalSeconds + delta,
                        todaySeconds = existing.todaySeconds + delta,
                        weekSeconds = existing.weekSeconds + delta,
                        monthSeconds = existing.monthSeconds + delta,
                        updatedAt = now
                    )
                    sessionCache[uuid] = now
                }
            }
        }
        runCatching {
            ids.forEach { uuid ->
                persistPlayer(currentHandler, uuid)
                // 只有在成功保存后才移除脏标记
                dirtyPlayers.remove(uuid)
            }
        }.onFailure { ex ->
            warning("[在线时长] 保存数据失败: ${ex.message}")
        }
    }

    private fun flushAll() {
        val ids = dirtyPlayers.toList()
        if (ids.isEmpty()) return
        val currentHandler = handler ?: return
        runCatching {
            ids.forEach { uuid ->
                persistPlayer(currentHandler, uuid)
                dirtyPlayers.remove(uuid)
            }
        }.onFailure { ex ->
            warning("[在线时长] 保存数据失败: ${ex.message}")
        }
    }

    private fun scheduleFlush() {
        flushTask.cancelTaskSafely()
        flushTask = submitAsync(period = PlaytimeSettings.autoSaveTicks) {
            accumulateAndFlushAll()
        }
    }

    private fun reloadInternal() {
        synchronized(stateLock) {
            shutdownInternal()
            if (!PlaytimeSettings.enabled) return
            runCatching {
                handler = DatabaseUtils.newPlayerDataHandler(TABLE_NAME, syncTick = 200L)
                loadAll()
                scheduleFlush()
                ready = true
            }.onFailure { ex ->
                warning("[在线时长] 初始化失败: ${ex.message}")
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
            dataCache.clear()
            sessionCache.clear()
            dirtyPlayers.clear()
            dataLocks.clear()
            ready = false
        }
    }

}
