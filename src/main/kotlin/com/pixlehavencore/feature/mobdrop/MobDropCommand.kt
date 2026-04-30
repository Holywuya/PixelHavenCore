package com.pixlehavencore.feature.mobdrop

import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePermission
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.subCommand


@CommandHeader(name = "mobdrop", aliases = ["md"], permissionDefault = PermissionDefault.TRUE)
object MobDropCommand {

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission("phcore.mobdrop.admin")) return@execute
            MobDropSettings.init()
            sender.msg("&a怪物自定义掉落模块配置已重载。")
        }
    }
}
