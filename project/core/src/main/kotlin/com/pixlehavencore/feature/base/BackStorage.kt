package com.pixlehavencore.feature.base

import com.pixlehavencore.util.DatabaseUtils
import org.bukkit.Bukkit
import org.bukkit.Location
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import taboolib.expansion.MultipleHandler
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object BackStorage {

    private const val TABLE_NAME = "back_location"
    private const val KEY_LOCATION = "location"

    @Volatile
    private var handler: MultipleHandler? = null
    private val shuttingDown = AtomicBoolean(false)

    private val cache = ConcurrentHashMap<UUID, BackData>()

    fun init() {
        shuttingDown.set(false)
        reload()
    }

    fun reload() {
        if (shuttingDown.get()) return
        submitAsync {
            close()
            runCatching {
                handler = DatabaseUtils.newPlayerDataHandler(TABLE_NAME, syncTick = 200L)
            }.onSuccess {
                shuttingDown.set(false)
            }.onFailure { ex ->
                warning("[Back] 初始化 PlayerDatabase 失败: ${ex.message}")
                warning("[Back] 位置数据将无法持久化，请检查数据库配置！")
                close()
            }
        }
    }

    fun close() {
        shuttingDown.set(true)
        flushCache()
        DatabaseUtils.closeMultipleHandler(handler)
        handler = null
        cache.clear()
    }

    fun get(player: UUID): BackData? {
        return cache[player]
    }

    fun set(player: UUID, data: BackData) {
        cache[player] = data
        submitAsync {
            if (shuttingDown.get()) return@submitAsync
            saveToDatabase(player, data)
        }
    }

    fun remove(player: UUID) {
        cache.remove(player)
        submitAsync {
            if (shuttingDown.get()) return@submitAsync
            val currentHandler = handler ?: return@submitAsync
            runCatching {
                currentHandler.database[player.toString(), KEY_LOCATION] = null
            }.onFailure { ex ->
                warning("[Back] 删除玩家数据失败($player): ${ex.message}")
            }
        }
    }

    fun loadFromDatabase(player: UUID): BackData? {
        val currentHandler = handler ?: return null
        return runCatching {
            val raw = currentHandler.database[player.toString(), KEY_LOCATION] ?: return null
            deserialize(raw)
        }.getOrElse { ex ->
            warning("[Back] 读取玩家数据失败($player): ${ex.message}")
            null
        }
    }

    private fun saveToDatabase(player: UUID, data: BackData) {
        val currentHandler = handler ?: return
        val serialized = serialize(data.location) ?: return
        runCatching {
            currentHandler.database[player.toString(), KEY_LOCATION] = serialized
        }.onFailure { ex ->
            warning("[Back] 保存玩家数据失败($player): ${ex.message}")
        }
    }

    private fun flushCache() {
        val currentHandler = handler ?: return
        for ((player, data) in cache) {
            val serialized = serialize(data.location) ?: continue
            runCatching {
                currentHandler.database[player.toString(), KEY_LOCATION] = serialized
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
