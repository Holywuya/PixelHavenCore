package com.pixlehavencore.feature.realworld.temperature

import com.pixlehavencore.feature.realworld.PlayerEnvState
import com.pixlehavencore.feature.realworld.ShelterType
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.block.Block
import org.bukkit.entity.Player

/**
 * 遮蔽判定子系统。
 *
 * 检测玩家头顶是否有遮蔽（屋顶/树冠），输出 [ShelterType] 和天气遮蔽状态。
 * 内置 5 秒缓存，避免每个 tick 重复扫描。
 */
object ShelterDetector {

    private const val SHELTER_CACHE_SECONDS = 5.0

    /**
     * 更新玩家遮蔽状态（带缓存）。
     */
    fun updateState(player: Player, state: PlayerEnvState, tickIntervalSeconds: Int) {
        state.shelterCacheTimer -= tickIntervalSeconds.coerceAtLeast(0).toDouble()

        val eyeBlock = player.eyeLocation.block
        val movedToDifferentBlock =
            state.shelterCacheBlockX != eyeBlock.x ||
                state.shelterCacheBlockY != eyeBlock.y ||
                state.shelterCacheBlockZ != eyeBlock.z

        if (!movedToDifferentBlock && state.shelterCacheTimer > 0.0) {
            return
        }

        state.shelterType = classifyShelter(player)
        state.isWeatherSheltered = isWeatherSheltered(player.eyeLocation)
        state.shelterCacheBlockX = eyeBlock.x
        state.shelterCacheBlockY = eyeBlock.y
        state.shelterCacheBlockZ = eyeBlock.z
        state.shelterCacheTimer = SHELTER_CACHE_SECONDS
    }

    fun isUnderSolidRoof(location: Location): Boolean {
        return findWeatherRoofBlock(location, 0, 0) != null
    }

    fun isOpenToSky(location: Location): Boolean {
        return !hasAnyOverheadCover(location)
    }

    fun isWeatherSheltered(player: Player): Boolean {
        return isWeatherSheltered(player.eyeLocation)
    }

    fun isWeatherSheltered(location: Location): Boolean {
        if (!isUnderSolidRoof(location) || isOpenToSky(location)) {
            return false
        }
        return hasWeatherTopCoverage(location)
    }

    private fun classifyShelter(player: Player): ShelterType {
        val hasOverhead = hasAnyOverheadCover(player.eyeLocation)
        if (!hasOverhead) return ShelterType.NONE

        val hasCompleteRoof = hasWeatherTopCoverage(player.eyeLocation)
        return if (hasCompleteRoof) ShelterType.BUILDING else ShelterType.CANOPY
    }

    private fun hasAnyOverheadCover(location: Location): Boolean {
        val world = location.world ?: return false
        val highestBlockY = world.getHighestBlockYAt(location.blockX, location.blockZ)
        return highestBlockY >= location.blockY
    }

    private fun hasWeatherTopCoverage(location: Location): Boolean {
        val radius = TemperatureSettings.shelterHorizontalRadius
        for (xOffset in -radius..radius) {
            for (zOffset in -radius..radius) {
                if (findWeatherRoofBlock(location, xOffset, zOffset) == null) {
                    return false
                }
            }
        }
        return true
    }

    private fun findWeatherRoofBlock(location: Location, xOffset: Int, zOffset: Int): Block? {
        val world = location.world ?: return null
        val baseX = location.blockX + xOffset
        val baseY = location.blockY
        val baseZ = location.blockZ + zOffset
        // 限制扫描高度为 playerY + 30，避免扫描整个 Y 轴（最坏 256 格）
        val maxY = (baseY + 30).coerceAtMost(world.maxHeight - 1)
        for (y in baseY + 1..maxY) {
            val block = world.getBlockAt(baseX, y, baseZ)
            if (isWeatherRoofCandidate(block)) {
                return block
            }
        }
        return null
    }

    private fun isWeatherRoofCandidate(block: Block): Boolean {
        if (block.isEmpty || block.isLiquid) {
            return false
        }
        val material = block.type
        if (isBaseWeatherRoof(material)) {
            return true
        }
        if (TemperatureSettings.shelterGlassCountsAsShelter && isGlassLike(material)) {
            return true
        }
        if (TemperatureSettings.shelterLeavesCountAsShelter && Tag.LEAVES.isTagged(material)) {
            return true
        }
        return false
    }

    private fun isBaseWeatherRoof(material: Material): Boolean {
        return material.isOccluding ||
            material.name.endsWith("_SLAB") ||
            material.name.endsWith("_STAIRS")
    }

    private fun isGlassLike(material: Material): Boolean {
        return material == Material.GLASS ||
            material.name.endsWith("_GLASS") ||
            material.name.endsWith("_GLASS_PANE")
    }
}
