package com.pixlehavencore.feature.economy

import com.pixlehavencore.util.EconomyUtils
import com.pixlehavencore.util.ADMIN_PERMISSION
import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePermission
import com.pixlehavencore.util.requirePlayer
import com.pixlehavencore.util.resolveOfflinePlayer
import org.bukkit.OfflinePlayer
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.suggestPlayers
import taboolib.common.platform.command.subCommand
import java.math.BigDecimal

@CommandHeader(name = "economy", aliases = ["eco"], permissionDefault = PermissionDefault.TRUE)
object MoneyCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!EconomySettings.enabled || !EconomyUtils.isAvailable()) {
                sender.msg("&c经济系统当前不可用。")
                return@execute
            }
            val player = sender.requirePlayer() ?: return@execute
            val currency = EconomySettings.defaultCurrency
            val balance = EconomyUtils.getBalance(player.cast<org.bukkit.entity.Player>(), currency)
            sender.msg("&a当前余额: &f${formatMoney(balance)} &7(${EconomySettings.getDefinition(currency).plural})")
        }
    }

    @CommandBody
    val help = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.msg("&6=== 钱包命令帮助 ===")
            sender.msg("&b/economy &7- 查看自己的余额")
            sender.msg("&b/economy pay <玩家> <金额> [货币] &7- 转账给玩家，默认货币无需写币种")
            sender.msg("&b/economy balance <玩家> [货币] &7- 查看玩家余额")
            sender.msg("&b/economy add <玩家> <金额> [货币] &7- 增加玩家余额，默认货币无需写币种")
            sender.msg("&b/economy give <玩家> <金额> &7- 通过中心银行发放金额")
            sender.msg("&b/economy remove <玩家> <金额> [货币] &7- 扣除玩家余额，默认货币无需写币种")
            sender.msg("&b/economy set <玩家> <金额> [货币] &7- 设置玩家余额，默认货币无需写币种")
            sender.msg("&b/economy cbank view &7- 查看中心银行状态")
            sender.msg("&b/economy cbank inject <金额> &7- 向中心银行注资")
            sender.msg("&b/economy cbank drain <金额> &7- 从中心银行缩表")
            sender.msg("&b/economy tax status &7- 查看收益池与应缴税统计")
            sender.msg("&b/economy tax settle &7- 立即执行一次统一结税")
            sender.msg("&b/economy reload &7- 重载经济配置(管理员)")
        }
    }

    @CommandBody
    val pay = subCommand {
        dynamic(comment = "player") {
            suggestPlayers()
            dynamic(comment = "amount") {
                execute<ProxyCommandSender> { sender, context, _ ->
                    val from = sender.requirePlayer() ?: return@execute
                    val targetName = context.getOrNull("player") ?: return@execute
                    val target = resolveOfflinePlayer(targetName) ?: run {
                        sender.msg("&c未找到玩家: $targetName")
                        return@execute
                    }
                    if (target.uniqueId == from.uniqueId) {
                        sender.msg("&c不能给自己转账。")
                        return@execute
                    }
                    val amount = parsePositiveAmount(context.getOrNull("amount") ?: "") ?: run {
                        sender.msg("&c金额必须为大于 0 的数字。")
                        return@execute
                    }
                    transfer(sender, from.cast(), target, amount, EconomySettings.defaultCurrency)
                }

                dynamic(comment = "currency") {
                    execute<ProxyCommandSender> { sender, context, argument ->
                        val from = sender.requirePlayer() ?: return@execute
                        val targetName = context.getOrNull("player") ?: return@execute
                        val target = resolveOfflinePlayer(targetName) ?: run {
                            sender.msg("&c未找到玩家: $targetName")
                            return@execute
                        }
                        if (target.uniqueId == from.uniqueId) {
                            sender.msg("&c不能给自己转账。")
                            return@execute
                        }
                        val amount = parsePositiveAmount(context.getOrNull("amount") ?: "") ?: run {
                            sender.msg("&c金额必须为大于 0 的数字。")
                            return@execute
                        }
                        val currency = EconomySettings.resolveCurrency(argument.toString())
                        transfer(sender, from.cast(), target, amount, currency)
                    }
                }
            }
        }
    }

    @CommandBody
    val balance = subCommand {
        dynamic(comment = "player") {
            suggestPlayers()
            execute<ProxyCommandSender> { sender, context, _ ->
                if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                val targetName = context.getOrNull("player") ?: return@execute
                val target = resolveOfflinePlayer(targetName) ?: run {
                    sender.msg("&c未找到玩家: $targetName")
                    return@execute
                }
                val currency = EconomySettings.defaultCurrency
                val balance = EconomyUtils.getBalance(target, currency)
                sender.msg("&a${target.name ?: target.uniqueId} 余额: &f${formatMoney(balance)} &7(${EconomySettings.getDefinition(currency).plural})")
            }
            dynamic(comment = "currency") {
                execute<ProxyCommandSender> { sender, context, argument ->
                    if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                    val targetName = context.getOrNull("player") ?: return@execute
                    val target = resolveOfflinePlayer(targetName) ?: run {
                        sender.msg("&c未找到玩家: $targetName")
                        return@execute
                    }
                    val currency = EconomySettings.resolveCurrency(argument.toString())
                    val balance = EconomyUtils.getBalance(target, currency)
                    sender.msg("&a${target.name ?: target.uniqueId} 余额: &f${formatMoney(balance)} &7(${EconomySettings.getDefinition(currency).plural})")
                }
            }
        }
    }

    @CommandBody
    val add = subCommand {
        dynamic(comment = "player") {
            suggestPlayers()
            dynamic(comment = "amount") {
                execute<ProxyCommandSender> { sender, context, _ ->
                    if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                    mutateBalance(sender, context.getOrNull("player"), context.getOrNull("amount"), EconomySettings.defaultCurrency, Mode.ADD)
                }
                dynamic(comment = "currency") {
                    execute<ProxyCommandSender> { sender, context, argument ->
                        if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                        mutateBalance(sender, context.getOrNull("player"), context.getOrNull("amount"), argument.toString(), Mode.ADD)
                    }
                }
            }
        }
    }

    @CommandBody
    val give = subCommand {
        dynamic(comment = "player") {
            suggestPlayers()
            dynamic(comment = "amount") {
                execute<ProxyCommandSender> { sender, context, _ ->
                    if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                    giveFromCentralBank(sender, context.getOrNull("player"), context.getOrNull("amount"))
                }
            }
        }
    }

    @CommandBody
    val remove = subCommand {
        dynamic(comment = "player") {
            suggestPlayers()
            dynamic(comment = "amount") {
                execute<ProxyCommandSender> { sender, context, _ ->
                    if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                    mutateBalance(sender, context.getOrNull("player"), context.getOrNull("amount"), EconomySettings.defaultCurrency, Mode.REMOVE)
                }
                dynamic(comment = "currency") {
                    execute<ProxyCommandSender> { sender, context, argument ->
                        if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                        mutateBalance(sender, context.getOrNull("player"), context.getOrNull("amount"), argument.toString(), Mode.REMOVE)
                    }
                }
            }
        }
    }

    @CommandBody
    val set = subCommand {
        dynamic(comment = "player") {
            suggestPlayers()
            dynamic(comment = "amount") {
                execute<ProxyCommandSender> { sender, context, _ ->
                    if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                    mutateBalance(sender, context.getOrNull("player"), context.getOrNull("amount"), EconomySettings.defaultCurrency, Mode.SET)
                }
                dynamic(comment = "currency") {
                    execute<ProxyCommandSender> { sender, context, argument ->
                        if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                        mutateBalance(sender, context.getOrNull("player"), context.getOrNull("amount"), argument.toString(), Mode.SET)
                    }
                }
            }
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
            EconomyProvider.reload()
            sender.msg("&a经济系统配置已重载。")
        }
    }

    @CommandBody
    val cbank = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.msg("&6=== 经济央行命令帮助 ===")
            sender.msg("&b/economy cbank view &7- 查看中心银行状态")
            sender.msg("&b/economy cbank inject <金额> &7- 向中心银行注资")
            sender.msg("&b/economy cbank drain <金额> &7- 从中心银行缩表")
        }

        literal("view") {
            execute<ProxyCommandSender> { sender, _, _ ->
                if (!requireCentralBankAdmin(sender)) return@execute
                sender.showCentralBankStatus()
            }
        }

        literal("inject") {
            dynamic(comment = "amount") {
                execute<ProxyCommandSender> { sender, _, argument ->
                    if (!requireCentralBankAdmin(sender)) return@execute
                    withPositiveAmount(sender, argument) { amount ->
                        sender.injectCentralBank(amount)
                    }
                }
            }
        }

        literal("drain") {
            dynamic(comment = "amount") {
                execute<ProxyCommandSender> { sender, _, argument ->
                    if (!requireCentralBankAdmin(sender)) return@execute
                    withPositiveAmount(sender, argument) { amount ->
                        sender.drainCentralBank(amount)
                    }
                }
            }
        }
    }

    @CommandBody
    val tax = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.msg("&6=== 经济税务命令帮助 ===")
            sender.msg("&b/economy tax status &7- 查看收益池与应缴税统计")
            sender.msg("&b/economy tax settle &7- 立即执行一次统一结税")
            sender.msg("&b/economy tax reload &7- 重载税务配置")
        }

        literal("status") {
            execute<ProxyCommandSender> { sender, _, _ ->
                if (!requireTaxAdmin(sender)) return@execute
                sender.showTaxStatus()
            }
        }

        literal("settle") {
            execute<ProxyCommandSender> { sender, _, _ ->
                if (!requireTaxAdmin(sender)) return@execute
                sender.settleTaxNow()
            }
        }

        literal("reload") {
            execute<ProxyCommandSender> { sender, _, _ ->
                if (!requireTaxAdmin(sender)) return@execute
                TaxService.reload()
                sender.msg("&a税务配置已重载。")
            }
        }
    }

    private fun resolveTargetByName(sender: ProxyCommandSender, playerName: String?): OfflinePlayer? {
        val targetName = playerName?.trim().orEmpty()
        if (targetName.isBlank()) {
            sender.msg("&c玩家名不能为空。")
            return null
        }
        val target = resolveOfflinePlayer(targetName)
        if (target == null) {
            sender.msg("&c未找到玩家: $targetName")
        }
        return target
    }

    private fun mutateBalance(sender: ProxyCommandSender, playerName: String?, rawAmount: String?, rawCurrency: String, mode: Mode) {
        val target = resolveTargetByName(sender, playerName) ?: return
        val amount = when (mode) {
            Mode.SET -> parseNonNegativeAmount(rawAmount ?: "")
            else -> parsePositiveAmount(rawAmount ?: "")
        } ?: run {
            sender.msg("&c金额格式无效。")
            return
        }
        val currency = EconomySettings.resolveCurrency(rawCurrency)

        when (mode) {
            Mode.ADD -> {
                if (!EconomyUtils.depositInternal(target, amount, currency)) {
                    sender.msg("&c增加余额失败。")
                    return
                }
            }

            Mode.REMOVE -> {
                if (!EconomyUtils.has(target, amount, currency)) {
                    sender.msg("&c目标余额不足。")
                    return
                }
                if (!EconomyUtils.withdraw(target, amount, currency)) {
                    sender.msg("&c扣除余额失败。")
                    return
                }
            }

            Mode.SET -> {
                val current = EconomyUtils.getBalance(target, currency)
                if (current > BigDecimal.ZERO) {
                    EconomyUtils.withdraw(target, current, currency)
                }
                if (amount > BigDecimal.ZERO && !EconomyUtils.depositInternal(target, amount, currency)) {
                    sender.msg("&c设置余额失败。")
                    return
                }
            }
        }

        val finalBalance = EconomyUtils.getBalance(target, currency)
        sender.msg("&a已更新 &f${target.name ?: target.uniqueId} &a余额为 &f${formatMoney(finalBalance)} &7(${EconomySettings.getDefinition(currency).plural})")
    }

    private fun giveFromCentralBank(sender: ProxyCommandSender, playerName: String?, rawAmount: String?) {
        val target = resolveTargetByName(sender, playerName) ?: return
        val amount = parsePositiveAmount(rawAmount ?: "") ?: run {
            sender.msg("&c金额必须为大于 0 的数字。")
            return
        }
        val balance = CentralBankService.depositToPlayer(target.uniqueId, amount)
        if (balance == null) {
            sender.msg("&c中心银行余额不足，发放失败。")
            return
        }
        TaxService.recordGenericIncome(target.uniqueId, amount)
        sender.msg("&a已通过中心银行发放给 &f${target.name ?: target.uniqueId} &a金额 &f${formatMoney(amount)}")
    }

    private fun transfer(sender: ProxyCommandSender, from: org.bukkit.entity.Player, target: OfflinePlayer, amount: BigDecimal, currency: String) {
        if (!EconomyUtils.has(from, amount, currency)) {
            sender.msg("&c余额不足。")
            return
        }
        if (!EconomyUtils.withdraw(from, amount, currency)) {
            sender.msg("&c扣款失败，请稍后再试。")
            return
        }
        if (!EconomyUtils.deposit(target, amount, currency)) {
            EconomyUtils.depositInternal(from, amount, currency)
            sender.msg("&c入账失败，交易已回滚。")
            return
        }
        sender.msg("&a已向 &f${target.name ?: target.uniqueId} &a转账 &f${formatMoney(amount)} &7(${EconomySettings.getDefinition(currency).plural})")
        target.player?.sendMessage("&a你收到来自 &f${from.name} &a的转账 &f${formatMoney(amount)} &7(${EconomySettings.getDefinition(currency).plural})".replace('&', '§'))
    }

    private fun parsePositiveAmount(raw: String): BigDecimal? {
        val value = raw.trim().toBigDecimalOrNull() ?: return null
        if (value <= BigDecimal.ZERO) return null
        return value
    }

    private fun parseNonNegativeAmount(raw: String): BigDecimal? {
        val value = raw.trim().toBigDecimalOrNull() ?: return null
        if (value < BigDecimal.ZERO) return null
        return value
    }

    private fun withPositiveAmount(sender: ProxyCommandSender, argument: Any, handler: (BigDecimal) -> Unit) {
        val amount = argument.toString().trim().toBigDecimalOrNull()
        if (amount == null || amount <= BigDecimal.ZERO) {
            sender.msg("&c金额必须大于 0。")
            return
        }
        handler(amount)
    }

    private fun requireAdmin(sender: ProxyCommandSender, errorMsg: String): Boolean {
        if (sender.hasPermission(ADMIN_PERMISSION)) return true
        sender.msg(errorMsg)
        return false
    }

    private fun requireTaxAdmin(sender: ProxyCommandSender): Boolean {
        return requireAdmin(sender, "&c你没有权限执行该经济税务操作。")
    }

    private fun requireCentralBankAdmin(sender: ProxyCommandSender): Boolean {
        return requireAdmin(sender, "&c你没有权限执行该央行操作。")
    }

    private enum class Mode {
        ADD,
        REMOVE,
        SET,
    }
}
