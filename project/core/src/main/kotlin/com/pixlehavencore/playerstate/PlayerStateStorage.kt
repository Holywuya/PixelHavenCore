package com.pixlehavencore.playerstate

import com.pixlehavencore.util.DataStore
import org.bukkit.Bukkit
import org.bukkit.Location
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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

    private val store = DataStore(TABLE_NAME)

    private val cache = ConcurrentHashMap<UUID, PlayerStateData>()

    @Volatile
    var ready: Boolean = false
        private set

    fun init() {
        ready = false
        store.init { success -> ready = true }
    }

    fun reload() {
        ready = false
        store.reload { success -> ready = true }
    }

    fun close() {
        ready = false
        store.close { flushCache() }
        cache.clear()
    }

    fun getOrCreate(uuid: UUID): PlayerStateData {
        return cache.computeIfAbsent(uuid) { PlayerStateData(uuid = uuid) }
    }

    fun get(uuid: UUID): PlayerStateData? = cache[uuid]

    fun loadFromDatabase(uuid: UUID, playerName: String): PlayerStateData? {
        return runCatching {
            val user = uuid.toString()
            val existingName = store.get(user, KEY_PLAYER_NAME)?.takeIf { it.isNotBlank() }
            val firstJoin = store.get(user, KEY_FIRST_JOIN)?.toLongOrNull() ?: 0L
            val lastJoin = store.get(user, KEY_LAST_JOIN)?.toLongOrNull() ?: 0L
            val lastQuit = store.get(user, KEY_LAST_QUIT)?.toLongOrNull() ?: 0L
            val joinCount = store.get(user, KEY_JOIN_COUNT)?.toIntOrNull() ?: 0
            val deathLoc = store.get(user, KEY_DEATH_LOC)?.takeIf { it.isNotBlank() } ?: ""
            val teleportLoc = store.get(user, KEY_TELEPORT_LOC)?.takeIf { it.isNotBlank() } ?: ""

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
        if (store.isShuttingDown()) return
        submitAsync {
            if (store.isShuttingDown()) return@submitAsync
            persist(data)
        }
    }

    private fun persist(data: PlayerStateData) {
        runCatching {
            val user = data.uuid.toString()
            store.set(user, KEY_PLAYER_NAME, data.playerName)
            store.set(user, KEY_FIRST_JOIN, data.firstJoinTime.toString())
            store.set(user, KEY_LAST_JOIN, data.lastJoinTime.toString())
            store.set(user, KEY_LAST_QUIT, data.lastQuitTime.toString())
            store.set(user, KEY_JOIN_COUNT, data.joinCount.toString())
            store.set(user, KEY_DEATH_LOC, data.lastDeathLocation)
            store.set(user, KEY_TELEPORT_LOC, data.lastTeleportLocation)
        }.onFailure { ex ->
            warning("[PlayerState] 保存玩家数据失败(${data.uuid}): ${ex.message}")
        }
    }

    private fun flushCache() {
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
