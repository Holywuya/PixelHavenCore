package com.pixlehavencore.feature.realworld.tick

import com.pixlehavencore.feature.realworld.GlobalEnvState
import com.pixlehavencore.feature.realworld.PlayerEnvState
import org.bukkit.entity.Player

/**
 * 玩家级子系统 ticker。
 *
 * 由 [com.pixlehavencore.feature.realworld.RealWorldService] 在玩家所在的实体线程
 * （Folia 区域线程）内、`RealWorldStorage.withPlayerState` 块中按注册顺序调用。
 *
 * 实现类只负责状态计算与状态变更，禁止：
 *  - 触发 dirty 标记或存储写入（dirty 由 Service 通过差量比较决定）
 *  - 调度新的任务
 *  - 触发 HUD 刷新（由 Service 在所有玩家 ticker 完成后处理）
 */
interface PlayerSubsystemTicker {
    fun tick(player: Player, state: PlayerEnvState, global: GlobalEnvState, dt: Int)
}