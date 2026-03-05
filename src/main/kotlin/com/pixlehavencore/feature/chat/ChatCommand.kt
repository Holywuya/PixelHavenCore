package com.pixlehavencore.feature.chat

import com.pixlehavencore.PixleHavenSettings
import com.pixlehavencore.feature.grindstone.GrindstoneRepairSettings
import com.pixlehavencore.feature.notification.NotificationService
import com.pixlehavencore.feature.optimization.viewdistance.ViewDistanceService
import com.pixlehavencore.feature.veinminer.VeinminerMessages
import com.pixlehavencore.feature.veinminer.VeinminerSettings
import com.pixlehavencore.feature.vanish.VanishSettings
import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePermission
import com.pixlehavencore.util.requirePlayer
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand

@CommandHeader(name = "phcore", aliases = ["phc"])
object ChatCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.msg("&6=== PixleHavenCore 命令帮助 ===")
            sender.msg("&b/phcore mention &7- 切换@提及接收")
            sender.msg("&b/phcore reload &7- 重载所有模块配置")
            sender.msg("&b/phcore chat reload &7- 重载聊天模块配置")
            sender.msg("&b/veinminer reload &7- 重载连锁挖掘配置")
            sender.msg("&b/grindstone reload &7- 重载砂轮修复配置")
            sender.msg("&b/notification reload &7- 重载服务器通知配置")
            sender.msg("&b/viewdistance reload &7- 重载视距控制配置")
            sender.msg("&b/vanish reload &7- 重载隐身模块配置")
            sender.msg("&b/menu &7- 打开菜单")
        }
    }

    @CommandBody
    val mention = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            val player = sender.requirePlayer() ?: return@execute
            val optOut = ChatMentionStorage.toggleOptOut(player)
            if (optOut) {
                VeinminerMessages.send(player, ChatSettings.mentionToggleOffMessage)
            } else {
                VeinminerMessages.send(player, ChatSettings.mentionToggleOnMessage)
            }
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission("phcore.admin")) return@execute
            PixleHavenSettings.reload()
            VeinminerSettings.reload()
            GrindstoneRepairSettings.reload()
            ChatSettings.reload()
            NotificationService.reload()
            ViewDistanceService.reload()
            VanishSettings.reload()
            sender.msg("&a已重载所有模块配置。")
        }
    }

    @CommandBody
    val chat = subCommand {
        literal("reload") {
            execute<ProxyCommandSender> { sender, _, _ ->
                if (!sender.requirePermission("phcore.chat.admin")) return@execute
                ChatSettings.reload()
                sender.msg("&a聊天模块配置已重载。")
            }
        }
    }
}
