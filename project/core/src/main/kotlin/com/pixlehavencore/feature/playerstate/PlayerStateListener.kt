package com.pixlehavencore.feature.playerstate

import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent

object PlayerStateListener {

    @SubscribeEvent
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!PlayerStateSettings.enabled) return
        val player = event.player
        val uuid = player.uniqueId

        var data = PlayerStateStorage.get(uuid)
        if (data == null) {
            data = PlayerStateStorage.loadFromDatabase(uuid, player.name)
        }
        if (data == null) {
            data = PlayerStateStorage.getOrCreate(uuid)
        }

        val now = System.currentTimeMillis()
        if (data.firstJoinTime == 0L) {
            data.firstJoinTime = now
        }
        data.playerName = player.name
        data.lastJoinTime = now
        data.joinCount += 1
        PlayerStateStorage.saveImmediate(uuid)
    }

    @SubscribeEvent
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (!PlayerStateSettings.enabled) return
        val uuid = event.player.uniqueId
        val data = PlayerStateStorage.getOrCreate(uuid)
        data.lastQuitTime = System.currentTimeMillis()
        PlayerStateStorage.saveImmediate(uuid)
    }

    @SubscribeEvent(priority = EventPriority.MONITOR)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        if (!PlayerStateSettings.enabled) return
        val player = event.player
        PlayerStateService.setLastDeathLocation(player.uniqueId, player.location)
    }

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        if (!PlayerStateSettings.enabled) return
        PlayerStateService.setLastTeleportLocation(event.player.uniqueId, event.from)
    }
}
