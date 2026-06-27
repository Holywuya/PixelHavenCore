package com.pixlehavencore.feature.industry.power

import org.bukkit.Bukkit
import org.bukkit.Location

object DominionBridge {

    private var available: Boolean = false

    fun init() {
        available = Bukkit.getPluginManager().getPlugin("Dominion") != null
    }

    fun isAvailable(): Boolean = available

    fun getDominionId(location: Location): String? {
        if (!available) return null
        return try {
            val plugin = Bukkit.getPluginManager().getPlugin("Dominion") ?: return null
            val result = plugin.javaClass.getMethod(
                "getDominionIdByLocation", Location::class.java
            ).invoke(plugin, location)
            result as? String
        } catch (e: Exception) {
            null
        }
    }

    fun getDominionArea(dominionId: String): Int {
        if (!available) return 0
        return try {
            val plugin = Bukkit.getPluginManager().getPlugin("Dominion") ?: return 0
            val result = plugin.javaClass.getMethod(
                "getDominionAreaById", String::class.java
            ).invoke(plugin, dominionId)
            (result as? Int) ?: 0
        } catch (e: Exception) {
            0
        }
    }
}
