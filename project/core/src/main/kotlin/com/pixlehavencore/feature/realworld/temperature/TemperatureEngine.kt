package com.pixlehavencore.feature.realworld.temperature

import com.pixlehavencore.feature.realworld.*
import com.pixlehavencore.feature.realworld.season.SeasonEngine
import com.pixlehavencore.feature.realworld.weather.WeatherQuery
import com.pixlehavencore.feature.realworld.weather.WeatherSettings
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.block.Block
import org.bukkit.block.data.Lightable
import org.bukkit.entity.Player
import kotlin.math.abs

object TemperatureEngine {

    private const val TEMPERATURE_SCAN_RANGE = 5
    private const val SHELTER_CACHE_SECONDS = 5.0

    private val temperatureScanOffsets = buildList {
        for (x in -TEMPERATURE_SCAN_RANGE..TEMPERATURE_SCAN_RANGE) {
            for (y in -TEMPERATURE_SCAN_RANGE..TEMPERATURE_SCAN_RANGE) {
                for (z in -TEMPERATURE_SCAN_RANGE..TEMPERATURE_SCAN_RANGE) {
                    add(TemperatureScanOffset(x, y, z, x * x + y * y + z * z))
                }
            }
        }
    }

    fun compute(
        player: Player,
        state: PlayerEnvState,
        global: GlobalEnvState,
        tickIntervalSeconds: Int,
    ) {
        val location = player.location
        val biomeName = location.block.biome.toString().lowercase()
        val worldTime = location.world?.time ?: 6000L

        val biomeBaseTemperature = getBiomeBaseTemperature(biomeName)
        state.biomeTemperature = biomeBaseTemperature

        val seasonModifier = SeasonEngine.getTemperatureModifier(global)
        val timeModifier = SeasonEngine.getTimeTemperatureModifier(worldTime)
        val weatherModifier = if (WeatherSettings.localEnabled) {
            WeatherQuery.getTemperatureModifierAt(player.location, global)
        } else {
            global.weather.temperatureModifier
        }
        val altitudeModifier = computeAltitudeModifier(location.blockY)

        updateShelterState(player, state, tickIntervalSeconds)
        val shelteredModifier = if (state.isSheltered) 5.0 else 0.0
        val armorModifier = getArmorTemperatureBonus(player)

        val ambientBaseline = biomeBaseTemperature +
            seasonModifier + timeModifier + weatherModifier +
            altitudeModifier + shelteredModifier + armorModifier

        // 温度方块：距离加权扫描
        state.heatSourceScanTimer -= tickIntervalSeconds.coerceAtLeast(0)
        if (state.heatSourceScanTimer <= 0.0) {
            val scanResult = scanTemperatureBlocks(player, ambientBaseline)
            state.nearHeatSource = scanResult.first
            state.temperatureBlockModifier = scanResult.second
            val interval = TemperatureSettings.heatSourceScanIntervalSeconds.toDouble()
            while (state.heatSourceScanTimer <= 0.0) {
                state.heatSourceScanTimer += interval
            }
        }

        // 潮湿度
        computeWetness(player, state, global, tickIntervalSeconds)
        val insulationMultiplier = (1.0 - state.wetness * 0.8).coerceIn(0.2, 1.0)

        val rawTarget = ambientBaseline + state.temperatureBlockModifier

        val comfortableMid = (TemperatureSettings.comfortMin + TemperatureSettings.comfortMax) / 2.0
        val targetTemperature = comfortableMid + (rawTarget - comfortableMid) * insulationMultiplier

        val maxChangePerTick = TemperatureSettings.maxChangePerTick.coerceAtLeast(0.0)
        val temperatureDifference = targetTemperature - state.temperature
        val change = when {
            maxChangePerTick <= 0.0 -> 0.0
            abs(temperatureDifference) <= maxChangePerTick -> temperatureDifference
            temperatureDifference > 0.0 -> maxChangePerTick
            else -> -maxChangePerTick
        }

        state.temperature += change
        state.temperaturePhase = classifyTemperature(state.temperature)
    }

