package com.pixlehavencore.util

import taboolib.common.platform.function.onlinePlayers
import taboolib.module.chat.colored
import java.util.UUID

/**
 * 广播带颜色代码的消息给所有在线玩家。
 * 替换 NotificationService 中的私有 broadcastMessage()。
 */
fun broadcastColored(message: String) {
    val colored = message.colored()
    onlinePlayers().forEach { it.sendMessage(colored) }
}

/**
 * 广播带颜色代码的消息给拥有指定权限的所有在线玩家，
 * 可选地排除特定 UUID（例如隐身通知中排除隐身玩家自身）。
 *
 * 替换 VanishService.notifyAdmins() 中的内联广播逻辑。
 */
fun broadcastToPermission(message: String, permission: String, exclude: UUID? = null) {
    val colored = message.colored()
    onlinePlayers().forEach { player ->
        if (exclude != null && player.uniqueId == exclude) return@forEach
        if (player.hasPermission(permission)) {
            player.sendMessage(colored)
        }
    }
}
