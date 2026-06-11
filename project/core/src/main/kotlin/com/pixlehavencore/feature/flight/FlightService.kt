package com.pixlehavencore.feature.flight

import com.pixlehavencore.bridge.TextBridge
import com.pixlehavencore.util.ADMIN_PERMISSION
import com.pixlehavencore.util.DominionBridge
import com.pixlehavencore.util.PlaceholderUtils.resolvePlaceholders
import com.pixlehavencore.util.TextUtils
import com.pixlehavencore.util.cancelTaskSafely
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.entity.Player
import taboolib.common.platform.function.info
import taboolib.common.platform.function.onlinePlayers
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.platform.util.submit as submitOnEntity
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import taboolib.platform.util.PlayerSessionMap
import java.util.UUID

object FlightService {

    private val playerData = PlayerSessionMap<FlightPlayerData>({ FlightPlayerData(baseSeconds = 0) })
    private var tickTask: Any? = null
    private var resetTask: Any? = null

    fun init() {
        FlightSettings.init()
        FlightBonusStorage.init()
        stop()
        if (!FlightSettings.enabled) return
        startTickTask()
        scheduleDailyReset()
    }

    fun reload() {
        stopTasks()
        FlightSettings.reload()
        if (!FlightSettings.enabled) return
        startTickTask()
        scheduleDailyReset()
    }

    fun stop() {
        stopTasks()
        FlightBonusStorage.close()
        playerData.clear()
    }

    private fun stopTasks() {
        tickTask.cancelTaskSafely()
        resetTask.cancelTaskSafely()
        tickTask = null
        resetTask = null
    }

    fun isEnabled(): Boolean = FlightSettings.enabled

    fun isBypass(player: Player): Boolean {
        return player.gameMode == GameMode.CREATIVE ||
            player.isOp ||
            player.hasPermission(ADMIN_PERMISSION)
    }

    // ========== 玩家生命周期 ==========

    fun handlePlayerJoin(player: Player) {
        if (!FlightSettings.enabled) return
        if (isBypass(player)) {
            playerData[player.uniqueId] = FlightPlayerData(baseSeconds = -1)
            player.submitOnEntity { player.allowFlight = true }
            return
        }
        val dailySeconds = resolveDailySeconds(player)
        val permanentBonus = FlightBonusStorage.loadBonus(player.uniqueId)
        val savedBase = FlightBonusStorage.loadBaseSeconds(player.uniqueId)
        val savedDay = FlightBonusStorage.loadDay(player.uniqueId)
        val currentDay = effectiveDay()
        val baseSeconds = if (savedBase != null && savedDay == currentDay) {
            savedBase
        } else {
            dailySeconds
        }
        playerData[player.uniqueId] = FlightPlayerData(baseSeconds = baseSeconds, permanentBonus = permanentBonus)
        if (baseSeconds + permanentBonus > 0 && isWorldEnabled(player.world.name)) {
            player.submitOnEntity { player.allowFlight = true }
        }
    }

    fun handlePlayerQuit(player: Player) {
        val data = playerData[player.uniqueId]
        if (data != null) {
            FlightBonusStorage.saveBonus(player.uniqueId, data.permanentBonus)
            FlightBonusStorage.saveBaseInfo(player.uniqueId, data.baseSeconds, effectiveDay())
        }
        player.isFlying = false
        player.allowFlight = false
    }

    fun handleWorldChange(player: Player) {
        if (!FlightSettings.enabled) return
        if (isBypass(player)) {
            val uuid = player.uniqueId
            if (playerData[uuid] == null) {
                playerData[uuid] = FlightPlayerData(baseSeconds = -1)
            }
            player.submitOnEntity { player.allowFlight = true }
            return
        }
        val uuid = player.uniqueId
        val data = playerData[uuid] ?: return
        if (!isWorldEnabled(player.world.name)) {
            player.submitOnEntity {
                player.isFlying = false
                player.allowFlight = false
            }
            playerData[uuid] = data.copy(manualDisable = false)
        } else if (data.effectiveSeconds > 0 && !data.manualDisable) {
            player.submitOnEntity { player.allowFlight = true }
        }
    }

    fun handleRespawn(player: Player) {
        handleWorldChange(player)
    }

    // ========== 飞行控制 ==========

