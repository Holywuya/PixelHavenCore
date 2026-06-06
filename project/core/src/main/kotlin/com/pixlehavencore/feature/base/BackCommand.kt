package com.pixlehavencore.feature.base

import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePlayer
import org.bukkit.entity.Player
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand

@CommandHeader(name = "back", permissionDefault = PermissionDefault.TRUE)
object BackCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!BaseCommandSettings.enabled) {
                sender.msg("&c基础模块已禁用。")
                return@execute
            }
            val proxyPlayer = sender.requirePlayer() ?: return@execute
            val bukkitPlayer = proxyPlayer.cast<Player>() ?: run {
                sender.msg("&c只有玩家可以使用此命令。")
                return@execute
            }
            BackService.teleportBack(bukkitPlayer)
        }
    }
}
