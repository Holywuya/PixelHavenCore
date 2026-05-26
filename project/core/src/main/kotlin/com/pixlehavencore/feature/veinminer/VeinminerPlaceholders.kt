package com.pixlehavencore.feature.veinminer

import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import taboolib.common.platform.function.adaptPlayer
import taboolib.platform.compat.PlaceholderExpansion

object VeinminerPlaceholders : PlaceholderExpansion {

    override val identifier: String = "veinminer"

    override fun onPlaceholderRequest(player: Player?, args: String): String {
        if (player == null) {
            return "0"
        }
        val proxy = adaptPlayer(player)
        return when (args.lowercase()) {
            "remaining" -> VeinminerLimitService.getRemaining(proxy).toString()
            "limit" -> VeinminerLimitService.getLimitValue(proxy).toString()
            "used" -> (VeinminerLimitService.getLimitValue(proxy) - VeinminerLimitService.getRemaining(proxy)).toString()
            "reset_seconds" -> VeinminerLimitService.getResetSeconds(proxy).toString()
            else -> "0"
        }
    }

    override fun onPlaceholderRequest(player: OfflinePlayer?, args: String): String {
        if (player?.isOnline == true) {
            return onPlaceholderRequest(player.player, args)
        }
        return "0"
    }
}
