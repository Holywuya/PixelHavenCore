package com.pixlehavencore.feature.realworld

import org.bukkit.entity.Player
import taboolib.platform.compat.PlaceholderExpansion

object RealWorldPlaceholders : PlaceholderExpansion {

    override val identifier: String = "phcorerw"

    override fun onPlaceholderRequest(player: Player?, args: String): String {
        return when (args.lowercase()) {
            "season" -> RealWorldService.getGlobalState()?.season?.displayName ?: ""
            "weather" -> RealWorldService.getGlobalState()?.weather?.displayName ?: ""
            "season_progress" -> RealWorldService.getGlobalState()?.let { global ->
                "%.1f%%".format(global.seasonProgress.coerceIn(0.0, 1.0) * 100)
            } ?: ""
            "temperature" -> player?.let { p ->
                RealWorldStorage.getPlayerSnapshot(p.uniqueId)?.temperature?.toInt()?.toString()
            } ?: ""
            "hydration" -> player?.let { p ->
                RealWorldStorage.getPlayerSnapshot(p.uniqueId)?.hydration?.toInt()?.toString()
            } ?: ""
            else -> ""
        }
    }
}
