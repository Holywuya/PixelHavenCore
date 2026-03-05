package com.pixlehavencore.feature.veinminer

import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.adaptPlayer
import taboolib.expansion.releaseDataContainer
import taboolib.expansion.setupDataContainer

object VeinminerListener {

    @SubscribeEvent
    fun onBlockBreak(event: BlockBreakEvent) {
        if (event.isCancelled) {
            return
        }
        if (VeinminerService.handleBlockBreak(event.player, event.block)) {
            event.isCancelled = true
        }
    }

    @SubscribeEvent
    fun onJoin(event: PlayerJoinEvent) {
        adaptPlayer(event.player).setupDataContainer()
    }

    @SubscribeEvent
    fun onQuit(event: PlayerQuitEvent) {
        adaptPlayer(event.player).releaseDataContainer()
    }
}
