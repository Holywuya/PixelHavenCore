package com.pixlehavencore.feature.realworld.tick

import org.bukkit.entity.Player

/**
 * 全局 tick 期间共享的只读上下文。
 *
 * 第一版只包含全局 ticker 之间确实需要共享的字段。
 * 不要把可变状态、Service 内部锁、调度句柄塞进来。
 */
class GlobalTickContext(
    val onlinePlayers: List<Player>,
)