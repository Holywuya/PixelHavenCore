package com.pixlehavencore.feature.base.back

import com.pixlehavencore.util.DataStore
import org.bukkit.Bukkit
import org.bukkit.Location
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object BackStorage {

    private const val TABLE_NAME = "back_location"
    private const val KEY_LOCATION = "location"

    private val store = DataStore(TABLE_NAME)

    private val cache = ConcurrentHashMap<UUID, BackData>()

    fun init() {
        store.init()
    }

    fun reload() {
        store.reload()
    }

    fun close() {
        store.close { flushCache() }
    }

    fun get(player: UUID): BackData? {
        return cache[player]
    }

    fun set(player: UUID, data: BackData) {
        cache[player] = data
        submitAsync {
            if (store.isShuttingDown()) return@submitAsync
            saveToDatabase(player, data)
        }
    }

    fun remove(player: UUID) {
        cache.remove(player)
        submitAsync {
            if (store.isShuttingDown()) return@submitAsync
            runCatching {
                store.set(player.toString(), KEY_LOCATION, "")
            }.onFailure { ex ->
                warning("[Back] 删除玩家数据失败($player): ${ex.message}")
            }
        }
    }

    fun loadFromDatabase(player: UUID): BackData? {
        return runCatching {
            val raw = store.get(player.toString(), KEY_LOCATION) ?: return null
            deserialize(raw)
        }.getOrElse { ex ->
            warning("[Back] 读取玩家数据失败($player): ${ex.message}")
            null
        }
    }

    private fun saveToDatabase(player: UUID, data: BackData) {
        val serialized = serialize(data.location) ?: return
        runCatching {
            store.set(player.toString(), KEY_LOCATION, serialized)
        }.onFailure { ex ->
            warning("[Back] 保存玩家数据失败($player): ${ex.message}")
        }
    }

    private fun flushCache() {
        for ((player, data) in cache) {
            val serialized = serialize(data.location) ?: continue
            runCatching {
                store.set(player.toString(), KEY_LOCATION, serialized)
            }.onFailure { ex ->
                warning("[Back] flushCache 保存失败($player): ${ex.message}")
            }
        }
    }

    private fun serialize(location: Location): String? {
        val worldName = location.world?.name ?: return null
        return "$worldName:${location.x}:${location.y}:${location.z}:${location.yaw}:${location.pitch}"
    }

    private fun deserialize(raw: String): BackData? {
        val parts = raw.split(":")
        if (parts.size < 4) return null
        val worldName = parts[0]
        val x = parts[1].toDoubleOrNull() ?: return null
        val y = parts[2].toDoubleOrNull() ?: return null
        val z = parts[3].toDoubleOrNull() ?: return null
        val yaw = parts.getOrElse(4) { "0.0" }.toFloatOrNull() ?: 0f
        val pitch = parts.getOrElse(5) { "0.0" }.toFloatOrNull() ?: 0f
        val world = Bukkit.getWorld(worldName) ?: return null
        return BackData(
            location = Location(world, x, y, z, yaw, pitch),
            reason = "persisted",
            timestamp = System.currentTimeMillis()
        )
    }
}
