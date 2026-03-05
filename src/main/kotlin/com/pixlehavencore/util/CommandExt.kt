package com.pixlehavencore.util

import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.ProxyPlayer
import taboolib.module.chat.colored

/**
 * 发送带颜色代码的消息，省去每次 .colored() 的模板代码。
 */
fun ProxyCommandSender.msg(text: String) = sendMessage(text.colored())

/**
 * 断言发送者是玩家；若不是，自动发送错误消息并返回 null。
 *
 * 用法：
 *   val player = sender.requirePlayer() ?: return@execute
 */
fun ProxyCommandSender.requirePlayer(
    errorMsg: String = "&c只有玩家可以使用此命令。"
): ProxyPlayer? {
    if (this is ProxyPlayer) return this
    msg(errorMsg)
    return null
}

/**
 * 断言发送者拥有指定权限；若没有，自动发送错误消息并返回 false。
 *
 * 用法：
 *   if (!sender.requirePermission("phcore.admin")) return@execute
 */
fun ProxyCommandSender.requirePermission(
    permission: String,
    errorMsg: String = "&c你没有权限执行该指令。"
): Boolean {
    if (hasPermission(permission)) return true
    msg(errorMsg)
    return false
}
