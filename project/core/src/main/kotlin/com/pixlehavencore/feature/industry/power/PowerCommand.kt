package com.pixlehavencore.feature.industry.power

import com.pixlehavencore.util.ADMIN_PERMISSION
import com.pixlehavencore.util.msg
import com.pixlehavencore.util.msgRaw
import com.pixlehavencore.util.requirePermission
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand

@CommandHeader(name = "industry", permissionDefault = PermissionDefault.TRUE)
object PowerCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
            sender.msgRaw("<gold>=== 工业模块帮助 ===")
            sender.msgRaw("<aqua>/industry power info <gray>- 查看所有领地能量信息")
            sender.msgRaw("<aqua>/industry power reload <gray>- 重载电力模块配置")
        }
    }

    @CommandBody
    val power = subCommand {
        literal("info") {
            execute<ProxyCommandSender> { sender, _, _ ->
                if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                sender.msgRaw("<gold>=== 领地能量信息 ===")
                if (!PowerSettings.enabled) {
                    sender.msg("<red>电力模块未启用")
                    return@execute
                }
                val allPools = PowerService.getAllPools()
                if (allPools.isEmpty()) {
                    sender.msgRaw("<gray>暂无领地能量数据")
                } else {
                    allPools.forEach { (id, pool) ->
                        sender.msgRaw("<yellow>领地: <white>$id")
                        sender.msgRaw("  <gray>能量: <white>${String.format("%.1f", pool.energy)} / ${String.format("%.1f", pool.capacity)}")
                        sender.msgRaw("  <gray>发电机数量: <white>${pool.generators.size}")
                    }
                }
            }
        }

        literal("reload") {
            execute<ProxyCommandSender> { sender, _, _ ->
                if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                PowerService.reload()
                sender.msg("<green>电力模块已重载")
            }
        }
    }
}
