package com.pixlehavencore.feature.flight

import com.pixlehavencore.bridge.TextBridge
import com.pixlehavencore.util.ADMIN_PERMISSION
import com.pixlehavencore.util.PlaceholderUtils.resolvePlaceholders
import com.pixlehavencore.util.TextUtils
import com.pixlehavencore.util.cancelTaskSafely
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.entity.Player
import taboolib.common.platform.function.info
import taboolib.common.platform.function.onlinePlayers
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.platform.util.submit as submitOnEntity
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import taboolib.platform.util.PlayerSessionMap
import java.util.UUID

object FlightService {

    private val playerData = PlayerSessionMap<FlightPlayerData>({ FlightPlayerData(remainingSeconds = 0) })
    private var tickTask: Any? = null
    private var resetTask: Any? = null

    fun init() {
        FlightSettings.init()
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
        playerData.clear()
    }

    private fun stopTasks() {
        tickTask.cancelTaskSafely()
        resetTask.cancelTaskSafely()
        tickTask = null
        resetTask = null
    }

    fun isEnabled(): Boolean = FlightSettings.enabled

    /**
     * 绕过检查：创造模式、OP、phcore.admin 权限
     */
    fun isBypass(player: Player): Boolean {
        return player.gameMode == GameMode.CREATIVE ||
            player.isOp ||
            player.hasPermission(ADMIN_PERMISSION)
    }

    // ========== 玩家生命周期 ==========

    fun handlePlayerJoin(player: Player) {
        if (!FlightSettings.enabled) return
        if (isBypass(player)) {
            playerData[player.uniqueId] = FlightPlayerData(remainingSeconds = -1)
            player.submitOnEntity { player.allowFlight = true }
            return
        }
        val dailySeconds = resolveDailySeconds(player)
        playerData[player.uniqueId] = FlightPlayerData(remainingSeconds = dailySeconds)
        if (dailySeconds > 0 && isWorldEnabled(player.world.name)) {
            player.submitOnEntity { player.allowFlight = true }
        }
    }

    fun handlePlayerQuit(player: Player) {
        player.isFlying = false
        player.allowFlight = false
        // playerData 由 PlayerSessionMap 自动清理，无需手动 remove
    }

    fun handleWorldChange(player: Player) {
        if (!FlightSettings.enabled) return
        if (isBypass(player)) {
            val uuid = player.uniqueId
            if (playerData[uuid] == null) {
                playerData[uuid] = FlightPlayerData(remainingSeconds = -1)
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

    fun enableFlight(player: Player) {
        val uuid = player.uniqueId
        if (isBypass(player)) {
            playerData[uuid] = FlightPlayerData(remainingSeconds = -1, manualDisable = false)
            player.submitOnEntity {
                player.allowFlight = true
                player.isFlying = true
            }
            return
        }
        val data = playerData[uuid] ?: return
        if (!isWorldEnabled(player.world.name)) return
        if (data.effectiveSeconds <= 0) return
        playerData[uuid] = data.copy(manualDisable = false)
        player.submitOnEntity {
            player.allowFlight = true
            player.isFlying = true
        }
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
    }

    fun toggleFlight(player: Player): Boolean {
        val data = playerData[player.uniqueId] ?: return false
        return if (player.isFlying || !data.manualDisable) {
            disableFlight(player)
            false
        } else {
            enableFlight(player)
            true
        }
    }

    // ========== 管理员操作 ==========

    fun setRemainingSeconds(player: Player, seconds: Int) {
        val uuid = player.uniqueId
        val currentData = playerData[uuid]
        val newData = if (currentData != null) {
            currentData.copy(remainingSeconds = seconds.coerceAtLeast(0))
        } else {
            FlightPlayerData(remainingSeconds = seconds.coerceAtLeast(0))
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

    fun addBonusSeconds(player: Player, seconds: Int) {
        val uuid = player.uniqueId
        val data = playerData[uuid] ?: FlightPlayerData(remainingSeconds = 0)
        val newData = data.copy(bonusSeconds = (data.bonusSeconds + seconds).coerceAtLeast(0), manualDisable = false)
        playerData[uuid] = newData
        if (newData.effectiveSeconds > 0 && isWorldEnabled(player.world.name)) {
            player.submitOnEntity {
                player.allowFlight = true
                player.isFlying = true
            }
        }
    }

    fun resetPlayer(player: Player) {
        val uuid = player.uniqueId
        val newDaily = resolveDailySeconds(player)
        playerData[uuid] = FlightPlayerData(remainingSeconds = newDaily)
        if (newDaily > 0 && isWorldEnabled(player.world.name)) {
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
        if (totalSeconds == Int.MAX_VALUE) return "∞"
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

                // Folia: 将玩家状态读取和处理都移到实体线程
                player.submitOnEntity {
                    // 旁观模式跳过
                    if (player.gameMode == GameMode.SPECTATOR) {
                        return@submitOnEntity
                    }

                    // 绕过玩家：不消耗、不限制、不显示
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
                        }
                        return@submitOnEntity
                    }

                    if (player.isFlying) {
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
        if (data.bonusSeconds > 0) {
            return data.copy(bonusSeconds = data.bonusSeconds - 1)
        }
        return data.copy(remainingSeconds = (data.remainingSeconds - 1).coerceAtLeast(0))
    }

    private fun sendFlightActionBar(player: Player, data: FlightPlayerData) {
        if (FlightSettings.displayMode != "actionbar") return
        val timeStr = formatTime(data.effectiveSeconds)
        val message = FlightSettings.msgActionBar.resolvePlaceholders("{time}" to timeStr)
        TextBridge.sendActionBar(player, TextUtils.parse(message))
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
        for ((uuid, _) in playerData.entries()) {
            val player = Bukkit.getPlayer(uuid) ?: continue
            if (isBypass(player)) {
                playerData[uuid] = FlightPlayerData(remainingSeconds = -1)
                player.submitOnEntity { player.allowFlight = true }
                continue
            }
            val newDaily = resolveDailySeconds(player)
            playerData[uuid] = FlightPlayerData(remainingSeconds = newDaily)
            if (newDaily > 0 && isWorldEnabled(player.world.name)) {
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

    private fun calculateDelayToNext(target: LocalTime): Long {
        val now = LocalDateTime.now()
        var next = now.with(target)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        return Duration.between(now, next).toMillis()
    }
}
