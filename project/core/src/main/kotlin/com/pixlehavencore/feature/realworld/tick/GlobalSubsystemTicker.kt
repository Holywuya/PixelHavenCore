package com.pixlehavencore.feature.realworld.tick

import com.pixlehavencore.feature.realworld.GlobalEnvState

/**
 * 全局子系统 ticker。
 *
 * 由 [com.pixlehavencore.feature.realworld.RealWorldService] 在主调度线程持有 globalStateLock 时按注册顺序调用。
 * 实现类只负责推进或读取全局状态，禁止：
 *  - 触发 dirty 标记或存储写入
 *  - 调度新的任务
 *  - 访问玩家实体（玩家级状态变更走 PlayerSubsystemTicker）
 */
interface GlobalSubsystemTicker {
    fun tick(global: GlobalEnvState, dt: Int, context: GlobalTickContext)
}
