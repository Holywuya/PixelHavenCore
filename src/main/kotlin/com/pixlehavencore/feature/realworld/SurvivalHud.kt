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

        val text = RealWorldSettings.hudActionBarFormat
            .replace("{temp}", "$tempColor${state.temperature.toInt()}")
            .replace("{hydration}", "$hydrationColor${state.hydration.toInt()}")
            .replace("{weather}", global.weather.displayName)
            .replace("{season}", global.season.displayName)

        player.sendActionBar(TextUtils.parse(colorize(text)))
    }

    private fun renderBossBar(player: Player, state: PlayerEnvState) {
        if (!RealWorldSettings.hudBossBarEnabled) {
            removeBossBar(player)
            return
        }

        val isSevere = state.temperaturePhase == TemperaturePhase.SEVERE_HEAT ||
            state.temperaturePhase == TemperaturePhase.SEVERE_COLD ||
            state.thirstPhase == ThirstPhase.DEHYDRATED
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

    fun onPlayerQuit(player: Player) {
        removeBossBar(player)
    }

    private fun colorize(text: String): String {
        return text.replace("&", "§")
    }
}
