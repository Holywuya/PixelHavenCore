package com.pixlehavencore.feature.realworld

import com.pixlehavencore.feature.realworld.fracture.FractureEngine
import com.pixlehavencore.feature.realworld.fracture.FractureSeverity
import com.pixlehavencore.feature.realworld.weather.WeatherQuery
import org.bukkit.entity.Player
import taboolib.platform.compat.PlaceholderExpansion

object RealWorldPlaceholders : PlaceholderExpansion {

    override val identifier: String = "phcorerw"

    override fun onPlaceholderRequest(player: Player?, args: String): String {
        val globalState = RealWorldService.getGlobalStateSnapshot()
        return when (args.lowercase()) {
            // === 全局信息 ===
            "season" -> globalState?.season?.displayName ?: ""
            "season_progress" -> globalState?.let { global ->
                "%.1f%%".format(global.seasonProgress.coerceIn(0.0, 1.0) * 100)
            } ?: ""
            "day_phase" -> globalState?.dayPhase?.displayName ?: ""
            "weather" -> {
                if (globalState == null || player == null) {
                    ""
                } else {
                    WeatherQuery.getWeatherAt(player.location, globalState).type.displayName
                }
            }

            // === 玩家体温 ===
            "temperature" -> player?.let { p ->
                RealWorldStorage.getPlayerSnapshot(p.uniqueId)?.temperature?.toInt()?.toString()
            } ?: ""
            "temperature_exact" -> player?.let { p ->
                RealWorldStorage.getPlayerSnapshot(p.uniqueId)?.temperature?.let { "%.1f".format(it) }
            } ?: ""
            "temperature_colored" -> player?.let { p ->
                RealWorldStorage.getPlayerSnapshot(p.uniqueId)?.let { state ->
                    val color = getTemperatureColor(state.temperaturePhase)
                    "${color}%.1f".format(state.temperature)
                }
            } ?: ""
            "temperature_phase" -> player?.let { p ->
                RealWorldStorage.getPlayerSnapshot(p.uniqueId)?.temperaturePhase?.displayName
            } ?: ""

            // === 玩家口渴 ===
            "hydration" -> player?.let { p ->
                RealWorldStorage.getPlayerSnapshot(p.uniqueId)?.hydration?.toInt()?.toString()
            } ?: ""
            "hydration_phase" -> player?.let { p ->
                RealWorldStorage.getPlayerSnapshot(p.uniqueId)?.thirstPhase?.displayName
            } ?: ""

            // === 玩家环境状态 ===
            "wetness" -> player?.let { p ->
                RealWorldStorage.getPlayerSnapshot(p.uniqueId)?.wetness?.let { "%.0f%%".format(it * 100) }
            } ?: ""
            "shelter" -> player?.let { p ->
                RealWorldStorage.getPlayerSnapshot(p.uniqueId)?.shelterType?.displayName
            } ?: ""
            "is_sheltered" -> player?.let { p ->
                RealWorldStorage.getPlayerSnapshot(p.uniqueId)?.isWeatherSheltered?.toString()
            } ?: ""
            "biome_temperature" -> player?.let { p ->
                RealWorldStorage.getPlayerSnapshot(p.uniqueId)?.biomeTemperature?.let { "%.1f".format(it) }
            } ?: ""
            "near_heat_source" -> player?.let { p ->
                RealWorldStorage.getPlayerSnapshot(p.uniqueId)?.nearHeatSource?.name?.lowercase() ?: "none"
            } ?: ""

            // === 玩家骨折 ===
            "fracture" -> player?.let { p ->
                RealWorldStorage.getPlayerSnapshot(p.uniqueId)?.fracture?.toInt()?.toString()
            } ?: ""
            "fracture_severity" -> player?.let { p ->
                val fracture = RealWorldStorage.getPlayerSnapshot(p.uniqueId)?.fracture ?: 0.0
                FractureEngine.classifyFracture(fracture).displayName
            } ?: ""

            // === 布尔判断（用于条件显示） ===
            "is_raining" -> player?.let { p ->
                RealWorldStorage.getPlayerSnapshot(p.uniqueId)?.isActuallyRaining?.toString()
            } ?: ""
            "is_comfortable" -> player?.let { p ->
                val phase = RealWorldStorage.getPlayerSnapshot(p.uniqueId)?.temperaturePhase
                (phase == TemperaturePhase.COMFORTABLE).toString()
            } ?: ""
            "is_thirsty" -> player?.let { p ->
                val phase = RealWorldStorage.getPlayerSnapshot(p.uniqueId)?.thirstPhase
                (phase != null && phase != ThirstPhase.FULL).toString()
            } ?: ""
            "is_injured" -> player?.let { p ->
                val fracture = RealWorldStorage.getPlayerSnapshot(p.uniqueId)?.fracture ?: 0.0
                (fracture > 0.0).toString()
            } ?: ""

            else -> ""
        }
    }

    private fun getTemperatureColor(phase: TemperaturePhase): String = when (phase) {
        TemperaturePhase.COMFORTABLE -> "&a"
        TemperaturePhase.HEAT, TemperaturePhase.COLD_MILD -> "&6"
        TemperaturePhase.COLD -> "&e"
        TemperaturePhase.SEVERE_HEAT, TemperaturePhase.SEVERE_COLD -> "&c"
    }

    private val TemperaturePhase.displayName: String
        get() = when (this) {
            TemperaturePhase.SEVERE_HEAT -> "严重过热"
            TemperaturePhase.HEAT -> "过热"
            TemperaturePhase.COMFORTABLE -> "舒适"
            TemperaturePhase.COLD_MILD -> "轻微寒冷"
            TemperaturePhase.COLD -> "寒冷"
            TemperaturePhase.SEVERE_COLD -> "严重寒冷"
        }

    private val ThirstPhase.displayName: String
        get() = when (this) {
            ThirstPhase.FULL -> "充足"
            ThirstPhase.THIRSTY -> "口渴"
            ThirstPhase.SEVERE_THIRST -> "严重口渴"
            ThirstPhase.DEHYDRATED -> "脱水"
        }

    private val ShelterType.displayName: String
        get() = when (this) {
            ShelterType.NONE -> "无遮蔽"
            ShelterType.CANOPY -> "树荫"
            ShelterType.BUILDING -> "建筑"
        }

    private val DayPhase.displayName: String
        get() = when (this) {
            DayPhase.DAY -> "白天"
            DayPhase.DUSK -> "黄昏"
            DayPhase.NIGHT -> "夜晚"
        }

    private val FractureSeverity.displayName: String
        get() = when (this) {
            FractureSeverity.NONE -> "无骨折"
            FractureSeverity.MILD -> "轻微骨折"
            FractureSeverity.MODERATE -> "中度骨折"
            FractureSeverity.SEVERE -> "严重骨折"
        }
}
