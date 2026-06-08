package com.pixlehavencore.util

import com.pixlehavencore.bridge.FoliaCompat
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.ChunkSnapshot
import taboolib.common.platform.function.warning
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object SafeLocationFinder {

    private val unsafeStandBlocks = setOf(
        Material.AIR,
        Material.CAVE_AIR,
        Material.VOID_AIR,
        Material.WATER,
        Material.LAVA,
        Material.FIRE,
        Material.SOUL_FIRE,
        Material.CACTUS,
        Material.COBWEB,
        Material.POWDER_SNOW,
        Material.SWEET_BERRY_BUSH,
        Material.WITHER_ROSE,
        Material.POINTED_DRIPSTONE,
        Material.TRIPWIRE,
        Material.TRIPWIRE_HOOK,
        Material.BIG_DRIPLEAF
    )

    private val safeOccupationBlocks = setOf(
        Material.AIR,
        Material.CAVE_AIR,
        Material.VOID_AIR,
        Material.WATER,
        Material.SHORT_GRASS,
        Material.TALL_GRASS,
        Material.FERN,
        Material.LARGE_FERN,
        Material.DEAD_BUSH,
        Material.DANDELION,
        Material.POPPY,
        Material.BLUE_ORCHID,
        Material.ALLIUM,
        Material.AZURE_BLUET,
        Material.RED_TULIP,
        Material.ORANGE_TULIP,
        Material.WHITE_TULIP,
        Material.PINK_TULIP,
        Material.OXEYE_DAISY,
        Material.CORNFLOWER,
        Material.LILY_OF_THE_VALLEY,
        Material.TORCHFLOWER,
        Material.SUNFLOWER,
        Material.LILAC,
        Material.ROSE_BUSH,
        Material.PEONY,
        Material.PITCHER_PLANT,
        Material.BROWN_MUSHROOM,
        Material.RED_MUSHROOM,
        Material.VINE,
        Material.GLOW_LICHEN,
        Material.SNOW,
        Material.TORCH,
        Material.SOUL_TORCH,
        Material.REDSTONE_TORCH
    )

    fun findSafeLocationAsync(
        world: World,
        centerX: Double,
        centerZ: Double,
        minRadius: Double,
        maxRadius: Double,
        maxAttempts: Int,
        timeoutSeconds: Double
    ): CompletableFuture<Location?> {
        val actualMin = minRadius.coerceAtMost(maxRadius)
        val actualMax = maxRadius.coerceAtLeast(actualMin)
        var attempt = 0

        fun tryNext(): CompletableFuture<Location?> {
            if (attempt >= maxAttempts) {
                return CompletableFuture.completedFuture(null)
            }
            attempt++

            val (x, z) = generateCandidateCoordinate(centerX, centerZ, actualMin, actualMax)
            val chunkX = x.toInt() shr 4
            val chunkZ = z.toInt() shr 4

            val future = CompletableFuture<Location?>()
            FoliaCompat.getChunkAtAsync(world, chunkX, chunkZ)
                .orTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .thenAccept { chunk ->
                    val snapshot = chunk.chunkSnapshot
                    val localX = x.toInt() and 0xF
                    val localZ = z.toInt() and 0xF

                    val safeLoc = searchSafe5x5(snapshot, world, localX, localZ, chunkX, chunkZ)
                    if (safeLoc != null) {
                        future.complete(safeLoc)
                    } else {
                        tryNext().thenAccept { success ->
                            future.complete(success)
                        }.exceptionally { ex ->
                            future.completeExceptionally(ex)
                            null
                        }
                    }
                }
                .exceptionally { ex ->
                    warning("[SafeLocationFinder] 候选位置判定失败(第${attempt}次): ${ex.message}")
                    tryNext().thenAccept { success ->
                        future.complete(success)
                    }.exceptionally { innerEx ->
                        future.completeExceptionally(innerEx)
                        null
                    }
                    null
                }
            return future
        }

        return tryNext()
    }

    fun findSafeLocationNearAsync(
        targetLoc: Location,
        timeoutSeconds: Double
    ): CompletableFuture<Location?> {
        val world = targetLoc.world ?: return CompletableFuture.completedFuture(null)

        return FoliaCompat.getChunkAtAsync(targetLoc)
            .orTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .thenApply { chunk ->
                val snapshot = chunk.chunkSnapshot
                val blockX = targetLoc.blockX
                val blockY = targetLoc.blockY
                val blockZ = targetLoc.blockZ
                val localX = blockX and 0xF
                val localZ = blockZ and 0xF
                val chunkX = blockX shr 4
                val chunkZ = blockZ shr 4

                val feetY = blockY - 1

                for (dy in 0..8) {
                    val y = feetY + dy
                    if (y + 2 > world.maxHeight) break
                    val loc = searchSafe5x5AtY(snapshot, world, localX, localZ, chunkX, chunkZ, y)
                    if (loc != null) return@thenApply loc
                }

                for (dy in 1..8) {
                    val y = feetY - dy
                    if (y < world.minHeight) break
                    val loc = searchSafe5x5AtY(snapshot, world, localX, localZ, chunkX, chunkZ, y)
                    if (loc != null) return@thenApply loc
                }

                null
            }
            .exceptionally { ex ->
                warning("[SafeLocationFinder] 附近安全位置搜索失败: ${ex.message}")
                null
            }
    }

    private fun searchSafe5x5(
        snapshot: ChunkSnapshot,
        world: World,
        centerLocalX: Int,
        centerLocalZ: Int,
        chunkX: Int,
        chunkZ: Int
    ): Location? {
        for (dx in -2..2) {
            for (dz in -2..2) {
                val lx = centerLocalX + dx
                val lz = centerLocalZ + dz
                if (lx !in 0..15 || lz !in 0..15) continue

                val highestY = snapshot.getHighestBlockYAt(lx, lz)
                if (isSafePosition(snapshot, lx, highestY, lz)) {
                    val wx = (chunkX shl 4) + lx + 0.5
                    val wz = (chunkZ shl 4) + lz + 0.5
                    if (world.worldBorder.isInside(Location(world, wx, 0.0, wz))) {
                        return Location(world, wx, (highestY + 1).toDouble(), wz)
                    }
                }
            }
        }
        return null
    }

    private fun searchSafe5x5AtY(
        snapshot: ChunkSnapshot,
        world: World,
        centerLocalX: Int,
        centerLocalZ: Int,
        chunkX: Int,
        chunkZ: Int,
        y: Int
    ): Location? {
        for (dx in -2..2) {
            for (dz in -2..2) {
                val lx = centerLocalX + dx
                val lz = centerLocalZ + dz
                if (lx !in 0..15 || lz !in 0..15) continue

                if (isSafePosition(snapshot, lx, y, lz)) {
                    val wx = (chunkX shl 4) + lx + 0.5
                    val wz = (chunkZ shl 4) + lz + 0.5
                    if (world.worldBorder.isInside(Location(world, wx, 0.0, wz))) {
                        return Location(world, wx, (y + 1).toDouble(), wz)
                    }
                }
            }
        }
        return null
    }

    private fun isSafePosition(snapshot: ChunkSnapshot, localX: Int, y: Int, localZ: Int): Boolean {
        val feetBlock = snapshot.getBlockType(localX, y, localZ)
        val bodyBlock = snapshot.getBlockType(localX, y + 1, localZ)
        val headBlock = snapshot.getBlockType(localX, y + 2, localZ)

        return feetBlock !in unsafeStandBlocks
            && bodyBlock in safeOccupationBlocks
            && headBlock in safeOccupationBlocks
    }

    private fun generateCandidateCoordinate(
        centerX: Double,
        centerZ: Double,
        minRadius: Double,
        maxRadius: Double
    ): Pair<Double, Double> {
        val angle = Random.nextDouble(0.0, 2 * Math.PI)
        val distance = minRadius + Random.nextDouble(0.0, maxRadius - minRadius)
        val x = centerX + distance * cos(angle)
        val z = centerZ + distance * sin(angle)
        return Pair(x, z)
    }
}
