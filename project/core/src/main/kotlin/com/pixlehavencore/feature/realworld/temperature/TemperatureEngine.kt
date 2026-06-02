package com.pixlehavencore.feature.realworld.temperature

import com.pixlehavencore.feature.realworld.*
import com.pixlehavencore.feature.realworld.enchantment.EnchantmentRegistry
import com.pixlehavencore.feature.realworld.season.SeasonEngine
import com.pixlehavencore.feature.realworld.weather.WeatherQuery
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.WeatherType as BukkitWeatherType
import org.bukkit.entity.Player

object TemperatureEngine {

    fun compute(
        player: Player,
        state: PlayerEnvState,
        global: GlobalEnvState,
        tickIntervalSeconds: Int,
    ) {
        // 减少死亡保护冷却时间
        if (state.deathProtectionCooldown > 0.0) {
            state.deathProtectionCooldown -= tickIntervalSeconds.coerceAtLeast(0).toDouble()
        }

        // 死亡保护：体温不变化
        if (state.deathProtectionTimer > 0.0) {
            state.deathProtectionTimer -= tickIntervalSeconds.coerceAtLeast(0).toDouble()
            // 在保护期内只更新遮蔽状态和潮湿度，不计算体温
            ShelterDetector.updateState(player, state, tickIntervalSeconds)
            computeWetness(player, state, global, tickIntervalSeconds)
            return
        }

        val location = player.location
        val biomeName = location.block.biome.toString().lowercase()
        val worldTime = location.world?.time ?: 6000L

        // 阶段 1: 基础环境温度
        val biomeBaseTemperature = getBiomeBaseTemperature(location, biomeName)
        state.biomeTemperature = biomeBaseTemperature

        val seasonModifier = SeasonEngine.getTemperatureModifier(global)
        val timeModifier = SeasonEngine.getTimeTemperatureModifier(worldTime, biomeName, location)
        val weatherModifier = WeatherQuery.getTemperatureModifierAt(location, global)
        val altitudeModifier = computeAltitudeModifier(location.blockY)

        ShelterDetector.updateState(player, state, tickIntervalSeconds)

        // 潮湿度
        computeWetness(player, state, global, tickIntervalSeconds)

        // 方块辐射扫描
        state.heatSourceScanTimer -= tickIntervalSeconds.coerceAtLeast(0)
        if (state.heatSourceScanTimer <= 0.0) {
            val scanResult = BlockRadiationScanner.scan(player, biomeBaseTemperature)
            state.nearHeatSource = scanResult.first
            state.temperatureBlockModifier = scanResult.second
            val interval = TemperatureSettings.heatSourceScanIntervalSeconds.toDouble()
            state.heatSourceScanTimer = interval
        }

        val blockRadiationModifier = state.temperatureBlockModifier

        // 阶段 2 + 3: 计算有效环境温度
        val isInWater = player.isInWater && TemperatureSettings.waterEnabled
        val effectiveEnvTemp: Double

        if (isInWater) {
            // 水中：水温 + 方块辐射
            val waterTemp = calculateWaterTemp(player, global, biomeName)
            state.lastWaterTemp = waterTemp
            effectiveEnvTemp = waterTemp + blockRadiationModifier
        } else {
            // 空气：基础环境 + 热源 + 湿度蒸发冷却
            var airFeelsLike = biomeBaseTemperature +
                seasonModifier + timeModifier + weatherModifier +
                altitudeModifier + blockRadiationModifier

            // 湿度蒸发冷却
            val wetnessCooling = -state.wetness * TemperatureSettings.wetnessCoolingFactor
            airFeelsLike += wetnessCooling

            // 阶段 4: 水/空气平滑过渡
            if (state.wetness > TemperatureSettings.waterExitBlendThreshold) {
                val blendFactor = state.wetness
                effectiveEnvTemp = airFeelsLike * (1.0 - blendFactor) + state.lastWaterTemp * blendFactor
            } else {
                effectiveEnvTemp = airFeelsLike
            }
        }

        // 阶段 5: 温度差
        var envDelta = effectiveEnvTemp - state.temperature

        // 传导率（水中导热更快）
        if (isInWater) {
            envDelta *= TemperatureSettings.waterConductivityMultiplier
        }

        // 阶段 6: 绝缘（护甲 + 遮蔽）
        val armorInsulation = getArmorInsulation(player)
        val shelterInsulation = when (state.shelterType) {
            ShelterType.NONE -> 0.0
            ShelterType.CANOPY -> TemperatureSettings.shelterCanopyInsulation
            ShelterType.BUILDING -> TemperatureSettings.shelterBuildingInsulation
        }
        val totalInsulation = armorInsulation + shelterInsulation
        val insulationFactor = 1.0 - totalInsulation.coerceIn(0.0, TemperatureSettings.armorInsulationMax)
        envDelta *= insulationFactor

        // 阶段 7: 主动回拉（体温调节）
        if (TemperatureSettings.regulationEnabled) {
            val setpoint = (TemperatureSettings.comfortMin + TemperatureSettings.comfortMax) / 2.0
            val deviation = state.temperature - setpoint

            val foodRatio = (player.foodLevel + player.saturation) / 40.0
            val foodFactor = 0.3 + foodRatio * 0.7
            val fracturePenalty = state.fracture / 100.0 * 0.5
            val capacity = (foodFactor - fracturePenalty).coerceAtLeast(0.1)

            val regulationForce = -deviation * TemperatureSettings.regulationStrength * capacity
            envDelta += regulationForce
        }

        // 阶段 8: 吸收曲线 + 动态限速
        // 动态上限 = 基础限速 + |温差| × 动态缩放，极端环境变化更快
        val maxChange = TemperatureSettings.maxChangeBase + Math.abs(envDelta) * TemperatureSettings.maxChangeDynamicScale
        val absorptionRate = TemperatureSettings.absorptionRate
        val rawChange = envDelta * (1.0 - Math.exp(-absorptionRate * Math.abs(envDelta)))
        val change = rawChange.coerceIn(-maxChange, maxChange)

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

    /**
     * 计算护甲绝缘值（百分比叠加）。
     * 返回总绝缘值（0.0 ~ armorInsulationMax）。
     */
    private fun getArmorInsulation(player: Player): Double {
        var insulation = 0.0
        for (armorPiece in player.inventory.armorContents) {
            val material = armorPiece?.type ?: continue
            val baseInsulation = when (material) {
                Material.LEATHER_HELMET,
                Material.LEATHER_CHESTPLATE,
                Material.LEATHER_LEGGINGS,
                Material.LEATHER_BOOTS,
                -> TemperatureSettings.armorInsulationLeather

                Material.CHAINMAIL_HELMET,
                Material.CHAINMAIL_CHESTPLATE,
                Material.CHAINMAIL_LEGGINGS,
                Material.CHAINMAIL_BOOTS,
                -> TemperatureSettings.armorInsulationChainmail

                Material.IRON_HELMET,
                Material.IRON_CHESTPLATE,
                Material.IRON_LEGGINGS,
                Material.IRON_BOOTS,
                -> TemperatureSettings.armorInsulationIron

                Material.GOLDEN_HELMET,
                Material.GOLDEN_CHESTPLATE,
                Material.GOLDEN_LEGGINGS,
                Material.GOLDEN_BOOTS,
                -> TemperatureSettings.armorInsulationGold

                Material.DIAMOND_HELMET,
                Material.DIAMOND_CHESTPLATE,
                Material.DIAMOND_LEGGINGS,
                Material.DIAMOND_BOOTS,
                -> TemperatureSettings.armorInsulationDiamond

                Material.NETHERITE_HELMET,
                Material.NETHERITE_CHESTPLATE,
                Material.NETHERITE_LEGGINGS,
                Material.NETHERITE_BOOTS,
                -> TemperatureSettings.armorInsulationNetherite

                else -> 0.0
            }

            // 检查温度抵抗附魔，每级增加 15% 绝缘值
            val enchantment = org.bukkit.Registry.ENCHANTMENT.get(EnchantmentRegistry.TEMPERATURE_RESISTANCE_KEY.key())
            val enchantLevel = if (enchantment != null) armorPiece.getEnchantmentLevel(enchantment) else 0
            val enchantBonus = if (enchantLevel > 0) 1.0 + enchantLevel * 0.15 else 1.0
            insulation += baseInsulation * enchantBonus
        }
        return insulation
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

        // 记录实际天气状态（不受遮蔽影响，用于 HUD 显示）
        state.isActuallyRaining = isRaining(player.location, global)

        // 检查玩家位置是否在淋雨（考虑遮蔽）
        val isRainingHere = !state.isWeatherSheltered && state.isActuallyRaining

        // 同步客户端天气视觉效果
        syncPlayerWeatherVisual(player, state, isRainingHere)

        when {
            player.isInWater -> state.wetness += TemperatureSettings.wetnessRateSubmerge * dt
            isRainingHere -> state.wetness += TemperatureSettings.wetnessRateRain * dt
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

    /**
     * 同步客户端天气视觉效果（雨滴、声音等）。
     * 使用 player.setPlayerWeather() 实现 per-player 天气，
     * 确保视觉效果与逻辑判断一致。
     */
    private fun syncPlayerWeatherVisual(player: Player, state: PlayerEnvState, isRainingHere: Boolean) {
        if (isRainingHere && !state.isClientRaining) {
            // 开始下雨
            state.isClientRaining = true
            player.setPlayerWeather(BukkitWeatherType.DOWNFALL)
        } else if (!isRainingHere && state.isClientRaining) {
            // 停止下雨
            state.isClientRaining = false
            player.setPlayerWeather(BukkitWeatherType.CLEAR)
        }
    }

    private fun isRaining(location: Location, global: GlobalEnvState): Boolean {
        return WeatherQuery.getWeatherAt(location, global).type == WeatherType.RAIN
    }
}
