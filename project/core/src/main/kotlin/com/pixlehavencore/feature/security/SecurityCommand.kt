package com.pixlehavencore.feature.security

import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePermission
import com.pixlehavencore.util.requirePlayer
import com.pixlehavencore.util.resolveOfflinePlayer
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.suggestPlayers
import taboolib.common.platform.command.subCommand

@CommandHeader(name = "security", permissionDefault = PermissionDefault.TRUE)
object SecurityCommand {

    @CommandBody
    val inv = subCommand {
        dynamic(comment = "player") {
            suggestPlayers()
            execute<ProxyCommandSender> { sender, _, argument ->
                if (!sender.requirePermission("phcore.admin")) return@execute
                val viewer = sender.requirePlayer()?.cast<Player>() ?: return@execute
                val targetName = argument.toString().trim()
                val target = resolveOfflinePlayer(targetName) ?: run {
                    sender.msg("&c未找到玩家: $targetName")
                    return@execute
                }
                if (!SecurityService.openInventory(viewer, target)) {
                    sender.msg("&c打开玩家背包失败。")
                }
            }
        }
    }

    @CommandBody
    val ec = subCommand {
        dynamic(comment = "player") {
            suggestPlayers()
            execute<ProxyCommandSender> { sender, _, argument ->
                if (!sender.requirePermission("phcore.admin")) return@execute
                val viewer = sender.requirePlayer()?.cast<Player>() ?: return@execute
                val targetName = argument.toString().trim()
                val target = resolveOfflinePlayer(targetName) ?: run {
                    sender.msg("&c未找到玩家: $targetName")
                    return@execute
                }
                if (!SecurityService.openEnderChest(viewer, target)) {
                    sender.msg("&c打开玩家末影箱失败。")
                }
            }
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission("phcore.admin")) return@execute
            SecurityService.reload()
            sender.msg("&a安全模块配置已重载。")
        }
    }

}
