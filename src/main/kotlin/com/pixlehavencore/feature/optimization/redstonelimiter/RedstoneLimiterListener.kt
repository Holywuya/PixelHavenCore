package com.pixlehavencore.feature.optimization.redstonelimiter

import org.bukkit.event.block.BlockPhysicsEvent
import org.bukkit.event.block.BlockRedstoneEvent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent

object RedstoneLimiterListener {

    // LOWEST 优先级确保在其他插件处理之前完成判定和阻断
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onBlockRedstone(event: BlockRedstoneEvent) {
        if (!RedstoneLimiterSettings.enabled) return

        // 信号未实际变化时忽略，减少无效检测
        if (event.newCurrent == event.oldCurrent) return

        val block = event.block
        val world = block.world

        if (world.name !in RedstoneLimiterSettings.enabledWorlds) return
        if (block.type !in RedstoneLimiterSettings.redstoneBlockTypes) return

        if (RedstoneLimiterService.onRedstoneEvent(world.name, block.x, block.y, block.z, block.type.name)) {
            // BlockRedstoneEvent 不可取消(Cancellable)，通过将信号归零来阻断传播
            event.newCurrent = 0
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onBlockPhysics(event: BlockPhysicsEvent) {
        if (!RedstoneLimiterSettings.enabled) return

        val changedType = event.changedType
        if (changedType !in RedstoneLimiterSettings.redstoneBlockTypes) return

        val block = event.block
        val world = block.world

        if (world.name !in RedstoneLimiterSettings.enabledWorlds) return

        if (RedstoneLimiterService.onRedstoneEvent(world.name, block.x, block.y, block.z, changedType.name)) {
            event.isCancelled = true
        }
    }
}
