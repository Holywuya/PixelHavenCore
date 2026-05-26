package com.pixlehavencore.util

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player

object EntityUtils {

    fun nearbyPlayers(world: World, center: Location, radius: Double): List<Player> {
        val radiusSquared = radius * radius
        return world.players.filter { it.location.distanceSquared(center) <= radiusSquared }
    }

    fun hasMoved(current: Location, start: Location, maxDistanceSquared: Double = 0.25): Boolean {
        if (current.world?.uid != start.world?.uid) {
            return true
        }
        return current.distanceSquared(start) > maxDistanceSquared
    }
}
