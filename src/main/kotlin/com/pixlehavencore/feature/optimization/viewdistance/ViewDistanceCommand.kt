package com.pixlehavencore.feature.optimization.viewdistance

import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePlayer
import org.bukkit.entity.Player
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand

@CommandHeader(name = "viewdistance", aliases = ["vd"], permission = "phcore.vdc.admin")
object ViewDistanceCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.msg("&6=== 视距控制命令帮助 ===")
            sender.msg("&b/vd get &7- 查看当前视距")
            sender.msg("&b/vd set <距离> &7- 设置视距")
            sender.msg("&b/vd reset &7- 重置为默认视距")
            sender.msg("&b/vd reload &7- 重载配置")
        }
    }

    @CommandBody
    val get = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            val player = sender.requirePlayer() ?: return@execute
            val distance = ViewDistanceService.resolvePlayerDistance(player)
            sender.msg("&a当前视距为 $distance。")
        }
    }

    @CommandBody
    val set = subCommand {
        dynamic(comment = "distance") {
            execute<ProxyCommandSender> { sender, _, argument ->
                val value = argument.toIntOrNull()
                if (value == null) {
                    sender.msg("&c无效的视距参数。")
                    return@execute
                }
                val player = sender.requirePlayer() ?: return@execute
                ViewDistanceService.setPlayerDistance(player, value)
                val bukkit = player.cast<Player>() ?: return@execute
                ViewDistanceService.applyDistance(bukkit, value)
                sender.msg("&a视距已设置为 ${ViewDistanceService.clampByLimits(value)}。")
            }
        }
    }

    @CommandBody
    val reset = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            val player = sender.requirePlayer() ?: return@execute
            ViewDistanceService.clearPlayerDistance(player)
            val bukkit = player.cast<Player>() ?: return@execute
            val target = ViewDistanceService.resolvePlayerDistance(player)
            ViewDistanceService.applyDistance(bukkit, target)
            sender.msg("&a视距已重置为 $target。")
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            ViewDistanceService.reload()
            sender.msg("&a视距控制配置已重载。")
        }
    }
}
