package com.pixlehavencore.feature.playtime

import com.pixlehavencore.util.cancelTaskSafely
import org.bukkit.entity.Player
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

object PlaytimeService {

    private var dailyResetTask: Any? = null
    private var weeklyResetTask: Any? = null
    private var monthlyResetTask: Any? = null
    private val dailyScheduled = AtomicBoolean(false)
    private val weeklyScheduled = AtomicBoolean(false)
    private val monthlyScheduled = AtomicBoolean(false)

    fun init() {
        scheduleResetTasks()
    }

    fun reload() {
        cancelResetTasks()
        scheduleResetTasks()
    }

    fun stop() {
        cancelResetTasks()
    }

    fun onPlayerJoin(player: Player) {
        val uuid = player.uniqueId
        val name = player.name
        val now = System.currentTimeMillis()
        PlaytimeStorage.preloadPlayer(uuid, name) {
            PlaytimeStorage.startSession(uuid, name, now)
        }
    }

    fun onPlayerQuit(player: Player) {
        val uuid = player.uniqueId
        val now = System.currentTimeMillis()
        PlaytimeStorage.endSession(uuid, now)
    }

    fun queryPlaytime(playerUuid: UUID): PlaytimeData? {
        return PlaytimeStorage.getData(playerUuid)
    }

    fun getCurrentSessionSeconds(playerUuid: UUID): Long {
        return PlaytimeStorage.getSessionDuration(playerUuid)
    }

    fun queryLeaderboard(type: String, limit: Int, callback: (List<LeaderboardEntry>) -> Unit) {
        PlaytimeStorage.queryLeaderboard(type, limit, callback)
    }

    fun cleanupPreview(days: Int): List<Pair<UUID, String>> {
        return PlaytimeStorage.cleanupPreview(days)
    }

    fun cleanupExecute(days: Int, callback: (Int) -> Unit) {
        PlaytimeStorage.cleanupOldData(days, callback)
    }

    fun resetDaily() {
        PlaytimeStorage.resetDailyStats()
    }

    fun resetWeekly() {
        PlaytimeStorage.resetWeeklyStats()
    }

    fun resetMonthly() {
        PlaytimeStorage.resetMonthlyStats()
    }

    private fun scheduleResetTasks() {
        scheduleDailyReset()
        scheduleWeeklyReset()
        scheduleMonthlyReset()
    }

    private fun cancelResetTasks() {
        dailyResetTask.cancelTaskSafely()
        weeklyResetTask.cancelTaskSafely()
        monthlyResetTask.cancelTaskSafely()
        dailyResetTask = null
        weeklyResetTask = null
        monthlyResetTask = null
        dailyScheduled.set(false)
        weeklyScheduled.set(false)
        monthlyScheduled.set(false)
    }

    private fun scheduleDailyReset() {
        if (!dailyScheduled.compareAndSet(false, true)) return
        val time = parseResetTime(PlaytimeSettings.dailyResetTime)
        val delay = calculateDelayToNext(time)
        val safeDelay = maxOf(delay, 1000L)
        dailyResetTask = submitAsync(delay = safeDelay / 50) {
            PlaytimeStorage.resetDailyStats()
            dailyScheduled.set(false)
            scheduleDailyReset()
        }
    }

    private fun scheduleWeeklyReset() {
        if (!weeklyScheduled.compareAndSet(false, true)) return
        val targetDay = when (PlaytimeSettings.weeklyResetDay) {
            1 -> DayOfWeek.MONDAY
            2 -> DayOfWeek.TUESDAY
            3 -> DayOfWeek.WEDNESDAY
            4 -> DayOfWeek.THURSDAY
            5 -> DayOfWeek.FRIDAY
            6 -> DayOfWeek.SATURDAY
            7 -> DayOfWeek.SUNDAY
            else -> DayOfWeek.MONDAY
        }
        val now = LocalDateTime.now()
        var next = now.with(DayOfWeek.from(targetDay)).with(LocalTime.MIDNIGHT)
        if (!next.isAfter(now)) {
            next = next.plusWeeks(1)
        }
        val delay = Duration.between(now, next).toMillis()
        val safeDelay = maxOf(delay, 1000L)
        weeklyResetTask = submitAsync(delay = safeDelay / 50) {
            PlaytimeStorage.resetWeeklyStats()
            weeklyScheduled.set(false)
            scheduleWeeklyReset()
        }
    }

    private fun scheduleMonthlyReset() {
        if (!monthlyScheduled.compareAndSet(false, true)) return
        val targetDay = PlaytimeSettings.monthlyResetDay.coerceIn(1, 28)
        val now = LocalDateTime.now()
        var next = now.withDayOfMonth(targetDay).with(LocalTime.MIDNIGHT)
        if (!next.isAfter(now)) {
            next = next.plusMonths(1)
        }
        val delay = Duration.between(now, next).toMillis()
        val safeDelay = maxOf(delay, 1000L)
        monthlyResetTask = submitAsync(delay = safeDelay / 50) {
            PlaytimeStorage.resetMonthlyStats()
            monthlyScheduled.set(false)
            scheduleMonthlyReset()
        }
    }

    private fun parseResetTime(timeStr: String): LocalTime {
        val parts = timeStr.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    }

    private fun calculateDelayToNext(target: LocalTime): Long {
        val now = LocalDateTime.now()
        var next = now.with(target)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        return Duration.between(now, next).toMillis()
    }

}
