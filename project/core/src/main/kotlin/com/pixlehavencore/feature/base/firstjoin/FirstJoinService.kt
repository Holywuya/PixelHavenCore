package com.pixlehavencore.feature.base.firstjoin

import com.pixlehavencore.util.DatabaseUtils
import com.pixlehavencore.util.SafeLocationFinder
import com.pixlehavencore.util.TextUtils
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import taboolib.expansion.MultipleHandler
import java.util.UUID

object FirstJoinService {

    private const val TABLE_NAME = "title_player_data"
    private const val KEY_ACTIVE = "active_title"
    private const val KEY_OWNED = "owned_titles"

    @Volatile
    private var titleHandler: MultipleHandler? = null

    @Volatile
    private var ready: Boolean = false

    fun init() {
        FirstJoinSettings.init()
        if (!FirstJoinSettings.enabled) return
        ready = false
        submitAsync {
            runCatching {
                titleHandler = DatabaseUtils.newPlayerDataHandler(TABLE_NAME, syncTick = 200L)
                ready = true
            }.onFailure { ex ->
                warning("[FirstJoin] 连接 title_player_data 失败: ${ex.message}")
                warning("[FirstJoin] 将回退到 hasPlayedBefore() 判定")
                ready = true
            }
        }
    }

    fun reload() {
        stop()
        FirstJoinSettings.reload()
        if (FirstJoinSettings.enabled) init()
    }

    fun stop() {
        ready = false
        DatabaseUtils.closeMultipleHandler(titleHandler)
        titleHandler = null
    }

    fun handleJoin(player: Player) {
        if (!FirstJoinSettings.enabled) return
        if (!ready) return

        val uuid = player.uniqueId
        val world = Bukkit.getWorld("world") ?: Bukkit.getWorlds().firstOrNull() ?: return

        submitAsync {
            if (!isFirstJoin(uuid)) return@submitAsync

            val spawn = world.spawnLocation
            val centerX = spawn.x + FirstJoinSettings.centerX
            val centerZ = spawn.z + FirstJoinSettings.centerZ
            val maxAttempts = 5 * FirstJoinSettings.safeLocationRetries

            SafeLocationFinder.findSafeLocationAsync(
                world, centerX, centerZ,
                FirstJoinSettings.minRadius, FirstJoinSettings.maxRadius,
                maxAttempts, FirstJoinSettings.chunkLoadTimeoutSeconds
            ).thenAccept { safeLoc ->
                if (!player.isOnline) return@thenAccept
                if (safeLoc == null) {
                    warning("[FirstJoin] 未找到安全随机位置(${player.name})")
                    return@thenAccept
                }
                player.teleportAsync(safeLoc).thenAccept { success ->
                    if (success) {
                        val msg = FirstJoinSettings.msgTeleported
                            .replace("{x}", safeLoc.blockX.toString())
                            .replace("{y}", safeLoc.blockY.toString())
                            .replace("{z}", safeLoc.blockZ.toString())
                        player.sendMessage(TextUtils.parse(msg))
                    }
                }
            }.exceptionally { ex ->
                warning("[FirstJoin] 随机传送流程异常(${player.name}): ${ex.message}")
                null
            }
        }
    }

    private fun isFirstJoin(uuid: UUID): Boolean {
        val currentHandler = titleHandler
        if (currentHandler != null) {
            return runCatching {
                val user = uuid.toString()
                val activeTitle = (currentHandler.database[user, KEY_ACTIVE] as? String)?.takeIf { it.isNotBlank() }
                val ownedJson = (currentHandler.database[user, KEY_OWNED] as? String)?.takeIf { it.isNotBlank() }
                activeTitle == null && ownedJson == null
            }.getOrDefault(false)
        }
        return !Bukkit.getOfflinePlayer(uuid).hasPlayedBefore()
    }

    fun isEnabled(): Boolean = FirstJoinSettings.enabled
}
