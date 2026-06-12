package com.pixlehavencore.feature.playerinfo

import com.pixlehavencore.util.ADMIN_PERMISSION
import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePermission
import com.pixlehavencore.util.requirePlayer
import com.pixlehavencore.util.resolveOfflinePlayer
import org.bukkit.entity.Player
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.suggestPlayers

@CommandHeader(name = "playerinfo", aliases = ["pi"], permissionDefault = PermissionDefault.TRUE)
object PlayerInfoCommand {

    @CommandBody
    val main = mainCommand {
        dynamic(comment = "player") {
            suggestPlayers()
            execute<ProxyCommandSender> { sender, _, argument ->
                if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                val viewer = sender.requirePlayer()?.cast<Player>() ?: return@execute
                val targetName = argument.toString().trim()
                val target = resolveOfflinePlayer(targetName) ?: run {
                    sender.msg("<red>未找到玩家: $targetName")
                    return@execute
                }
                PlayerInfoService.openDashboard(viewer, target)
            }
        }
    }
}
