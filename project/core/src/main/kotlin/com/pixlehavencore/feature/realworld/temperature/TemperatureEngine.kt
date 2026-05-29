package com.pixlehavencore.feature.realworld.temperature

import com.pixlehavencore.feature.realworld.*
import com.pixlehavencore.feature.realworld.season.SeasonEngine
import com.pixlehavencore.feature.realworld.weather.WeatherQuery
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.block.Biome
import org.bukkit.block.Block
import org.bukkit.block.data.Lightable
import org.bukkit.entity.Player

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

        val biomeBaseTemperature = getBiomeBaseTemperature(location, biomeName)
        state.biomeTemperature = biomeBaseTemperature

        val seasonModifier = SeasonEngine.getTemperatureModifier(global)
        val timeModifier = SeasonEngine.getTimeTemperatureModifier(worldTime, biomeName, location)
        val weatherModifier = WeatherQuery.getTemperatureModifierAt(location, global)
        val altitudeModifier = computeAltitudeModifier(location.blockY)

        updateShelterState(player, state, tickIntervalSeconds)
        val armorModifier = getArmorTemperatureBonus(player)

        // 潮湿度
        computeWetness(player, state, global, tickIntervalSeconds)

        // 温度方块：衰减叠加扫描
        state.heatSourceScanTimer -= tickIntervalSeconds.coerceAtLeast(0)
        if (state.heatSourceScanTimer <= 0.0) {
            val scanResult = scanTemperatureBlocks(player, biomeBaseTemperature)
            state.nearHeatSource = scanResult.first
            state.temperatureBlockModifier = scanResult.second
            val interval = TemperatureSettings.heatSourceScanIntervalSeconds.toDouble()
            while (state.heatSourceScanTimer <= 0.0) {
                state.heatSourceScanTimer += interval
            }
        }

        // 体感温度：浸水时直接替换为水温，否则计算空气体感温度
        val feelsLike = if (player.isInWater && TemperatureSettings.waterEnabled) {
            calculateWaterTemp(player, global, biomeName)
        } else {
            calculateAirFeelsLike(
                player = player,
                state = state,
                global = global,
                biomeBaseTemperature = biomeBaseTemperature,
                seasonModifier = seasonModifier,
                timeModifier = timeModifier,
                weatherModifier = weatherModifier,
                altitudeModifier = altitudeModifier,
                armorModifier = armorModifier,
            )
        }

        val absorptionRate = TemperatureSettings.absorptionRate
        val changeRate = if (player.isInWater && TemperatureSettings.waterEnabled) {
            TemperatureSettings.waterConductivityMultiplier
        } else {
            1.0
        }

        val envDelta = (feelsLike - state.temperature) * changeRate

        // 体温调节阻尼：当环境让体温远离设定点时，阻尼减缓这个过程
        val dampingFactor = if (TemperatureSettings.regulationEnabled) {
            val setpoint = (TemperatureSettings.comfortMin + TemperatureSettings.comfortMax) / 2.0
            val deviation = state.temperature - setpoint

            // 判断环境是否让体温远离设定点
            val isMovingAway = (envDelta > 0 && deviation > 0) || (envDelta < 0 && deviation < 0)

            if (isMovingAway) {
                // 饱食度影响阻尼强度（饱食度高 → 阻尼强 → 体温更稳定）
                val foodRatio = (player.foodLevel + player.saturation) / 40.0
                val foodFactor = 0.3 + foodRatio * 0.7
                // 骨折降低阻尼效果（骨折越重，体温越难维持）
                val fracturePenalty = state.fracture / 100.0 * 0.5
                // 阻尼系数：1 - 阻尼强度，范围 0.15~0.65（骨折时更高）
                1.0 - (foodFactor * 0.5 - fracturePenalty).coerceAtLeast(0.0)
            } else {
                // 环境让体温靠近设定点，无阻尼
                1.0
            }
        } else {
            1.0
        }

        val change = envDelta * dampingFactor * (1.0 - Math.exp(-absorptionRate * Math.abs(envDelta)))

        state.temperature += change
        state.temperaturePhase = classifyTemperature(state.temperature)
    }

    fun getBiomeBaseTemperature(location: Location, biomeName: String): Double {
        // 优先使用 Bukkit API 从世界获取原生温度
        location.world?.let { world ->
            try {
                val nativeTemp = world.getTemperature(location.blockX, location.blockY, location.blockZ)
                // Minecraft 原生温度范围：-0.5 到 2.0
                // 转换为摄氏度：-0.5 → -12.5°C, 0.0 → 0°C, 1.0 → 25°C, 2.0 → 50°C
                return nativeTemp * 25.0
            } catch (e: Exception) {
                // API 调用失败，fallback 到名称匹配
            }
        }

        // Fallback：基于名称的硬编码映射（保留用于兼容）
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

    fun getBiomeWaterTemp(location: Location, biomeName: String): Double {
        location.world?.let { world ->
            try {
                val nativeTemp = world.getTemperature(location.blockX, location.blockY, location.blockZ)
                // 原生温度 0.0~2.0 映射到水温 2~24°C
                // 0.0(冰冻)→2°C, 0.5(海洋)→12°C, 0.95(丛林)→22°C, 2.0(下界)→24°C(上限)
                return (2.0 + nativeTemp * 11.0).coerceIn(2.0, 24.0)
            } catch (e: Exception) {
                // fallback
            }
        }

        val normalizedName = biomeName.lowercase()
        return when {
            normalizedName.contains("jungle") || normalizedName.contains("bamboo") -> 24.0
            normalizedName.contains("swamp") || normalizedName.contains("mangrove") -> 22.0
            normalizedName.contains("desert") || normalizedName.contains("badlands") -> 20.0
            normalizedName.contains("plains") || normalizedName.contains("forest") || normalizedName.contains("beach") -> 16.0
            normalizedName.contains("ocean") || normalizedName.contains("river") -> 14.0
            normalizedName.contains("taiga") || normalizedName.contains("dark_forest") -> 10.0
            normalizedName.contains("snow") || normalizedName.contains("ice") || normalizedName.contains("frozen") -> 2.0
            else -> 14.0
        }
    }

    fun calculateWaterTemp(player: Player, global: GlobalEnvState, biomeName: String): Double {
        if (!TemperatureSettings.waterEnabled) {
            return 14.0
        }

        val biomeWaterTemp = getBiomeWaterTemp(player.location, biomeName)

        val previousSeason = getPreviousSeason(global.season)
        val seasonLag = previousSeason.temperatureModifier * TemperatureSettings.waterSeasonLagRatio

        val waterSurfaceY = findWaterSurfaceY(player.location)
        val waterDepth = (waterSurfaceY - player.location.blockY).coerceAtLeast(0)
        val depthModifier = -(waterDepth / 10.0).coerceAtMost(TemperatureSettings.waterMaxDepthCool) *
            TemperatureSettings.waterDepthCoolPer10Blocks

        return biomeWaterTemp + seasonLag + depthModifier
    }

    private fun getPreviousSeason(currentSeason: Season): Season {
        val previousIndex = (currentSeason.ordinal - 1 + Season.entries.size) % Season.entries.size
        return Season.entries[previousIndex]
    }

    private fun findWaterSurfaceY(location: Location): Int {
        val world = location.world ?: return location.blockY
        val maxY = world.maxHeight - 1
        for (y in location.blockY + 1..maxY) {
            val block = world.getBlockAt(location.blockX, y, location.blockZ)
            if (block.type != Material.WATER) {
                return y
            }
        }
        return maxY
    }

    fun getBiomeDayNightFactor(location: Location, biomeName: String): Double {
        location.world?.let { world ->
            try {
                val nativeTemp = world.getTemperature(location.blockX, location.blockY, location.blockZ)
                // 干燥/炎热群系温差大，潮湿/水域群系温差小
                // nativeTemp 0.0~2.0 映射到因子 0.5~1.5
                return (0.5 + nativeTemp * 0.5).coerceIn(0.5, 1.5)
            } catch (e: Exception) {
                // fallback
            }
        }

        val normalizedName = biomeName.lowercase()
        return when {
            normalizedName.contains("desert") || normalizedName.contains("badlands") -> 1.5
            normalizedName.contains("jungle") || normalizedName.contains("bamboo") -> 0.5
            normalizedName.contains("swamp") || normalizedName.contains("mangrove") -> 0.5
            normalizedName.contains("ocean") || normalizedName.contains("river") -> 0.7
            else -> 1.0
        }
    }

    fun calculateAirFeelsLike(
        player: Player,
        state: PlayerEnvState,
        global: GlobalEnvState,
        biomeBaseTemperature: Double,
        seasonModifier: Double,
        timeModifier: Double,
        weatherModifier: Double,
        altitudeModifier: Double,
        armorModifier: Double,
    ): Double {
        val shelterModifier = when (state.shelterType) {
            ShelterType.NONE -> 0.0
            ShelterType.CANOPY -> TemperatureSettings.shelterCanopyBonus
            ShelterType.BUILDING -> TemperatureSettings.shelterBuildingBonus
        }

        val blockModifier = state.temperatureBlockModifier

        val wetnessModifier = if (player.isInWater) {
            0.0
        } else {
            -state.wetness * TemperatureSettings.wetnessCoolingFactor
        }

        return biomeBaseTemperature +
            seasonModifier + timeModifier + weatherModifier +
            altitudeModifier + shelterModifier + armorModifier +
            blockModifier + wetnessModifier
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

        state.shelterType = classifyShelter(player)
        state.isWeatherSheltered = isWeatherSheltered(player.eyeLocation)
        state.shelterCacheBlockX = eyeBlock.x
        state.shelterCacheBlockY = eyeBlock.y
        state.shelterCacheBlockZ = eyeBlock.z
        state.shelterCacheTimer = SHELTER_CACHE_SECONDS
    }

    private fun classifyShelter(player: Player): ShelterType {
        val hasOverhead = hasAnyOverheadCover(player.eyeLocation)
        if (!hasOverhead) return ShelterType.NONE

        val hasCompleteRoof = hasWeatherTopCoverage(player.eyeLocation)
        return if (hasCompleteRoof) ShelterType.BUILDING else ShelterType.CANOPY
    }

    /**
     * 衰减叠加扫描周围温度方块（球体裁剪：只扫描半径 5 的球体）。
     * 返回 (最近热源枚举, 辐射加热偏移量)。
     * 公式: modifier = Σ (方块温度 - 环境温度) × 衰减因子^距离
     * 衰减因子默认 0.5，每远 1 格效果减半。
     * 多热源可叠加但有自然衰减，不会无限累加。
     */
    private fun scanTemperatureBlocks(player: Player, ambientTemp: Double): Pair<HeatSource?, Double> {
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

        val maxDistSq = TEMPERATURE_SCAN_RANGE * TEMPERATURE_SCAN_RANGE
        for (offset in temperatureScanOffsets) {
            // 球体裁剪：跳过超出半径的方块
            if (offset.distSqInt > maxDistSq) continue

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
                nearestSource = matchLegacyHeatSource(block)
            }
        }

        return nearestSource to blockModifier
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
            !state.isWeatherSheltered && isRaining(player.location, global) -> state.wetness += TemperatureSettings.wetnessRateRain * dt
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

    private fun isRaining(location: Location, global: GlobalEnvState): Boolean {
        return WeatherQuery.getWeatherAt(location, global).type == WeatherType.RAIN
    }

    private data class TemperatureScanOffset(
        val x: Int,
        val y: Int,
        val z: Int,
        val distSqInt: Int,
    )
}
