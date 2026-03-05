package com.pixlehavencore.feature.menu

import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePlayer
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand

@CommandHeader(name = "menu", aliases = ["ui"], permission = "phcore.menu")
object TrMenuLiteCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!SimpleMenuSettings.enabled) {
                sender.msg("&c菜单模块当前已禁用。")
                return@execute
            }
            val player = sender.requirePlayer() ?: return@execute
            val defaultMenu = SimpleMenuSettings.menus[SimpleMenuSettings.defaultMenuId]?.id
                ?: SimpleMenuSettings.menuIds.firstOrNull()
            if (defaultMenu == null) {
                sender.msg("&c未找到可用菜单配置。")
                return@execute
            }
            TrMenuLiteService.openMenu(player.cast() ?: return@execute, defaultMenu)
        }
    }

    @CommandBody
    val open = subCommand {
        dynamic(comment = "menu") {
            execute<ProxyCommandSender> { sender, _, argument ->
                if (!SimpleMenuSettings.enabled) {
                    sender.msg("&c菜单模块当前已禁用。")
                    return@execute
                }
                val player = sender.requirePlayer() ?: return@execute
                val target = argument.toString().trim()
                if (target.isBlank()) {
                    sender.msg("&c用法: /menu open <菜单ID>")
                    return@execute
                }
                TrMenuLiteService.openMenu(player.cast() ?: return@execute, target)
            }
        }
    }
}
