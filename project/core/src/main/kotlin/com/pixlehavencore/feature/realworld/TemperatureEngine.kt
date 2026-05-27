package com.pixlehavencore.feature.realworld

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.block.Block
import org.bukkit.block.data.Lightable
import org.bukkit.entity.Player
import kotlin.math.abs

object TemperatureEngine {

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
        val weatherModifier = if (RealWorldSettings.localWeatherEnabled) {
            WeatherQuery.getTemperatureModifierAt(player.location, global)
        } else {
            global.weather.temperatureModifier
        }
        val altitudeModifier = computeAltitudeModifier(location.blockY)

        state.isSheltered = isSheltered(player)
        state.isWeatherSheltered = isWeatherSheltered(player)
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
            val interval = RealWorldSettings.heatSourceScanIntervalSeconds.toDouble()
            while (state.heatSourceScanTimer <= 0.0) {
                state.heatSourceScanTimer += interval
            }
        }

        // 潮湿度
        computeWetness(player, state, global, tickIntervalSeconds)
        val insulationMultiplier = (1.0 - state.wetness * 0.8).coerceIn(0.2, 1.0)

        val rawTarget = ambientBaseline + state.temperatureBlockModifier

        val comfortableMid = (RealWorldSettings.comfortMin + RealWorldSettings.comfortMax) / 2.0
        val targetTemperature = comfortableMid + (rawTarget - comfortableMid) * insulationMultiplier

        val maxChangePerTick = RealWorldSettings.maxChangePerTick.coerceAtLeast(0.0)
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

    /**
     * 距离加权扫描周围温度方块。
     * 返回 (最近热源枚举, 加权平均温度偏移量)。
     * 偏移量 = 加权平均方块温度 - 环境基准温度，
     * 正值加热、负值降温。
     * 权重公式: 1 / max(0.5, distance²)
     */
    private fun scanTemperatureBlocks(player: Player, ambientBaseline: Double): Pair<HeatSource?, Double> {
        val originBlock = player.location.block
        val temperatureBlocks = RealWorldSettings.temperatureBlocks
        if (temperatureBlocks.isEmpty()) return null to 0.0

        val originLoc = player.location.add(0.0, 0.5, 0.0)
        var weightedSum = 0.0
        var totalWeight = 0.0
        var nearestSource: HeatSource? = null
        var nearestDistSq = Int.MAX_VALUE

        val maxRange = 5
        for (x in -maxRange..maxRange) {
            for (y in -maxRange..maxRange) {
                for (z in -maxRange..maxRange) {
                    val block = originBlock.getRelative(x, y, z)
                    val temp = temperatureBlocks[block.type] ?: continue
                    if (!isBlockActive(block)) continue

                    val blockCenter = block.location.add(0.5, 0.5, 0.5)
                    val distSq = originLoc.distanceSquared(blockCenter)
                    val weight = 1.0 / maxOf(0.5, distSq)
                    weightedSum += temp * weight
                    totalWeight += weight

                    val distSqInt = x * x + y * y + z * z
                    if (distSqInt < nearestDistSq) {
                        nearestDistSq = distSqInt
                        nearestSource = matchLegacyHeatSource(block)
                    }
                }
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
        val radius = RealWorldSettings.shelterHorizontalRadius
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
        if (RealWorldSettings.shelterGlassCountsAsShelter && isGlassLike(material)) {
            return true
        }
        if (RealWorldSettings.shelterLeavesCountAsShelter && Tag.LEAVES.isTagged(material)) {
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
                -> RealWorldSettings.armorBonusLeather / 4.0

                Material.NETHERITE_HELMET,
                Material.NETHERITE_CHESTPLATE,
                Material.NETHERITE_LEGGINGS,
                Material.NETHERITE_BOOTS,
                -> RealWorldSettings.armorBonusNetherite / 4.0

                else -> 0.0
            }
        }
        return totalBonus
    }

    fun classifyTemperature(temp: Double): TemperaturePhase {
        return when {
            temp >= RealWorldSettings.severeHeatThreshold -> TemperaturePhase.SEVERE_HEAT
            temp >= RealWorldSettings.heatThreshold -> TemperaturePhase.HEAT
            temp >= RealWorldSettings.coldMildThreshold -> TemperaturePhase.COMFORTABLE
            temp >= RealWorldSettings.coldThreshold -> TemperaturePhase.COLD_MILD
            temp >= RealWorldSettings.severeColdThreshold -> TemperaturePhase.COLD
            else -> TemperaturePhase.SEVERE_COLD
        }
    }

    private fun computeAltitudeModifier(blockY: Int): Double {
        val threshold = RealWorldSettings.altitudeThresholdY
        if (blockY <= threshold) {
            return 0.0
        }
        val exceededHeight = blockY - threshold
        return -exceededHeight * RealWorldSettings.altitudeDropPerBlock
    }

    private fun computeWetness(player: Player, state: PlayerEnvState, global: GlobalEnvState, tickSeconds: Int) {
        val dt = tickSeconds.coerceAtLeast(0).toDouble()
        when {
            player.isInWater -> state.wetness += RealWorldSettings.wetnessRateSubmerge * dt
            !state.isWeatherSheltered && isRaining(global) -> state.wetness += RealWorldSettings.wetnessRateRain * dt
            else -> {
                val dryRate = if (state.temperature > 30.0)
                    RealWorldSettings.wetnessDryRate * 2.0
                else
                    RealWorldSettings.wetnessDryRate
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
}
