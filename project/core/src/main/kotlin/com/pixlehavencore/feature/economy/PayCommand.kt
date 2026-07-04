package com.pixlehavencore.feature.economy

import com.pixlehavencore.util.EconomyUtils
import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePlayer
import com.pixlehavencore.util.resolveOfflinePlayer
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.suggestPlayers

@CommandHeader(name = "pay", permissionDefault = PermissionDefault.TRUE)
object PayCommand {

    @CommandBody
    val main = mainCommand {
        dynamic(comment = "player") {
            suggestPlayers()
            dynamic(comment = "amount") {
                execute<ProxyCommandSender> { sender, context, _ ->
                    val from = sender.requirePlayer() ?: return@execute
                    if (!EconomySettings.enabled || !EconomyUtils.isAvailable()) {
                        sender.msg("<red>经济系统当前不可用。")
                        return@execute
                    }
                    val targetName = context.getOrNull("player") ?: return@execute
                    val target = resolveOfflinePlayer(targetName) ?: run {
                        sender.msg("<red>未找到玩家: $targetName")
                        return@execute
                    }
                    if (target.uniqueId == from.uniqueId) {
                        sender.msg("<red>不能给自己转账。")
                        return@execute
                    }
                    val amount = MoneyCommand.parseAmount(context.getOrNull("amount") ?: "") ?: run {
                        sender.msg("<red>金额必须为大于 0 的数字。")
                        return@execute
                    }
                    MoneyCommand.transfer(sender, from.cast(), target, amount, EconomySettings.defaultCurrency)
                }

                dynamic(comment = "currency") {
                    execute<ProxyCommandSender> { sender, context, argument ->
                        val from = sender.requirePlayer() ?: return@execute
                        if (!EconomySettings.enabled || !EconomyUtils.isAvailable()) {
                            sender.msg("<red>经济系统当前不可用。")
                            return@execute
                        }
                        val targetName = context.getOrNull("player") ?: return@execute
                        val target = resolveOfflinePlayer(targetName) ?: run {
                            sender.msg("<red>未找到玩家: $targetName")
                            return@execute
                        }
                        if (target.uniqueId == from.uniqueId) {
                            sender.msg("<red>不能给自己转账。")
                            return@execute
                        }
                        val amount = MoneyCommand.parseAmount(context.getOrNull("amount") ?: "") ?: run {
                            sender.msg("<red>金额必须为大于 0 的数字。")
                            return@execute
                        }
                        val currency = EconomySettings.resolveCurrency(argument.toString())
                        MoneyCommand.transfer(sender, from.cast(), target, amount, currency)
                    }
                }
            }
        }
    }
}
