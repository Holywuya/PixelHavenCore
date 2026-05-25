package com.pixlehavencore.feature.realworld

import com.pixlehavencore.util.TextUtils
import org.bukkit.Bukkit
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.entity.Player
import taboolib.platform.util.sendActionBar
import taboolib.platform.util.submit as submitOnEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object SurvivalHud {

    private val bossBars = ConcurrentHashMap<UUID, BossBar>()

    fun render(player: Player, state: PlayerEnvState, global: GlobalEnvState) {
        player.submitOnEntity {
            renderCurrentThread(player, state, global)
        }
    }

    fun renderCurrentThread(player: Player, state: PlayerEnvState, global: GlobalEnvState) {
        runCatching {
            renderActionBar(player, state, global)
            renderBossBar(player, state)
        }
    }

    private fun renderActionBar(player: Player, state: PlayerEnvState, global: GlobalEnvState) {
        val statusText = buildStatusActionBar(state, global)
        val warningText = buildWarningActionBar(global)
        if (warningText != null) {
            val mergedText = if (isSevereState(state)) {
                "$statusText  &8|  $warningText"
            } else {
                warningText
            }
            player.sendActionBar(TextUtils.parse(colorize(mergedText)))
            return
        }

        val visibilityText = buildVisibilityActionBar(state, global)
        val finalText = if (visibilityText != null) {
            "$statusText  &8|  $visibilityText"
        } else {
            statusText
        }
        player.sendActionBar(TextUtils.parse(colorize(finalText)))
    }

    private fun buildStatusActionBar(state: PlayerEnvState, global: GlobalEnvState): String {
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

        return RealWorldSettings.hudActionBarFormat
            .replace("{temp}", "$tempColor${state.temperature.toInt()}")
            .replace("{hydration}", "$hydrationColor${state.hydration.toInt()}")
            .replace("{weather}", global.weather.displayName)
            .replace("{season}", global.season.displayName)
    }

    private fun buildWarningActionBar(global: GlobalEnvState): String? {
        val pendingWeather = global.pendingWeather ?: return null
        if (global.warningRemainingSeconds <= 0.0) {
            return null
        }

        val remainingSeconds = kotlin.math.ceil(global.warningRemainingSeconds).toInt().coerceAtLeast(1)
        val hint = when (pendingWeather) {
            WeatherType.BLIZZARD -> "请尽快寻找热源或进入室内"
            WeatherType.SANDSTORM -> "请尽快进入室内并远离露天区域"
            WeatherType.ACID_RAIN -> "请尽快寻找遮蔽物，避免暴露在雨中"
            else -> "请尽快做好防护"
        }
        return "&6⚠ &e${pendingWeather.displayName}&6将在 &c${remainingSeconds} &6秒后到来，&e$hint"
    }

    private fun buildVisibilityActionBar(state: PlayerEnvState, global: GlobalEnvState): String? {
        val weather = WeatherEngine.currentVisibilityWeather(global) ?: return null
        return when (weather) {
            WeatherType.FOG -> "&7薄雾弥漫，远处轮廓开始模糊"
            WeatherType.BLIZZARD -> if (state.isWeatherSheltered) {
                "&f室外暴风雪肆虐，白雾正压迫视野"
            } else {
                "&f暴风雪扑面，雪幕正在快速吞没视线"
            }
            WeatherType.SANDSTORM -> if (state.isWeatherSheltered) {
                "&6室外黄沙翻滚，离开遮蔽物会迅速失去视线"
            } else {
                "&6沙尘遮眼，近距离外几乎难以辨认目标"
            }
            else -> null
        }
    }

    private fun renderBossBar(player: Player, state: PlayerEnvState) {
        if (!RealWorldSettings.hudBossBarEnabled) {
            removeBossBar(player)
            return
        }

        val isSevere = isSevereState(state)
        if (!isSevere) {
            removeBossBar(player)
            return
        }

        val title: String
        val color: BarColor
        when {
            state.temperaturePhase == TemperaturePhase.SEVERE_HEAT -> {
                title = RealWorldSettings.hudBossBarTitleHeat
                color = BarColor.RED
            }
            state.temperaturePhase == TemperaturePhase.SEVERE_COLD -> {
                title = RealWorldSettings.hudBossBarTitleCold
                color = BarColor.BLUE
            }
            else -> {
                title = RealWorldSettings.hudBossBarTitleThirst
                color = BarColor.YELLOW
            }
        }

        val bossBar = bossBars.computeIfAbsent(player.uniqueId) {
            Bukkit.createBossBar(colorize(title), color, BarStyle.SOLID)
        }
        val gracePeriod = RealWorldSettings.extremeGracePeriodSeconds.toDouble()
        val progress = if (gracePeriod <= 0.0) {
            0.0
        } else {
            (state.graceTimer / gracePeriod).coerceIn(0.0, 1.0)
        }

        bossBar.setTitle(colorize(title))
        bossBar.color = color
        bossBar.progress = progress
        if (!bossBar.players.contains(player)) {
            bossBar.addPlayer(player)
        }
    }

    private fun removeBossBar(player: Player) {
        val bossBar = bossBars.remove(player.uniqueId) ?: return
        bossBar.removePlayer(player)
        bossBar.removeAll()
    }

    private fun isSevereState(state: PlayerEnvState): Boolean {
        return state.temperaturePhase == TemperaturePhase.SEVERE_HEAT ||
            state.temperaturePhase == TemperaturePhase.SEVERE_COLD ||
            state.thirstPhase == ThirstPhase.DEHYDRATED
    }

    fun onPlayerQuit(player: Player) {
        removeBossBar(player)
    }

    private fun colorize(text: String): String {
        return text.replace("&", "§")
    }
}
