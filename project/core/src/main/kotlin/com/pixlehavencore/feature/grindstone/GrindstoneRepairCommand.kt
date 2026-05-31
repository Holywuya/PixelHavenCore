package com.pixlehavencore.feature.grindstone

import com.pixlehavencore.util.ADMIN_PERMISSION
import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePermission
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand

@CommandHeader(name = "grindstone", aliases = ["grindrepair"], permissionDefault = PermissionDefault.TRUE)
object GrindstoneRepairCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.msg("<gold>=== 砂轮修复命令帮助 ===")
            sender.msg("<aqua>/grindstone reload <gray>- 重载配置")
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
            GrindstoneRepairSettings.init()
            sender.msg("<green>砂轮修复配置已重载。")
        }
    }
}
