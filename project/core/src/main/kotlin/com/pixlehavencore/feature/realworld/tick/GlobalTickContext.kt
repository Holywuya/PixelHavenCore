package com.pixlehavencore.feature.realworld.tick

import org.bukkit.Location
import org.bukkit.entity.Player

/**
 * 全局 tick 期间共享的只读上下文。
 *
 * 不要把可变状态、Service 内部锁、调度句柄塞进来。
 */
class GlobalTickContext(
    val onlinePlayers: List<Player>,
    /**
     * 预收集的玩家位置快照，避免在 globalStateLock 内跨线程访问 player.location。
     * key = player.uniqueId
     */
    val playerLocations: Map<java.util.UUID, Location>,
)