    fun enableFlight(player: Player): Boolean {
        val uuid = player.uniqueId
        if (isBypass(player)) {
            playerData[uuid] = FlightPlayerData(baseSeconds = -1, manualDisable = false)
            player.submitOnEntity {
                player.allowFlight = true
                player.isFlying = true
            }
            return true
        }
        val data = playerData[uuid] ?: return false
        if (!isWorldEnabled(player.world.name)) return false
        if (data.effectiveSeconds <= 0) {
            player.sendMessage(TextUtils.parse(FlightSettings.msgNoTime))
            return false
        }
        if (DominionBridge.isAvailable() && !DominionBridge.canFlyAt(player, player.location)) {
            player.sendMessage(TextUtils.parse(FlightSettings.msgDominionBlocked))
            return false
        }
        playerData[uuid] = data.copy(manualDisable = false)
        player.submitOnEntity {
            player.allowFlight = true
            player.isFlying = true
        }
        sendFlightActionBar(player, playerData[uuid] ?: data)
        return true
    }

    fun disableFlight(player: Player) {
        val uuid = player.uniqueId
        val data = playerData[uuid]
        if (data != null) {
            playerData[uuid] = data.copy(manualDisable = true)
        }
        player.submitOnEntity {
            player.isFlying = false
            player.allowFlight = false
        }
        clearActionBar(player)
    }

    fun toggleFlight(player: Player): Boolean {
        if (playerData[player.uniqueId] == null) return false
        if (player.isFlying) {
            player.submitOnEntity { player.isFlying = false }
            return false
        }
        return enableFlight(player)
    }

    // ========== 管理员操作 ==========

    fun setBaseSeconds(player: Player, seconds: Int) {
        val uuid = player.uniqueId
        val currentData = playerData[uuid]
        val newData = if (currentData != null) {
            currentData.copy(baseSeconds = seconds.coerceAtLeast(0))
        } else {
            FlightPlayerData(baseSeconds = seconds.coerceAtLeast(0))
        }
        playerData[uuid] = newData
        if (seconds > 0 && isWorldEnabled(player.world.name)) {
            player.submitOnEntity {
                player.allowFlight = true
                player.isFlying = true
            }
        } else {
            player.submitOnEntity {
                player.isFlying = false
                player.allowFlight = false
            }
        }
    }

    fun addPermanentBonus(player: Player, seconds: Int) {
        val uuid = player.uniqueId
        val data = playerData[uuid] ?: FlightPlayerData(baseSeconds = 0)
        val newBonus = (data.permanentBonus + seconds).coerceAtLeast(0)
        val newData = data.copy(permanentBonus = newBonus, manualDisable = false)
        playerData[uuid] = newData
        FlightBonusStorage.saveBonus(uuid, newBonus)
        if (newData.effectiveSeconds > 0 && isWorldEnabled(player.world.name)) {
            player.submitOnEntity {
                player.allowFlight = true
                player.isFlying = true
            }
        }
    }

    fun resetPlayer(player: Player) {
        val uuid = player.uniqueId
        val currentData = playerData[uuid]
        val newDaily = resolveDailySeconds(player)
        val permanentBonus = currentData?.permanentBonus ?: 0
        playerData[uuid] = FlightPlayerData(baseSeconds = newDaily, permanentBonus = permanentBonus)
        if (newDaily + permanentBonus > 0 && isWorldEnabled(player.world.name)) {
            player.submitOnEntity {
                player.allowFlight = true
                player.isFlying = true
            }
        } else {
            player.submitOnEntity {
                player.isFlying = false
                player.allowFlight = false
            }
        }
    }

    fun getPlayerData(uuid: UUID): FlightPlayerData? = playerData[uuid]

    // ========== 分组解析 ==========

    fun resolveDailySeconds(player: Player): Int {
        for (group in FlightSettings.groups) {
            if (group.permission.isEmpty() || player.hasPermission(group.permission)) {
                return group.dailySeconds
            }
        }
        return 0
    }

    // ========== 时间格式化 ==========

    fun formatTime(totalSeconds: Int): String {
        if (totalSeconds == Int.MAX_VALUE) return "\u221e"
        if (totalSeconds <= 0) return "00:00"
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%d:%02d", m, s)
    }

    // ========== 内部：计时器 ==========