    fun getBiomeBaseTemperature(biomeName: String): Double {
        val normalizedName = biomeName.lowercase()
        return when {
            normalizedName.contains("nether") || normalizedName.contains("basalt") || normalizedName.contains("crimson") || normalizedName.contains("warped") -> 40.0
            normalizedName.contains("desert") || normalizedName.contains("badlands") -> 35.0
            normalizedName.contains("savanna") -> 30.0
            normalizedName.contains("jungle") || normalizedName.contains("bamboo") -> 28.0
            normalizedName.contains("swamp") || normalizedName.contains("mangrove") -> 24.0
            normalizedName.contains("snow") || normalizedName.contains("ice") || normalizedName.contains("frozen") -> -10.0
            normalizedName.contains("beach") || normalizedName.contains("stony_shore") -> 18.0
            normalizedName.contains("plains") || normalizedName.contains("sunflower") || normalizedName.contains("meadow") || normalizedName.contains("mushroom") -> 15.0
            normalizedName.contains("dark_forest") -> 12.0
            normalizedName.contains("forest") || normalizedName.contains("birch") || normalizedName.contains("cherry") -> 14.0
            normalizedName.contains("ocean") || normalizedName.contains("river") -> 12.0
            normalizedName.contains("end") -> 5.0
            normalizedName.contains("peak") || normalizedName.contains("mountain") || normalizedName.contains("windswept") || normalizedName.contains("stony") -> 5.0
            normalizedName.contains("taiga") || normalizedName.contains("grove") -> 3.0
            else -> 15.0
        }
    }

