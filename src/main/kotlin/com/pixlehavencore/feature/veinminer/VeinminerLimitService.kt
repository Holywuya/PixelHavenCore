package com.pixlehavencore.feature.veinminer

import com.pixlehavencore.util.ensureDataContainer
import taboolib.common.platform.ProxyPlayer
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.onlinePlayers
import taboolib.expansion.getDataContainer
import taboolib.expansion.setupPlayerDatabase
import com.pixlehavencore.PixleHavenSettings
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

object VeinminerLimitService {

    private const val KEY_USED = "veinminer_used"
    private const val KEY_RESET_AT = "veinminer_reset_at"

    @Volatile
    private var nextResetAt: LocalDateTime? = null

    @Volatile
    private var lastFixedMarker: Long = 0L

    fun init() {
        initDatabase()
        updateResetSchedule()
        scheduleFixedReset()
    }

    fun updateResetSchedule() {
        nextResetAt = computeNextReset(LocalDateTime.now())
    }

    fun getRemaining(player: ProxyPlayer): Int {
        resetIfNeeded(player)
        val used = getUsage(player)
        val limit = getLimitValue(player)
        return (limit - used).coerceAtLeast(0)
    }

    fun getResetSeconds(player: ProxyPlayer): Long {
        if (!VeinminerSettings.limitEnabled) {
            return 0L
        }
        val target = nextResetAt ?: computeNextReset(LocalDateTime.now())
        val now = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val reset = target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return ((reset - now) / 1000L).coerceAtLeast(0L)
    }

    fun consume(player: ProxyPlayer, amount: Int): Boolean {
        if (!VeinminerSettings.limitEnabled) {
            return true
        }
        val limit = getLimitValue(player)
        if (limit <= 0) {
            return amount < 0
        }
        resetIfNeeded(player)
        val used = getUsage(player)
        if (amount < 0) {
            val next = (used + amount).coerceAtLeast(0)
            setUsage(player, next)
            return true
        }
        if (used + amount > limit) {
            return false
        }
        setUsage(player, used + amount)
        return true
    }

    fun getLimitValue(player: ProxyPlayer): Int {
        return resolveGroup(player)?.limit ?: 0
    }

    fun getPricePerBlock(player: ProxyPlayer): Double {
        return resolveGroup(player)?.pricePerBlock ?: 0.0
    }

    private fun resolveGroup(player: ProxyPlayer): VeinminerGroup? {
        if (VeinminerSettings.groups.isEmpty()) {
            return null
        }
        return VeinminerSettings.groups.firstOrNull { group ->
            group.permission.isEmpty() || player.hasPermission(group.permission)
        }
    }

    private fun resetIfNeeded(player: ProxyPlayer) {
        if (!VeinminerSettings.limitEnabled) {
            return
        }
        val marker = currentFixedMarker(LocalDateTime.now())
        val last = getResetAt(player)
        if (last < marker) {
            setUsage(player, 0, marker)
        }
    }

    private fun computeNextReset(now: LocalDateTime): LocalDateTime {
        val todayTarget = now.toLocalDate().atTime(VeinminerSettings.limitResetHour, VeinminerSettings.limitResetMinute)
        return if (now.isBefore(todayTarget)) {
            todayTarget
        } else {
            todayTarget.plusDays(1)
        }
    }

    private fun currentFixedMarker(now: LocalDateTime): Long {
        val todayTarget = now.toLocalDate().atTime(VeinminerSettings.limitResetHour, VeinminerSettings.limitResetMinute)
        val markerTime = if (now.isBefore(todayTarget)) todayTarget.minusDays(1) else todayTarget
        return markerTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun initDatabase() {
        if (PixleHavenSettings.databaseType == "mysql") {
            setupPlayerDatabase(
                host = PixleHavenSettings.mysqlHost,
                port = PixleHavenSettings.mysqlPort.toIntOrNull() ?: 3306,
                user = PixleHavenSettings.mysqlUser,
                password = PixleHavenSettings.mysqlPassword,
                database = PixleHavenSettings.mysqlDatabase,
                table = "veinminer_data"
            )
        } else {
            setupPlayerDatabase(File(getDataFolder(), PixleHavenSettings.sqliteFile), "veinminer_data")
        }
    }

    private fun ensureContainer(player: ProxyPlayer) = player.ensureDataContainer()

    private fun getUsage(player: ProxyPlayer): Int {
        ensureContainer(player)
        val container = player.getDataContainer()
        return container[KEY_USED]?.toIntOrNull() ?: 0
    }

    private fun setUsage(player: ProxyPlayer, count: Int, resetAtOverride: Long? = null) {
        ensureContainer(player)
        val container = player.getDataContainer()
        container[KEY_USED] = count
        val resetAt = resetAtOverride ?: currentFixedMarker(LocalDateTime.now())
        container[KEY_RESET_AT] = resetAt
    }

    private fun getResetAt(player: ProxyPlayer): Long {
        ensureContainer(player)
        val container = player.getDataContainer()
        return container[KEY_RESET_AT]?.toLongOrNull() ?: 0L
    }

    private fun scheduleFixedReset() {
        lastFixedMarker = currentFixedMarker(LocalDateTime.now())
        taboolib.common.platform.function.submit(period = 20 * 60) {
            val marker = currentFixedMarker(LocalDateTime.now())
            if (marker != lastFixedMarker) {
                lastFixedMarker = marker
                onlinePlayers().forEach { player ->
                    setUsage(player, 0, marker)
                }
            }
        }
    }
}