    private fun startTickTask() {
        tickTask = submit(delay = 20L, period = 20L) {
            if (!FlightSettings.enabled) return@submit
            for (proxy in onlinePlayers()) {
                val player = proxy.cast<Player>() ?: continue
                val uuid = player.uniqueId
                val data = playerData[uuid] ?: continue

                player.submitOnEntity {
                    if (player.gameMode == GameMode.SPECTATOR) {
                        return@submitOnEntity
                    }

                    if (isBypass(player)) {
                        if (!player.allowFlight) {
                            player.allowFlight = true
                        }
                        return@submitOnEntity
                    }

                    if (!isWorldEnabled(player.world.name)) {
                        if (player.allowFlight) {
                            player.isFlying = false
                            player.allowFlight = false
                            playerData[uuid] = data.copy(manualDisable = false)
                            clearActionBar(player)
                        }
                        return@submitOnEntity
                    }

                    if (player.isFlying) {
                        if (DominionBridge.isAvailable() && !DominionBridge.canFlyAt(player, player.location)) {
                            player.isFlying = false
                            clearActionBar(player)
                            if (FlightSettings.msgDominionBlocked.isNotBlank()) {
                                TextBridge.sendActionBar(player, TextUtils.parse(FlightSettings.msgDominionBlocked))
                            }
                            return@submitOnEntity
                        }
                        if (data.isUnlimited) {
                            sendFlightActionBar(player, data)
                            return@submitOnEntity
                        }
                        if (data.effectiveSeconds <= 0) {
                            player.isFlying = false
                            player.allowFlight = false
                            playerData[uuid] = data.copy(manualDisable = false)
                            if (FlightSettings.msgTimeExpired.isNotBlank()) {
                                TextBridge.sendActionBar(player, TextUtils.parse(FlightSettings.msgTimeExpired))
                            } else {
                                clearActionBar(player)
                            }
                            return@submitOnEntity
                        }
                        val newData = decrementTime(data)
                        playerData[uuid] = newData
                        sendFlightActionBar(player, newData)
                    }
                }
            }
        }
    }

    private fun decrementTime(data: FlightPlayerData): FlightPlayerData {
        if (data.baseSeconds > 0) {
            return data.copy(baseSeconds = data.baseSeconds - 1)
        }
        return data.copy(permanentBonus = (data.permanentBonus - 1).coerceAtLeast(0))
    }

    private fun sendFlightActionBar(player: Player, data: FlightPlayerData) {
        if (FlightSettings.displayMode != "actionbar") return
        val timeStr = formatTime(data.effectiveSeconds)
        val message = FlightSettings.msgActionBar.resolvePlaceholders("{time}" to timeStr)
        TextBridge.sendActionBar(player, TextUtils.parse(message))
    }

    private fun clearActionBar(player: Player) {
        player.sendActionBar(Component.empty())
    }

    private fun isWorldEnabled(worldName: String): Boolean {
        val worlds = FlightSettings.enabledWorlds
        return worlds.isEmpty() || worldName in worlds
    }

    // ========== 内部：每日重置 ==========

    private fun scheduleDailyReset() {
        val time = parseResetTime(FlightSettings.dailyResetTime)
        val delay = calculateDelayToNext(time)
        val period = 24 * 60 * 60 * 1000L
        resetTask = submitAsync(delay = delay / 50, period = period / 50) {
            info("[飞行] 执行每日飞行时间重置...")
            performDailyReset()
        }
    }

    private fun performDailyReset() {
        val currentDay = effectiveDay()
        for ((uuid, _) in playerData.entries()) {
            val player = Bukkit.getPlayer(uuid) ?: continue
            if (isBypass(player)) {
                playerData[uuid] = FlightPlayerData(baseSeconds = -1)
                player.submitOnEntity { player.allowFlight = true }
                continue
            }
            val newDaily = resolveDailySeconds(player)
            val oldData = playerData[uuid]
            val permanentBonus = oldData?.permanentBonus ?: 0
            playerData[uuid] = FlightPlayerData(baseSeconds = newDaily, permanentBonus = permanentBonus)
            FlightBonusStorage.saveBaseInfo(uuid, newDaily, currentDay)
            if (newDaily + permanentBonus > 0 && isWorldEnabled(player.world.name)) {
                player.submitOnEntity { player.allowFlight = true }
                if (FlightSettings.msgDailyReset.isNotBlank()) {
                    player.submitOnEntity {
                        player.sendMessage(TextUtils.parse(FlightSettings.msgDailyReset))
                    }
                }
            } else {
                player.submitOnEntity {
                    player.isFlying = false
                    player.allowFlight = false
                }
            }
        }
    }

    private fun parseResetTime(timeStr: String): LocalTime {
        val parts = timeStr.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    }

    private fun effectiveDay(): Long {
        val resetTime = parseResetTime(FlightSettings.dailyResetTime)
        val now = LocalTime.now()
        return if (now >= resetTime) LocalDate.now().toEpochDay()
        else LocalDate.now().minusDays(1).toEpochDay()
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
