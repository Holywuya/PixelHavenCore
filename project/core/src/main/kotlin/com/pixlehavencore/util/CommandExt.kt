package com.pixlehavencore.util

import com.pixlehavencore.bridge.TextBridge
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.ProxyPlayer
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.CommandSender

private val PERMISSION_ALIASES = mapOf(
    "phcore.viewdistance.admin" to listOf("phcore.vdc.admin"),
    "phcore.viewdistance.afk.bypass" to listOf("phcore.vdc.afk.bypass"),
    "phcore.viewdistance.dynamic.bypass" to listOf("phcore.vdc.dynamic.bypass"),
    "phcore.viewdistance.ping.toggle" to listOf("phcore.vdc.ping.toggle"),
    "phcore.notification.admin" to listOf("phcore.notify.admin"),
    "phcore.entityclearer.admin" to listOf("phcore.entityclear.admin"),
    "phcore.veinminer.admin" to listOf("veinminer.admin"),
    "phcore.economy.admin" to listOf("eco.admin.cbank")
)

/**
 * 发送带颜色代码的消息，支持 MiniMessage 标签和 & 颜色码。
 */
fun ProxyCommandSender.msg(text: String) =
    sendMessage(TextBridge.toLegacy(TextUtils.parse(text)))

/**
 * 全局超级权限：`phcore.admin`。
 * 任何模块权限校验都会先检查这个节点。
 */
fun ProxyCommandSender.hasPermissionOrAdmin(permission: String): Boolean {
    if (hasPermission("phcore.admin") || hasPermission(permission)) return true
    return PERMISSION_ALIASES[permission].orEmpty().any { hasPermission(it) }
}

fun CommandSender.hasPermissionOrAdmin(permission: String): Boolean {
    if (hasPermission("phcore.admin") || hasPermission(permission)) return true
    return PERMISSION_ALIASES[permission].orEmpty().any { hasPermission(it) }
}

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
    if (hasPermissionOrAdmin(permission)) return true
    msg(errorMsg)
    return false
}

fun resolveOfflinePlayer(name: String): OfflinePlayer? {
    val online = Bukkit.getPlayerExact(name)
    if (online != null) return online
    return runCatching { Bukkit.getOfflinePlayer(name) }.getOrNull()
}
