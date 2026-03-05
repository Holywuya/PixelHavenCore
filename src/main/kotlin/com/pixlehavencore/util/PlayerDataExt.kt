package com.pixlehavencore.util

import taboolib.common.platform.ProxyPlayer
import taboolib.expansion.getDataContainer
import taboolib.expansion.setupDataContainer

/**
 * 确保玩家数据容器已初始化，替换各模块中重复的 ensureContainer() 私有函数。
 *
 * 使用此扩展的模块：
 *   - ChatMentionStorage
 *   - VeinminerLimitService
 *   - ViewDistanceService
 */
fun ProxyPlayer.ensureDataContainer() {
    runCatching { getDataContainer() }.getOrElse { setupDataContainer() }
}
