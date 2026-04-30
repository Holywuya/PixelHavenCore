package com.pixlehavencore.feature.spawners

import com.pixlehavencore.util.MythicMobsBridge
import com.pixlehavencore.util.RandomUtils
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import taboolib.common.platform.function.info
import taboolib.common.platform.function.onlinePlayers
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.warning
import taboolib.platform.util.submit as submitOnEntity
import taboolib.platform.util.submit as submitOnLocation
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.cos
import kotlin.math.sin

object SpawnerService {

    private val tasks = ConcurrentHashMap<String, Any?>()
    @Volatile
    private var running = false

    fun init() {
        running = false
        stopTasks()
        SpawnerSettings.reload()

        if (!SpawnerSettings.enabled) {
            info("[Spawners] 模块已在主配置中禁用。")
            return
        }

        val enabledSpawners = SpawnerSettings.spawners.filter { it.enabled }
        if (enabledSpawners.isEmpty()) {
            info("[Spawners] 已加载 ${SpawnerSettings.spawners.size} 个刷怪配置，但没有任何启用项。")
            return
        }

        running = true
        enabledSpawners
            .sortedWith(compareByDescending<SpawnerDefinition> { it.priority }.thenBy { it.spawnerId.lowercase() })
            .forEach { startSpawnerTask(it) }
        info("[Spawners] 已加载 ${SpawnerSettings.spawners.size} 个刷怪配置，其中 ${enabledSpawners.size} 个已启用。")
    }

    fun reload() {
        init()
    }

    fun stop() {
        running = false
        stopTasks()
    }

    fun isEnabled(): Boolean {
        return SpawnerSettings.enabled && SpawnerSettings.spawners.any { it.enabled }
    }

    private fun startSpawnerTask(definition: SpawnerDefinition) {
        val task = submit(delay = definition.intervalTicks, period = definition.intervalTicks) {
            if (!running || !definition.enabled) {
                return@submit
            }
            runCatching {
                tickSpawner(definition)
            }.onFailure { ex ->
                warning("[Spawners] [${definition.sourcePath}] 刷怪执行失败: ${ex.message}")
            }
        }
        tasks["${definition.sourcePath}#${definition.spawnerId}"] = task
    }

    private fun stopTasks() {
        tasks.values.forEach { invokeCancel(it) }
        tasks.clear()
    }

    private fun tickSpawner(definition: SpawnerDefinition) {
        if (!running || !definition.enabled) {
            return
        }
        if (!RandomUtils.roll(definition.chance)) {
            return
        }

        val players = collectEligiblePlayers(definition)
        if (players.isEmpty()) {
            return
        }

        repeat(definition.spawnAmount) {
            val player = players.randomOrNull() ?: return@repeat
            scheduleSpawn(definition, player)
        }
    }

    private fun collectEligiblePlayers(definition: SpawnerDefinition): List<Player> {
        // Folia: 使用 onlinePlayers() 快照 + proxy.cast 替代 Bukkit.getPlayer
        return onlinePlayers().mapNotNull { proxy ->
            val player = proxy.cast<Player>() ?: return@mapNotNull null
            if (definition.matchesWorld(proxy.world)) {
                player
            } else {
                null
            }
        }
    }

    private fun scheduleSpawn(definition: SpawnerDefinition, player: Player) {
        player.submitOnEntity {
            if (!running || !definition.enabled) {
                return@submitOnEntity
            }
            val anchor = player.location.clone()
            val spawnLocation = buildSpawnLocation(anchor, definition.distance) ?: return@submitOnEntity
            spawnLocation.submitOnLocation {
                if (!running || !definition.enabled) {
                    return@submitOnLocation
                }
                spawnAtLocation(definition, spawnLocation)
            }
        }
    }

    private fun buildSpawnLocation(anchor: Location, maxDistance: Int): Location? {
        val world = anchor.world ?: return null
        val distanceCap = if (maxDistance > 0) maxDistance else 12
        val minDistance = 4.0.coerceAtMost(distanceCap.toDouble())
        val distance = if (distanceCap <= minDistance) {
            distanceCap.toDouble()
        } else {
            ThreadLocalRandom.current().nextDouble(minDistance, distanceCap.toDouble())
        }
        val angle = ThreadLocalRandom.current().nextDouble(0.0, Math.PI * 2.0)
        val x = anchor.x + cos(angle) * distance
        val z = anchor.z + sin(angle) * distance
        return Location(world, x, anchor.y, z)
    }

    private fun spawnAtLocation(definition: SpawnerDefinition, spawnLocation: Location) {
        val world = spawnLocation.world ?: return
        val blockX = spawnLocation.blockX
        val blockZ = spawnLocation.blockZ
        val highestY = world.getHighestBlockYAt(blockX, blockZ)
        val finalLocation = Location(world, blockX + 0.5, highestY + 1.0, blockZ + 0.5)
        if (!isSpawnLocationSafe(finalLocation)) {
            return
        }
        if (reachedMaxAmount(definition, finalLocation)) {
            return
        }

        val mobId = definition.types.randomOrNull() ?: return
        val entity = MythicMobsBridge.spawnMob(mobId, finalLocation, definition.rollLevel()) ?: return
        entity.addScoreboardTag(definition.trackingTag)
        applyDespawnPolicy(entity, finalLocation, definition)
    }

    private fun reachedMaxAmount(definition: SpawnerDefinition, center: Location): Boolean {
        if (definition.maxAmount <= 0) {
            return false
        }
        val world = center.world ?: return false
        val range = (definition.distance * 2).coerceAtLeast(1).toDouble()
        val existing = world.getNearbyEntities(center, range, range, range)
            .count { entity -> entity.isValid && entity.scoreboardTags.contains(definition.trackingTag) }
        return existing >= definition.maxAmount
    }

    private fun isSpawnLocationSafe(location: Location): Boolean {
        val block = location.block
        val ground = location.clone().add(0.0, -1.0, 0.0).block
        return block.isPassable && ground.type.isSolid
    }

    private fun applyDespawnPolicy(entity: Entity, spawnLocation: Location, definition: SpawnerDefinition) {
        if (entity is LivingEntity) {
            entity.removeWhenFarAway = definition.removeWhenFarAway
        }

        if (definition.delayTicks <= 0L || definition.distance <= 0) {
            return
        }

        val despawnDistanceSquared = definition.distance.toDouble() * definition.distance.toDouble()
        entity.submitOnEntity(delay = definition.delayTicks) {
            if (!entity.isValid || entity.location.world?.uid != spawnLocation.world?.uid) {
                return@submitOnEntity
            }
            if (entity.location.distanceSquared(spawnLocation) >= despawnDistanceSquared) {
                entity.remove()
            }
        }
    }

    private fun invokeCancel(task: Any?) {
        if (task == null) {
            return
        }
        runCatching {
            task.javaClass.methods.firstOrNull { method ->
                method.name == "cancel" && method.parameterTypes.isEmpty()
            }?.invoke(task)
        }
    }
}
