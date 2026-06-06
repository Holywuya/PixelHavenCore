package com.pixlehavencore.playerstate

import com.pixlehavencore.util.DatabaseUtils
import org.bukkit.Bukkit
import org.bukkit.Location
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import taboolib.expansion.MultipleHandler
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

data class PlayerStateData(
    val uuid: UUID,
    var playerName: String = "",
    var firstJoinTime: Long = 0L,
    var lastJoinTime: Long = 0L,
    var lastQuitTime: Long = 0L,
    var joinCount: Int = 0,
    var lastDeathLocation: String = "",
    var lastTeleportLocation: String = ""
)

object PlayerStateStorage {

    private const val TABLE_NAME = "player_meta"
    private const val KEY_PLAYER_NAME = "player_name"
    private const val KEY_FIRST_JOIN = "first_join_time"
    private const val KEY_LAST_JOIN = "last_join_time"
    private const val KEY_LAST_QUIT = "last_quit_time"
    private const val KEY_JOIN_COUNT = "join_count"
    private const val KEY_DEATH_LOC = "last_death_location"
    private const val KEY_TELEPORT_LOC = "last_teleport_location"

    @Volatile
    private var handler: MultipleHandler? = null
    private val shuttingDown = AtomicBoolean(false)

    private val cache = ConcurrentHashMap<UUID, PlayerStateData>()

    @Volatile
    var ready: Boolean = false
        private set

    fun init() {
        shuttingDown.set(false)
        reload()
    }

    fun reload() {
        if (shuttingDown.get()) return
        ready = false
        submitAsync {
            close()
            runCatching {
                handler = DatabaseUtils.newPlayerDataHandler(TABLE_NAME, syncTick = 200L)
            }.onSuccess {
                shuttingDown.set(false)
                ready = true
            }.onFailure { ex ->
                warning("[PlayerState] 初始化 PlayerDatabase 失败: ${ex.message}")
                warning("[PlayerState] 状态数据将无法持久化，请检查数据库配置！")
                close()
                ready = true
            }
        }
    }

    fun close() {
        ready = false
        shuttingDown.set(true)
        flushCache()
        DatabaseUtils.closeMultipleHandler(handler)
        handler = null
        cache.clear()
    }

    fun getOrCreate(uuid: UUID): PlayerStateData {
        return cache.computeIfAbsent(uuid) { PlayerStateData(uuid = uuid) }
    }

    fun get(uuid: UUID): PlayerStateData? = cache[uuid]

    fun loadFromDatabase(uuid: UUID, playerName: String): PlayerStateData? {
        val currentHandler = handler ?: return null
        return runCatching {
            val user = uuid.toString()
            val existingName = (currentHandler.database[user, KEY_PLAYER_NAME] as? String)?.takeIf { it.isNotBlank() }
            val firstJoin = (currentHandler.database[user, KEY_FIRST_JOIN] as? String)?.toLongOrNull() ?: 0L
            val lastJoin = (currentHandler.database[user, KEY_LAST_JOIN] as? String)?.toLongOrNull() ?: 0L
            val lastQuit = (currentHandler.database[user, KEY_LAST_QUIT] as? String)?.toLongOrNull() ?: 0L
            val joinCount = (currentHandler.database[user, KEY_JOIN_COUNT] as? String)?.toIntOrNull() ?: 0
            val deathLoc = (currentHandler.database[user, KEY_DEATH_LOC] as? String)?.takeIf { it.isNotBlank() } ?: ""
            val teleportLoc = (currentHandler.database[user, KEY_TELEPORT_LOC] as? String)?.takeIf { it.isNotBlank() } ?: ""

            val data = PlayerStateData(
                uuid = uuid,
                playerName = existingName ?: playerName,
                firstJoinTime = firstJoin,
                lastJoinTime = lastJoin,
                lastQuitTime = lastQuit,
                joinCount = joinCount,
                lastDeathLocation = deathLoc,
                lastTeleportLocation = teleportLoc
            )
            cache[uuid] = data
            data
        }.getOrElse { ex ->
            warning("[PlayerState] 读取玩家数据失败($playerName): ${ex.message}")
            null
        }
    }

    fun saveImmediate(uuid: UUID) {
        val data = cache[uuid] ?: return
        if (shuttingDown.get()) return
        submitAsync {
            if (shuttingDown.get()) return@submitAsync
            persist(data)
        }
    }

    private fun persist(data: PlayerStateData) {
        val currentHandler = handler ?: return
        runCatching {
            val user = data.uuid.toString()
            currentHandler.database[user, KEY_PLAYER_NAME] = data.playerName
            currentHandler.database[user, KEY_FIRST_JOIN] = data.firstJoinTime.toString()
            currentHandler.database[user, KEY_LAST_JOIN] = data.lastJoinTime.toString()
            currentHandler.database[user, KEY_LAST_QUIT] = data.lastQuitTime.toString()
            currentHandler.database[user, KEY_JOIN_COUNT] = data.joinCount.toString()
            currentHandler.database[user, KEY_DEATH_LOC] = data.lastDeathLocation
            currentHandler.database[user, KEY_TELEPORT_LOC] = data.lastTeleportLocation
        }.onFailure { ex ->
            warning("[PlayerState] 保存玩家数据失败(${data.uuid}): ${ex.message}")
        }
    }

    private fun flushCache() {
        val currentHandler = handler ?: return
        for ((_, data) in cache) {
            runCatching { persist(data) }.onFailure { ex ->
                warning("[PlayerState] flushCache 保存失败(${data.uuid}): ${ex.message}")
            }
        }
    }

    fun deserializeLocation(raw: String): Location? {
        val parts = raw.split(":")
        if (parts.size < 4) return null
        val worldName = parts[0]
        val x = parts[1].toDoubleOrNull() ?: return null
        val y = parts[2].toDoubleOrNull() ?: return null
        val z = parts[3].toDoubleOrNull() ?: return null
        val yaw = parts.getOrElse(4) { "0.0" }.toFloatOrNull() ?: 0f
        val pitch = parts.getOrElse(5) { "0.0" }.toFloatOrNull() ?: 0f
        val world = Bukkit.getWorld(worldName) ?: return null
        return Location(world, x, y, z, yaw, pitch)
    }

    fun serializeLocation(location: Location): String? {
        val worldName = location.world?.name ?: return null
        return "$worldName:${location.x}:${location.y}:${location.z}:${location.yaw}:${location.pitch}"
    }
}
