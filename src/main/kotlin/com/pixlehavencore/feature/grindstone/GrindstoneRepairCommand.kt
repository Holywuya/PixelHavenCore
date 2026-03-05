package com.pixlehavencore.feature.grindstone

import com.pixlehavencore.util.msg
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand

@CommandHeader(name = "grindstone", aliases = ["grindrepair"], permission = "phcore.grindstone.admin")
object GrindstoneRepairCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.msg("&6=== 砂轮修复命令帮助 ===")
            sender.msg("&b/grindstone reload &7- 重载配置")
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            GrindstoneRepairSettings.reload()
            sender.msg("&a砂轮修复配置已重载。")
        }
    }
}
