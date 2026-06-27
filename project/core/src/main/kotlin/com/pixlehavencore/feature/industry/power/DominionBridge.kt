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
}
