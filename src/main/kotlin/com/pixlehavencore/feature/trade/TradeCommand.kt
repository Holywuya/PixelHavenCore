package com.pixlehavencore.feature.trade

import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePermission
import com.pixlehavencore.util.requirePlayer
import org.bukkit.Bukkit
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.suggestPlayers
import taboolib.common.platform.command.subCommand

@CommandHeader(name = "trade", aliases = ["faceTrade"], permissionDefault = PermissionDefault.TRUE)
object TradeCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.msg("&6=== 面对面交易命令帮助 ===")
            sender.msg("&b/trade request <玩家> &7- 向玩家发起交易请求")
            sender.msg("&b/trade accept <玩家> &7- 接受交易请求")
            sender.msg("&b/trade deny <玩家> &7- 拒绝交易请求")
            sender.msg("&bShift+右键玩家 &7- 直接发送交易请求")
            sender.msg("&b/trade reload &7- 重载交易模块配置")
        }
    }

    @CommandBody
    val request = subCommand {
        dynamic(comment = "player") {
            suggestPlayers()
            execute<ProxyCommandSender> { sender, _, argument ->
                val player = sender.requirePlayer() ?: return@execute
                val target = Bukkit.getPlayerExact(argument.toString().trim())
                if (target == null) {
                    sender.msg("&c目标玩家不在线。")
                    return@execute
                }
                TradeService.requestTrade(player.cast(), target)
            }
        }
    }

    @CommandBody
    val accept = subCommand {
        dynamic(comment = "player") {
            suggestPlayers()
            execute<ProxyCommandSender> { sender, _, argument ->
                val player = sender.requirePlayer() ?: return@execute
                if (!TradeService.acceptTrade(player.cast(), argument.toString().trim())) {
                    sender.msg(TradeSettings.requestNoActiveMessage)
                }
            }
        }
    }

    @CommandBody
    val deny = subCommand {
        dynamic(comment = "player") {
            suggestPlayers()
            execute<ProxyCommandSender> { sender, _, argument ->
                val player = sender.requirePlayer() ?: return@execute
                if (!TradeService.denyTrade(player.cast(), argument.toString().trim())) {
                    sender.msg(TradeSettings.requestNoActiveMessage)
                }
            }
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission("phcore.admin")) return@execute
            TradeService.reload()
            sender.msg("&a交易模块配置已重载。")
        }
    }
}
