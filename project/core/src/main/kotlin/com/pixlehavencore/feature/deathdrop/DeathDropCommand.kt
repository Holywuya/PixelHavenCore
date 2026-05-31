package com.pixlehavencore.feature.deathdrop

import com.pixlehavencore.util.ADMIN_PERMISSION
import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePermission
import com.pixlehavencore.util.resolveOfflinePlayer
import org.bukkit.OfflinePlayer
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.suggestPlayers
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand

@CommandHeader(name = "deathdrop", aliases = ["ddrop"], permissionDefault = PermissionDefault.TRUE)
object DeathDropCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.msg("<gold>=== 死亡掉落命令帮助 ===")
            sender.msg("<aqua>/deathdrop reload <gray>- 重载配置")
            sender.msg("<aqua>/deathdrop add <玩家> <次数> <gray>- 增加今日免掉落次数")
            sender.msg("<aqua>/deathdrop set <玩家> <次数> <gray>- 设置今日剩余免掉落次数")
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
            DeathDropSettings.init()
            DeathDropUsageStorage.init()
            sender.msg("<green>死亡掉落配置已重载。")
        }
    }

    @CommandBody
    val add = subCommand {
        dynamic(comment = "player") {
            suggestPlayers()
            dynamic(comment = "count") {
                execute<ProxyCommandSender> { sender, context, argument ->
                    if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                    val targetName = context.getOrNull("player") ?: return@execute
                    val target = resolveOfflinePlayer(targetName) ?: run {
                        sender.msg("<red>未找到玩家: $targetName")
                        return@execute
                    }
                    val count = argument.toString().trim().toIntOrNull() ?: run {
                        sender.msg("<red>次数必须为整数。")
                        return@execute
                    }
                    val bonus = DeathDropUsageStorage.addBonusToday(target.uniqueId, count)
                    val remaining = (DeathDropSettings.dailyKeepCount + bonus - DeathDropUsageStorage.getUsedToday(target.uniqueId)).coerceAtLeast(0)
                    sender.msg("<green>已为玩家 <white>${target.name ?: target.uniqueId} <green>增加今日免掉落次数 <white$count<green>，当前剩余 <white$remaining<green> 次。")
                }
            }
        }
    }

    @CommandBody
    val set = subCommand {
        dynamic(comment = "player") {
            suggestPlayers()
            dynamic(comment = "count") {
                execute<ProxyCommandSender> { sender, context, argument ->
                    if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                    val targetName = context.getOrNull("player") ?: return@execute
                    val target = resolveOfflinePlayer(targetName) ?: run {
                        sender.msg("<red>未找到玩家: $targetName")
                        return@execute
                    }
                    val remainingTarget = argument.toString().trim().toIntOrNull() ?: run {
                        sender.msg("<red>次数必须为整数。")
                        return@execute
                    }
                    val used = DeathDropUsageStorage.getUsedToday(target.uniqueId)
                    val bonus = (remainingTarget + used - DeathDropSettings.dailyKeepCount).coerceAtLeast(0)
                    DeathDropUsageStorage.setBonusToday(target.uniqueId, bonus)
                    val remaining = (DeathDropSettings.dailyKeepCount + bonus - used).coerceAtLeast(0)
                    sender.msg("<green>已设置玩家 <white>${target.name ?: target.uniqueId} <green>今日剩余免掉落次数为 <white$remaining<green> 次。")
                }
            }
        }
    }

}
