package com.pixlehavencore.util

import cn.lunadeer.dominion.api.DominionAPI
import cn.lunadeer.dominion.api.dtos.flag.Flags
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player

object DominionBridge {

    private const val PLUGIN_NAME = "Dominion"
    private const val BYPASS_PERMISSION = "dominion.admin.bypass"

    @Volatile
    private var api: DominionAPI? = null

    fun isAvailable(): Boolean {
        if (api != null) return true
        val plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME) ?: return false
        api = try {
            DominionAPI.getInstance()
        } catch (_: Throwable) {
            null
        }
        return api != null
    }

    fun canFlyAt(player: Player, loc: Location): Boolean {
        val dominionAPI = api ?: return true
        if (player.hasPermission(BYPASS_PERMISSION)) return true
        if (dominionAPI.getDominion(loc) == null) return true
        return dominionAPI.checkPrivilegeFlagSilence(loc, Flags.FLY, player)
    }
}
