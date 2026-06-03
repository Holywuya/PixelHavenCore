package com.pixlehavencore.feature.realworld.temperature

import com.pixlehavencore.feature.realworld.HeatSource
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.data.Lightable
import org.bukkit.entity.Player

/**
 * 方块辐射扫描子系统。
 *
 * 衰减叠加扫描周围温度方块（球体裁剪：只扫描半径 5 的球体）。
 * 公式: modifier = Σ (方块温度 - 环境温度) × 衰减因子^距离
 * 衰减因子默认 0.5，每远 1 格效果减半。
 * 多热源可叠加但有自然衰减，不会无限累加。
 */
object BlockRadiationScanner {

    private const val SCAN_RANGE = 5
    private const val MAX_DIST_SQ = SCAN_RANGE * SCAN_RANGE

    // 在构建时就过滤掉超出球体范围的条目，减少内存使用和迭代次数
    private val scanOffsets = buildList {
        for (x in -SCAN_RANGE..SCAN_RANGE) {
            for (y in -SCAN_RANGE..SCAN_RANGE) {
                for (z in -SCAN_RANGE..SCAN_RANGE) {
                    val distSq = x * x + y * y + z * z
                    if (distSq <= MAX_DIST_SQ) {
                        add(ScanOffset(x, y, z, distSq))
                    }
                }
            }
        }
    }

    /**
     * 扫描周围温度方块，返回 (最近热源枚举, 辐射加热偏移量)。
     */
    fun scan(player: Player, ambientTemp: Double): Pair<HeatSource?, Double> {
        val playerLocation = player.location
        val originBlock = playerLocation.block
        val temperatureBlocks = TemperatureSettings.temperatureBlocks
        if (temperatureBlocks.isEmpty()) return null to 0.0

        val baseCenterOffsetX = originBlock.x + 0.5 - playerLocation.x
        val baseCenterOffsetY = originBlock.y + 0.5 - (playerLocation.y + 0.5)
        val baseCenterOffsetZ = originBlock.z + 0.5 - playerLocation.z
        val decayFactor = TemperatureSettings.blockDecayFactor
        var blockModifier = 0.0
        var nearestSource: HeatSource? = null
        var nearestDistSq = Int.MAX_VALUE

        // 所有偏移量已经在构建时过滤，无需再次检查
        for (offset in scanOffsets) {
            val block = originBlock.getRelative(offset.x, offset.y, offset.z)
            val temp = temperatureBlocks[block.type] ?: continue
            if (!isBlockActive(block)) continue

            val dx = offset.x + baseCenterOffsetX
            val dy = offset.y + baseCenterOffsetY
            val dz = offset.z + baseCenterOffsetZ
            val distSq = dx * dx + dy * dy + dz * dz
            val distance = kotlin.math.sqrt(distSq)
            val contribution = (temp - ambientTemp) * Math.pow(decayFactor, distance)
            blockModifier += contribution

            if (offset.distSqInt < nearestDistSq) {
                nearestDistSq = offset.distSqInt
                nearestSource = matchHeatSource(block)
            }
        }

        return nearestSource to blockModifier
    }

    private fun isBlockActive(block: Block): Boolean {
        val data = block.blockData
        if (data is Lightable && !data.isLit) return false
        return true
    }

    private fun matchHeatSource(block: Block): HeatSource? {
        return when (block.type) {
            Material.LAVA -> HeatSource.LAVA
            Material.CAMPFIRE -> if (isLit(block)) HeatSource.CAMPFIRE else null
            Material.SOUL_CAMPFIRE -> if (isLit(block)) HeatSource.SOUL_CAMPFIRE else null
            Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER -> if (isLit(block)) HeatSource.FURNACE else null
            Material.FIRE -> HeatSource.FIRE
            Material.ICE -> HeatSource.ICE
            Material.PACKED_ICE -> HeatSource.PACKED_ICE
            Material.BLUE_ICE -> HeatSource.BLUE_ICE
            Material.MAGMA_BLOCK -> HeatSource.MAGMA_BLOCK
            else -> null
        }
    }

    private fun isLit(block: Block): Boolean {
        return (block.blockData as? Lightable)?.isLit == true
    }

    private data class ScanOffset(
        val x: Int,
        val y: Int,
        val z: Int,
        val distSqInt: Int,
    )
}
