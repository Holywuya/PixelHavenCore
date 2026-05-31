package com.pixlehavencore.feature.veinminer

import com.pixlehavencore.bridge.TextBridge
import com.pixlehavencore.util.ADMIN_PERMISSION
import com.pixlehavencore.util.TextUtils
import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePermission
import com.pixlehavencore.util.requirePlayer
import org.bukkit.Bukkit
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.suggestPlayers
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.function.onlinePlayers

@CommandHeader(name = "veinminer", aliases = ["vm"], permissionDefault = PermissionDefault.TRUE)
object VeinminerCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.msg("<gold>=== 连锁挖矿帮助 ===")
            sender.msg("<aqua>/veinminer toggle <gray>- 切换连锁挖矿开关")
            sender.msg("<aqua>/veinminer reload <gray>- 重载连锁挖矿配置")
            sender.msg("<aqua>/veinminer limit <gray>- 查看自己的次数信息")
            sender.msg("<aqua>/veinminer add <玩家> <次数> <gray>- 增加剩余次数")
            sender.msg("<aqua>/veinminer remove <玩家> <次数> <gray>- 减少剩余次数")
            sender.msg("<aqua>/veinminer set <玩家> <次数> <gray>- 设置剩余次数")
            sender.msg("<gray>当前状态：<white>${if (VeinminerSettings.enabled) "已启用" else "未启用"}")
        }
    }

    @CommandBody
    val toggle = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
            val newState = !VeinminerSettings.enabled
            VeinminerSettings.toggle(newState)
            if (newState) {
                val remaining = if (sender is taboolib.common.platform.ProxyPlayer) {
                    VeinminerLimitService.getRemaining(sender)
                } else {
                    0
                }
                VeinminerMessages.send(sender, VeinminerSettings.messageModeOn, mapOf("remaining" to remaining))
            } else {
                VeinminerMessages.send(sender, VeinminerSettings.messageModeOff)
            }
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
            VeinminerSettings.init()
            sender.msg("<green>连锁挖掘配置已重载。")
        }
    }

    @CommandBody
    val limit = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            val player = sender.requirePlayer("<red>只有玩家可以使用该命令。") ?: return@execute
            val remaining = VeinminerLimitService.getRemaining(player)
            val limit = VeinminerLimitService.getLimitValue(player)
            val used = VeinminerLimitService.getUsed(player)
            VeinminerMessages.send(player, VeinminerSettings.messageLimitCommand, mapOf("remaining" to remaining, "limit" to limit))
            player.sendMessage(TextBridge.toLegacy(TextUtils.parseMiniMessage("<gray>已使用: <white>$used</white></gray>")))
        }
    }

    @CommandBody
    val add = subCommand {
        dynamic(comment = "player") {
            suggestPlayers()
            dynamic(comment = "amount") {
                execute<ProxyCommandSender> { sender, context, argument ->
                    handleRemainingMutation(sender, context.getOrNull("player")?.toString(), argument.toString(), Mutation.ADD)
                }
            }
        }
    }

    @CommandBody
    val remove = subCommand {
        dynamic(comment = "player") {
            suggestPlayers()
            dynamic(comment = "amount") {
                execute<ProxyCommandSender> { sender, context, argument ->
                    handleRemainingMutation(sender, context.getOrNull("player")?.toString(), argument.toString(), Mutation.REMOVE)
                }
            }
        }
    }

    @CommandBody
    val set = subCommand {
        dynamic(comment = "player") {
            suggestPlayers()
            dynamic(comment = "amount") {
                execute<ProxyCommandSender> { sender, context, argument ->
                    handleRemainingMutation(sender, context.getOrNull("player")?.toString(), argument.toString(), Mutation.SET)
                }
            }
        }
    }

    private fun handleRemainingMutation(sender: ProxyCommandSender, targetName: String?, amountText: String, mutation: Mutation) {
        if (!sender.requirePermission(ADMIN_PERMISSION)) return
        val cleanTarget = targetName?.trim().orEmpty()
        if (cleanTarget.isBlank()) {
            sender.msg("<red>请输入目标玩家。")
            return
        }
        val target = Bukkit.getPlayerExact(cleanTarget) ?: run {
            sender.msg("<red>玩家不存在或不在线: $cleanTarget")
            return
        }
        val proxyTarget = onlinePlayers().firstOrNull { it.uniqueId == target.uniqueId } ?: run {
            sender.msg("<red>无法解析目标玩家代理实例: $cleanTarget")
            return
        }
        val amount = amountText.trim().toIntOrNull()
        if (amount == null || amount < 0) {
            sender.msg("<red>请输入有效的非负整数次数。")
            return
        }
        val result = when (mutation) {
            Mutation.ADD -> VeinminerLimitService.addRemaining(proxyTarget, amount)
            Mutation.REMOVE -> VeinminerLimitService.removeRemaining(proxyTarget, amount)
            Mutation.SET -> VeinminerLimitService.setRemaining(proxyTarget, amount)
        }
        if (result == null) {
            sender.msg("<red>该玩家当前没有可用的次数上限配置。")
            return
        }
        val limit = VeinminerLimitService.getLimitValue(proxyTarget)
        val used = VeinminerLimitService.getUsed(proxyTarget)
        val actionText = when (mutation) {
            Mutation.ADD -> "增加"
            Mutation.REMOVE -> "减少"
            Mutation.SET -> "设置"
        }
        sender.msg("<green>已为 <white>${target.name}</white> <green>${actionText}次数，当前剩余 <white>$result</white> <gray>/ <white>$limit</white> <green>，已使用 <white>$used</white></green>。")
        target.sendMessage(TextUtils.parseMiniMessage("<green>你的连锁挖矿剩余次数已被管理员调整为 <white>$result</white> <gray>/ <white>$limit</white></green>。"))
    }

    private enum class Mutation {
        ADD,
        REMOVE,
        SET,
    }
}