    fun isSheltered(player: Player): Boolean {
        return hasAnyOverheadCover(player.eyeLocation)
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

    private fun updateShelterState(player: Player, state: PlayerEnvState, tickIntervalSeconds: Int) {
        state.shelterCacheTimer -= tickIntervalSeconds.coerceAtLeast(0).toDouble()

        val eyeBlock = player.eyeLocation.block
        val movedToDifferentBlock =
            state.shelterCacheBlockX != eyeBlock.x ||
                state.shelterCacheBlockY != eyeBlock.y ||
                state.shelterCacheBlockZ != eyeBlock.z

        if (!movedToDifferentBlock && state.shelterCacheTimer > 0.0) {
            return
        }

        state.isSheltered = hasAnyOverheadCover(player.eyeLocation)
        state.isWeatherSheltered = isWeatherSheltered(player.eyeLocation)
        state.shelterCacheBlockX = eyeBlock.x
        state.shelterCacheBlockY = eyeBlock.y
        state.shelterCacheBlockZ = eyeBlock.z
        state.shelterCacheTimer = SHELTER_CACHE_SECONDS
    }

    /**
     * 距离加权扫描周围温度方块。
     * 返回 (最近热源枚举, 加权平均温度偏移量)。
     * 偏移量 = 加权平均方块温度 - 环境基准温度，
     * 正值加热、负值降温。
     * 权重公式: 1 / max(0.5, distance²)
     */
    private fun scanTemperatureBlocks(player: Player, ambientBaseline: Double): Pair<HeatSource?, Double> {
        val playerLocation = player.location
        val originBlock = playerLocation.block
        val temperatureBlocks = TemperatureSettings.temperatureBlocks
        if (temperatureBlocks.isEmpty()) return null to 0.0

        val baseCenterOffsetX = originBlock.x + 0.5 - playerLocation.x
        val baseCenterOffsetY = originBlock.y + 0.5 - (playerLocation.y + 0.5)
        val baseCenterOffsetZ = originBlock.z + 0.5 - playerLocation.z
        var weightedSum = 0.0
        var totalWeight = 0.0
        var nearestSource: HeatSource? = null
        var nearestDistSq = Int.MAX_VALUE

        for (offset in temperatureScanOffsets) {
            val block = originBlock.getRelative(offset.x, offset.y, offset.z)
            val temp = temperatureBlocks[block.type] ?: continue
            if (!isBlockActive(block)) continue

            val dx = offset.x + baseCenterOffsetX
            val dy = offset.y + baseCenterOffsetY
            val dz = offset.z + baseCenterOffsetZ
            val distSq = dx * dx + dy * dy + dz * dz
            val weight = 1.0 / maxOf(0.5, distSq)
            weightedSum += temp * weight
            totalWeight += weight

            if (offset.distSqInt < nearestDistSq) {
                nearestDistSq = offset.distSqInt
                nearestSource = matchLegacyHeatSource(block)
            }
        }

        val weightedAvg = if (totalWeight > 0.0) weightedSum / totalWeight else ambientBaseline
        return nearestSource to (weightedAvg - ambientBaseline)
    }

    private fun isBlockActive(block: Block): Boolean {
        val data = block.blockData
        if (data is Lightable && !data.isLit) return false
        return true
    }

    private fun matchLegacyHeatSource(block: Block): HeatSource? {
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
        val maxY = world.maxHeight - 1
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

    private fun getArmorTemperatureBonus(player: Player): Double {
        var totalBonus = 0.0
        for (armorPiece in player.inventory.armorContents) {
            val material = armorPiece?.type ?: continue
            totalBonus += when (material) {
                Material.LEATHER_HELMET,
                Material.LEATHER_CHESTPLATE,
                Material.LEATHER_LEGGINGS,
                Material.LEATHER_BOOTS,
                -> TemperatureSettings.armorBonusLeather / 4.0

                Material.NETHERITE_HELMET,
                Material.NETHERITE_CHESTPLATE,
                Material.NETHERITE_LEGGINGS,
                Material.NETHERITE_BOOTS,
                -> TemperatureSettings.armorBonusNetherite / 4.0

                else -> 0.0
            }
        }
        return totalBonus
    }

    fun classifyTemperature(temp: Double): TemperaturePhase {
        return when {
            temp >= TemperatureSettings.severeHeatThreshold -> TemperaturePhase.SEVERE_HEAT
            temp >= TemperatureSettings.heatThreshold -> TemperaturePhase.HEAT
            temp >= TemperatureSettings.coldMildThreshold -> TemperaturePhase.COMFORTABLE
            temp >= TemperatureSettings.coldThreshold -> TemperaturePhase.COLD_MILD
            temp >= TemperatureSettings.severeColdThreshold -> TemperaturePhase.COLD
            else -> TemperaturePhase.SEVERE_COLD
        }
    }

    private fun computeAltitudeModifier(blockY: Int): Double {
        val threshold = TemperatureSettings.altitudeThresholdY
        if (blockY <= threshold) {
            return 0.0
        }
        val exceededHeight = blockY - threshold
        return -exceededHeight * TemperatureSettings.altitudeDropPerBlock
    }

    private fun computeWetness(player: Player, state: PlayerEnvState, global: GlobalEnvState, tickSeconds: Int) {
        val dt = tickSeconds.coerceAtLeast(0).toDouble()
        when {
            player.isInWater -> state.wetness += TemperatureSettings.wetnessRateSubmerge * dt
            !state.isWeatherSheltered && isRaining(global) -> state.wetness += TemperatureSettings.wetnessRateRain * dt
            else -> {
                val dryRate = if (state.temperature > 30.0)
                    TemperatureSettings.wetnessDryRate * 2.0
                else
                    TemperatureSettings.wetnessDryRate
                state.wetness -= dryRate * dt
            }
        }
        state.wetness = state.wetness.coerceIn(0.0, 1.0)
    }

    private fun isRaining(global: GlobalEnvState): Boolean {
        return global.weather == WeatherType.RAIN ||
            global.weather == WeatherType.THUNDER ||
            global.weather == WeatherType.ACID_RAIN
    }

    private data class TemperatureScanOffset(
        val x: Int,
        val y: Int,
        val z: Int,
        val distSqInt: Int,
    )
}
