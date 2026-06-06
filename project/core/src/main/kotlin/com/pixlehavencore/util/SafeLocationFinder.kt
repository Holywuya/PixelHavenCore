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

    /**
     * RTP 场景：异步搜索安全随机位置。
     * CompletableFuture 链式重试，每次重试为链的一个阶段。
     * 使用迭代计数器控制重试深度，禁止递归方法调用，堆栈深度 O(1)。
     *
     * @param world    目标世界
     * @param centerX  搜索中心 X
     * @param centerZ  搜索中心 Z
     * @param minRadius 最小半径
     * @param maxRadius 最大半径
     * @param maxAttempts 最大尝试次数
     * @param timeoutSeconds 区块加载超时秒数
     * @return CompletableFuture<Location?> 安全位置，null 表示搜索失败
     */
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

            return FoliaCompat.getChunkAtAsync(world, chunkX, chunkZ)
                .orTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .thenApply { chunk -> chunk.chunkSnapshot }
                .thenCompose { snapshot ->
                    val localX = x.toInt() and 0xF
                    val localZ = z.toInt() and 0xF
                    val highestY = snapshot.getHighestBlockYAt(localX, localZ)

                    if (isSafePosition(snapshot, localX, highestY, localZ)) {
                        CompletableFuture.completedFuture(
                            Location(world, x, (highestY + 1).toDouble(), z)
                        )
                    } else {
                        tryNext()
                    }
                }
                .exceptionally { ex ->
                    warning("[SafeLocationFinder] 候选位置判定失败(第${attempt}次): ${ex.message}")
                    null
                }
        }

        return tryNext()
    }

    /**
     * Back 场景：在目标位置附近异步搜索安全位置。
     * 仅加载目标位置所在区块，在 ChunkSnapshot 上搜索。
     *
     * @param targetLoc 目标位置
     * @param timeoutSeconds 区块加载超时秒数
     * @return CompletableFuture<Location?> 安全位置，null 表示未找到
     */
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

                val feetY = blockY - 1
                if (isSafePosition(snapshot, localX, feetY, localZ)) {
                    return@thenApply targetLoc.clone()
                }

                for (dy in 1..8) {
                    val y = feetY + dy
                    if (y + 1 > world.maxHeight) break
                    if (isSafePosition(snapshot, localX, y, localZ)) {
                        val safeLoc = targetLoc.clone()
                        safeLoc.y = (y + 1).toDouble()
                        return@thenApply safeLoc
                    }
                }

                for (dy in 1..8) {
                    val y = feetY - dy
                    if (y < world.minHeight) break
                    if (isSafePosition(snapshot, localX, y, localZ)) {
                        val safeLoc = targetLoc.clone()
                        safeLoc.y = (y + 1).toDouble()
                        return@thenApply safeLoc
                    }
                }

                null
            }
            .exceptionally { ex ->
                warning("[SafeLocationFinder] 附近安全位置搜索失败: ${ex.message}")
                null
            }
    }

    /**
     * 通过 ChunkSnapshot 判定位置安全性（纯函数，线程安全）。
     * 脚下方块必须可站立（非 AIR/非可通过），身体+头部方块必须可通过。
     */
    private fun isSafePosition(snapshot: ChunkSnapshot, localX: Int, y: Int, localZ: Int): Boolean {
        val feetBlock = snapshot.getBlockType(localX, y, localZ)
        val bodyBlock = snapshot.getBlockType(localX, y + 1, localZ)
        val headBlock = snapshot.getBlockType(localX, y + 2, localZ)

        val isPassable = { mat: Material -> mat == Material.AIR || mat.isAir }
        return !isPassable(feetBlock) && isPassable(bodyBlock) && isPassable(headBlock)
    }

    /**
     * 极坐标随机采样生成候选坐标（纯函数，无 World API 调用）。
     */
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