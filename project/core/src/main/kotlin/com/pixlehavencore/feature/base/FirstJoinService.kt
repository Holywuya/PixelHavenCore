package com.pixlehavencore.feature.base

import com.pixlehavencore.util.DatabaseUtils
import com.pixlehavencore.util.TextUtils
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import taboolib.expansion.MultipleHandler
import taboolib.platform.util.submit as submitOnEntity
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

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
        if (!BaseCommandSettings.enabled) return
        if (!ready) return

        val uuid = player.uniqueId
        val world = Bukkit.getWorld("world") ?: Bukkit.getWorlds().firstOrNull() ?: return

        submitAsync {
            if (!isFirstJoin(uuid)) return@submitAsync

            val spawn = world.spawnLocation
            val centerX = spawn.x + FirstJoinSettings.centerX
            val centerZ = spawn.z + FirstJoinSettings.centerZ

            val targetLoc = findRandomSafeLocation(
                world,
                centerX,
                centerZ,
                FirstJoinSettings.minRadius,
                FirstJoinSettings.maxRadius,
                FirstJoinSettings.safeLocationRetries
            )

            if (targetLoc == null) {
                warning("[FirstJoin] 未找到安全随机位置(${player.name})")
                return@submitAsync
            }

            player.submitOnEntity {
                player.teleport(targetLoc)
                val msg = FirstJoinSettings.msgTeleported
                    .replace("{x}", targetLoc.blockX.toString())
                    .replace("{y}", targetLoc.blockY.toString())
                    .replace("{z}", targetLoc.blockZ.toString())
                player.sendMessage(TextUtils.parse(msg))
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

    private fun findRandomSafeLocation(
        world: World,
        centerX: Double,
        centerZ: Double,
        minRadius: Double,
        maxRadius: Double,
        retries: Int
    ): Location? {
        val actualMin = minRadius.coerceAtMost(maxRadius)
        val actualMax = maxRadius.coerceAtLeast(actualMin)

        repeat(5 * retries) {
            val angle = Random.nextDouble(0.0, 2 * Math.PI)
            val distance = actualMin + Random.nextDouble(0.0, actualMax - actualMin)
            val x = centerX + distance * cos(angle)
            val z = centerZ + distance * sin(angle)
            val y = world.getHighestBlockYAt(x.toInt(), z.toInt())
            val loc = Location(world, x, y + 1.0, z)
            if (loc.block.isPassable && world.getBlockAt(loc.blockX, loc.blockY + 1, loc.blockZ).isPassable) {
                return loc
            }
        }

        return null
    }

    fun isEnabled(): Boolean = FirstJoinSettings.enabled
}
