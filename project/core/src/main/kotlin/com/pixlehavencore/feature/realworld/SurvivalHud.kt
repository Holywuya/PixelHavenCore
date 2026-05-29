package com.pixlehavencore.feature.realworld

import com.pixlehavencore.bridge.TextBridge
import com.pixlehavencore.feature.realworld.fracture.FractureEngine
import com.pixlehavencore.feature.realworld.fracture.FractureSeverity
import com.pixlehavencore.feature.realworld.stamina.StaminaPhase
import com.pixlehavencore.feature.realworld.stamina.StaminaSettings
import com.pixlehavencore.feature.realworld.temperature.FrostOverlay
import com.pixlehavencore.feature.realworld.temperature.HeatOverlay
import com.pixlehavencore.feature.realworld.weather.WeatherQuery
import com.pixlehavencore.util.TextUtils
import org.bukkit.Bukkit
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.entity.Player
import taboolib.platform.util.PlayerSessionMap
import taboolib.platform.util.submit as submitOnEntity
import java.util.UUID

object SurvivalHud {

    private val bossBars = PlayerSessionMap<BossBar>({ throw IllegalStateException() })

    fun render(player: Player, state: PlayerEnvState, global: GlobalEnvState) {
        player.submitOnEntity {
            renderCurrentThread(player, state, global)
        }
    }

    fun renderCurrentThread(player: Player, state: PlayerEnvState, global: GlobalEnvState) {
        runCatching {
            renderActionBar(player, state, global)
            renderBossBar(player, state)
            FrostOverlay.update(player, state.temperaturePhase)
            HeatOverlay.update(player, state.temperaturePhase)
        }
    }

    private fun renderActionBar(player: Player, state: PlayerEnvState, global: GlobalEnvState) {
        val statusText = buildStatusActionBar(player, state, global)
        TextBridge.sendActionBar(player, TextUtils.parse(colorize(statusText)))
    }

    private fun buildStatusActionBar(player: Player, state: PlayerEnvState, global: GlobalEnvState): String {
        val tempColor = when (state.temperaturePhase) {
            TemperaturePhase.COMFORTABLE -> "&a"
            TemperaturePhase.HEAT, TemperaturePhase.COLD_MILD -> "&6"
            TemperaturePhase.COLD -> "&e"
            TemperaturePhase.SEVERE_HEAT, TemperaturePhase.SEVERE_COLD -> "&c"
        }
        val hydrationColor = when (state.thirstPhase) {
            ThirstPhase.FULL -> "&a"
            ThirstPhase.THIRSTY -> "&6"
            ThirstPhase.SEVERE_THIRST, ThirstPhase.DEHYDRATED -> "&c"
        }
        val weather = WeatherQuery.getWeatherAt(player.location, global).type
        val shelterText = when (state.shelterType) {
            ShelterType.NONE -> RealWorldSettings.hudUnshelteredIndicator
            ShelterType.CANOPY -> "🌳"
            ShelterType.BUILDING -> RealWorldSettings.hudShelteredIndicator
        }

        val fractureSeverity = FractureEngine.classifyFracture(state.fracture)
        val fractureText = if (fractureSeverity != FractureSeverity.NONE) {
            " ${FractureEngine.getFractureColor(fractureSeverity)}🦴${state.fracture.toInt()}"
        } else {
            ""
        }

        // 使用 StringBuilder 避免多次 replace 产生临时 String
        val format = RealWorldSettings.hudActionBarFormat
        val sb = StringBuilder(format.length + 32)
        var i = 0
        while (i < format.length) {
            if (format[i] == '{') {
                val end = format.indexOf('}', i)
                if (end != -1) {
                    when (format.substring(i, end + 1)) {
                        "{temp}" -> sb.append(tempColor).append(state.temperature.toInt())
                        "{hydration}" -> sb.append(hydrationColor).append(state.hydration.toInt())
                        "{wetness}" -> sb.append((state.wetness * 100).toInt())
                        "{sheltered}" -> sb.append(shelterText).append(fractureText)
                        "{weather}" -> sb.append(weather.displayName)
                        "{season}" -> sb.append(global.season.displayName)
                        else -> sb.append(format, i, end + 1)
                    }
                    i = end + 1
                    continue
                }
            }
            sb.append(format[i])
            i++
        }
        return sb.toString()
    }

    private fun renderBossBar(player: Player, state: PlayerEnvState) {
        if (!RealWorldSettings.hudBossBarEnabled && !StaminaSettings.bossBarEnabled) {
            removeBossBar(player)
            return
        }

        val title: String
        val color: BarColor

        when {
            // 体力 DEPLETED 最高优先级
            StaminaSettings.bossBarEnabled && state.staminaPhase == StaminaPhase.DEPLETED -> {
                title = StaminaSettings.bossBarTitleDepleted
                color = BarColor.valueOf(StaminaSettings.bossBarColorDepleted)
            }
            // 体力 EXHAUSTED 次优先级
            StaminaSettings.bossBarEnabled && state.staminaPhase == StaminaPhase.EXHAUSTED -> {
                title = StaminaSettings.bossBarTitleExhausted
                color = BarColor.valueOf(StaminaSettings.bossBarColorExhausted)
            }
            // 温度/口渴极端状态
            state.temperaturePhase == TemperaturePhase.SEVERE_HEAT -> {
                title = RealWorldSettings.hudBossBarTitleHeat
                color = BarColor.RED
            }
            state.temperaturePhase == TemperaturePhase.SEVERE_COLD -> {
                title = RealWorldSettings.hudBossBarTitleCold
                color = BarColor.BLUE
            }
            state.thirstPhase == ThirstPhase.DEHYDRATED -> {
                title = RealWorldSettings.hudBossBarTitleThirst
                color = BarColor.YELLOW
            }
            else -> {
                removeBossBar(player)
                return
            }
        }

        val bossBar: BossBar = bossBars.get(player.uniqueId) ?: run {
            val barStyle = try { BarStyle.valueOf(StaminaSettings.bossBarStyle) } catch (_: Exception) { BarStyle.SOLID }
            val bar = Bukkit.createBossBar(colorize(title), color, barStyle)
            bossBars[player.uniqueId] = bar
            bar
        }

        bossBar.setTitle(colorize(title))
        bossBar.color = color
        bossBar.progress = 1.0
        if (!bossBar.players.contains(player)) {
            bossBar.addPlayer(player)
        }
    }

    private fun removeBossBar(player: Player) {
        val bossBar = bossBars.remove(player.uniqueId) ?: return
        bossBar.removePlayer(player)
        bossBar.removeAll()
    }

    fun onPlayerQuit(player: Player) {
        removeBossBar(player)
        FrostOverlay.clear(player)
        HeatOverlay.clear(player)
    }

    private fun colorize(text: String): String {
        return text.replace("&", "§")
    }
}
