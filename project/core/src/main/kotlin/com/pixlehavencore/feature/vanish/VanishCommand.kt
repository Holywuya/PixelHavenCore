package com.pixlehavencore.feature.vanish

import com.pixlehavencore.util.PlaceholderUtils.resolvePlaceholders
import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePermission
import com.pixlehavencore.util.requirePlayer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.suggestPlayers
import taboolib.common.platform.command.subCommand

// ---------------------------------------------------------------------------
// /vanish — 普通隐身切换
// 权限: phcore.vanish
// ---------------------------------------------------------------------------
@CommandHeader(name = "vanish", aliases = ["v"], permissionDefault = PermissionDefault.TRUE)
object VanishCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!VanishSettings.enabled) {
                sender.msg("&c隐身模块当前已禁用。")
                return@execute
            }
            if (!sender.requirePermission("phcore.vanish")) return@execute
            val player = sender.requirePlayer() ?: return@execute
            val nowVanished = VanishService.toggleNormalVanish(player.cast<Player>())
            if (nowVanished) {
                player.msg(VanishSettings.msgVanishOn)
            } else {
                player.msg(VanishSettings.msgVanishOff)
            }
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission("phcore.admin")) return@execute
            VanishSettings.init()
            sender.msg("&a隐身模块配置已重载。")
        }
    }
}

// ---------------------------------------------------------------------------
// /vanish-list — 列出当前普通隐身玩家
// 权限: phcore.vanish.admin
// ---------------------------------------------------------------------------
@CommandHeader(name = "vanish-list", aliases = ["vlist"], permissionDefault = PermissionDefault.TRUE)
object VanishListCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!VanishSettings.enabled) {
                sender.msg("&c隐身模块当前已禁用。")
                return@execute
            }
            if (!sender.requirePermission("phcore.admin")) return@execute
            val vanished = VanishService.getNormalVanishedPlayers()
            if (vanished.isEmpty()) {
                sender.msg(VanishSettings.msgNoVanishedPlayers)
                return@execute
            }
            sender.msg("&8[隐身] &7当前普通隐身玩家（${vanished.size} 人）：")
            vanished.forEach { p ->
                sender.msg("  &7- &f${p.name}")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// /vanish-show <player|--all> — 让自己看见指定隐身玩家（或全部普通隐身玩家）
// 权限: phcore.vanish.admin
// ---------------------------------------------------------------------------
@CommandHeader(name = "vanish-show", aliases = ["vshow"], permissionDefault = PermissionDefault.TRUE)
object VanishShowCommand {

    @CommandBody
    val main = mainCommand {
        dynamic(comment = "player") {
            suggestPlayers(listOf("--all"))
        }
        execute<ProxyCommandSender> { sender, _, argument ->
            if (!VanishSettings.enabled) {
                sender.msg("&c隐身模块当前已禁用。")
                return@execute
            }
            if (!sender.requirePermission("phcore.admin")) return@execute
            val player = sender.requirePlayer() ?: return@execute
            val observer = player.cast<Player>()
            val arg = argument.toString().trim()

            if (arg.equals("--all", ignoreCase = true)) {
                val count = VanishService.showAllNormalVanishedTo(observer)
                if (count == 0) {
                    player.msg(VanishSettings.msgNoVanishedPlayers)
                } else {
                    player.msg(VanishSettings.msgShowAll)
                }
                return@execute
            }

            if (arg.isBlank()) {
                player.msg("&c用法: /vanish-show <玩家名> 或 /vanish-show --all")
                return@execute
            }

            val target = Bukkit.getPlayer(arg)
            if (target == null) {
                player.msg(VanishSettings.msgPlayerNotFound.resolvePlaceholders("{player}" to arg))
                return@execute
            }

            when (VanishService.showPlayerTo(observer, target)) {
                VanishService.ShowResult.OK -> {
                    player.msg(VanishSettings.msgShowPlayer.resolvePlaceholders("{player}" to target.name))
                }
                VanishService.ShowResult.NOT_VANISHED -> {
                    player.msg("&7${target.name} 当前没有隐身。")
                }
            }
        }
    }
}
