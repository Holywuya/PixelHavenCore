package com.pixlehavencore.feature.base.back

import com.pixlehavencore.bridge.FoliaCompat
import com.pixlehavencore.bridge.TextBridge
import com.pixlehavencore.util.SafeLocationFinder
import com.pixlehavencore.util.TextUtils
import com.pixlehavencore.util.cancelTaskSafely
import com.pixlehavencore.util.sendMsg
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.warning
import taboolib.platform.util.submit as submitOnEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class BackData(
    val location: Location,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)

class WarmupState(
    val targetLocation: Location,
    val startLocation: Location,
    var remaining: Int,
    var taskRef: Any,
    @Volatile var cancelled: Boolean = false
)

object BackService {

    private val warmups = ConcurrentHashMap<UUID, WarmupState>()

    fun init() {
        BackSettings.init()
        BackStorage.init()
        stop()
    }

    fun reload() {
        stop()
        BackSettings.reload()
        BackStorage.reload()
    }

    fun stop() {
        for ((_, state) in warmups) {
            state.taskRef.cancelTaskSafely()
        }
        warmups.clear()
    }

    fun isEnabled(): Boolean = BackSettings.enabled

    fun handleDeath(player: Player) {
        if (!BackSettings.enabled) return
        record(player.uniqueId, player.location, "death")
        sendDeathButton(player)
    }

    private fun record(player: UUID, location: Location, reason: String) {
        val data = BackData(location = location.clone(), reason = reason)
        BackStorage.set(player, data)
    }

    private fun sendDeathButton(player: Player) {
        val button = TextUtils.parse(BackSettings.msgDeathButton)
            .clickEvent(ClickEvent.runCommand("/back"))
            .hoverEvent(HoverEvent.showText(TextUtils.parse(BackSettings.msgDeathHover)))
        TextBridge.sendMessage(player, button)
    }

    fun teleportBack(player: Player): Boolean {
        if (!BackSettings.enabled) {
            player.sendMsg(BackSettings.msgNoLocation)
            return false
        }

        val uuid = player.uniqueId

        if (warmups.containsKey(uuid)) {
            player.sendMsg(BackSettings.msgAlreadyWarmingUp)
            return false
        }

        val data = getBackData(uuid)
        if (data == null) {
            player.sendMsg(BackSettings.msgNoLocation)
            return false
        }

        val targetWorldName = data.location.world?.name ?: run {
            player.sendMsg("&c目标世界不可用。")
            return false
        }

        submit {
            val targetWorld = Bukkit.getWorld(targetWorldName)
            if (targetWorld == null) {
                BackStorage.remove(uuid)
                player.sendMsg("&c目标世界不可用。")
                return@submit
            }
            val targetLoc = Location(targetWorld, data.location.x, data.location.y, data.location.z, data.location.yaw, data.location.pitch)

            val shouldSkipWarmup = player.isOp || player.hasPermission(BackSettings.instantPermission)
            if (shouldSkipWarmup || BackSettings.warmupSeconds <= 0) {
                doTeleportAsync(player, targetLoc, uuid)
            } else {
                startWarmup(player, targetLoc, uuid)
            }
        }

        return true
    }

    fun cancelWarmup(uuid: UUID) {
        warmups[uuid]?.let { it.cancelled = true }
    }

    private fun getBackData(player: UUID): BackData? {
        var data = BackStorage.get(player)
        if (data != null) return data
        data = BackStorage.loadFromDatabase(player)
        if (data != null) {
            BackStorage.set(player, data)
        }
        return data
    }

    private fun startWarmup(player: Player, targetLoc: Location, uuid: UUID) {
        val startLoc = player.location.clone()
        player.sendMsg(
            BackSettings.msgWarmupStarting.replace("{time}", BackSettings.warmupSeconds.toString())
        )

        val warmupState = WarmupState(
            targetLocation = targetLoc,
            startLocation = startLoc,
            remaining = BackSettings.warmupSeconds,
            taskRef = Any()
        )

        val task = player.submitOnEntity(delay = 0L, period = 20L) {
            if (!player.isOnline) {
                warmups.remove(uuid)
                warmupState.taskRef.cancelTaskSafely()
                return@submitOnEntity
            }

            if (warmupState.cancelled) {
                warmups.remove(uuid)
                warmupState.taskRef.cancelTaskSafely()
                player.sendMsg(BackSettings.msgWarmupCancelled)
                return@submitOnEntity
            }

            if (BackSettings.cancelOnMove && warmupState.remaining < BackSettings.warmupSeconds) {
                val currentLoc = player.location
                if (currentLoc.blockX != warmupState.startLocation.blockX ||
                    currentLoc.blockY != warmupState.startLocation.blockY ||
                    currentLoc.blockZ != warmupState.startLocation.blockZ
                ) {
                    warmups.remove(uuid)
                    warmupState.taskRef.cancelTaskSafely()
                    player.sendMsg(BackSettings.msgWarmupCancelled)
                    return@submitOnEntity
                }
            }

            if (warmupState.remaining <= 0) {
                warmups.remove(uuid)
                warmupState.taskRef.cancelTaskSafely()
                doTeleportAsync(player, warmupState.targetLocation, uuid)
                return@submitOnEntity
            }

            TextBridge.sendActionBar(
                player,
                TextUtils.parse(BackSettings.msgWarmupStarting.replace("{time}", warmupState.remaining.toString()))
            )
            warmupState.remaining--
        }

        warmupState.taskRef = task
        warmups[uuid] = warmupState
    }

    private fun doTeleportAsync(player: Player, targetLoc: Location, uuid: UUID) {
        if (!player.isOnline) return

        val chunkFuture = FoliaCompat.getChunkAtAsync(targetLoc)
            .orTimeout(BackSettings.chunkLoadTimeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)

        chunkFuture.thenApply { chunk -> chunk.chunkSnapshot }
            .thenCompose { _ ->
                if (BackSettings.unsafeTeleport) {
                    player.teleportAsync(targetLoc)
                } else {
                    SafeLocationFinder.findSafeLocationNearAsync(targetLoc, BackSettings.chunkLoadTimeoutSeconds)
                        .thenCompose { safeLoc ->
                            if (safeLoc == null) {
                                player.sendMsg("&c未找到安全传送位置。")
                                java.util.concurrent.CompletableFuture.completedFuture(false)
                            } else {
                                player.teleportAsync(safeLoc)
                            }
                        }
                }
            }
            .thenAccept { success ->
                if (success) {
                    BackStorage.remove(uuid)
                    player.sendMsg(BackSettings.msgTeleported)
                    player.playSound(player.location, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f)
                }
            }
            .exceptionally { ex ->
                warning("[Back] 传送流程异常(${player.name}): ${ex.message}")
                player.sendMsg("&c传送失败。")
                null
            }
    }
}
