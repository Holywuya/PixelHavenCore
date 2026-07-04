package com.pixlehavencore.feature.economy

import com.pixlehavencore.bridge.TextBridge
import com.pixlehavencore.util.EconomyUtils
import com.pixlehavencore.util.ADMIN_PERMISSION
import com.pixlehavencore.util.TextUtils
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
                sender.msg("<red>经济系统当前不可用。")
                return@execute
            }
            val player = sender.requirePlayer() ?: return@execute
            val currency = EconomySettings.defaultCurrency
            val balance = EconomyUtils.getBalance(player.cast<org.bukkit.entity.Player>(), currency)
            sender.msg("<green>当前余额: <white>${formatMoney(balance)} <gray>(${EconomySettings.getDefinition(currency).plural})")
        }
    }

    @CommandBody
    val help = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.msg("<gold>=== 钱包命令帮助 ===")
            sender.msg("<aqua>/economy <gray>- 查看自己的余额")
            sender.msg("<aqua>/economy pay <玩家> <金额> [货币] <gray>- 转账给玩家，默认货币无需写币种")
            sender.msg("<aqua>/economy balance <玩家> [货币] <gray>- 查看玩家余额")
            sender.msg("<aqua>/economy add <玩家> <金额> [货币] <gray>- 增加玩家余额，默认货币无需写币种")
            sender.msg("<aqua>/economy give <玩家> <金额> <gray>- 通过中心银行发放金额")
            sender.msg("<aqua>/economy remove <玩家> <金额> [货币] <gray>- 扣除玩家余额，默认货币无需写币种")
            sender.msg("<aqua>/economy set <玩家> <金额> [货币] <gray>- 设置玩家余额，默认货币无需写币种")
            sender.msg("<aqua>/economy cbank view <gray>- 查看中心银行状态")
            sender.msg("<aqua>/economy cbank inject <金额> <gray>- 向中心银行注资")
            sender.msg("<aqua>/economy cbank drain <金额> <gray>- 从中心银行缩表")
            sender.msg("<aqua>/economy tax status <gray>- 查看收益池与应缴税统计")
            sender.msg("<aqua>/economy tax settle <gray>- 立即执行一次统一结税")
            sender.msg("<aqua>/economy reload <gray>- 重载经济配置(管理员)")
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
                        sender.msg("<red>未找到玩家: $targetName")
                        return@execute
                    }
                    if (target.uniqueId == from.uniqueId) {
                        sender.msg("<red>不能给自己转账。")
                        return@execute
                    }
                    val amount = parseAmount(context.getOrNull("amount") ?: "") ?: run {
                        sender.msg("<red>金额必须为大于 0 的数字。")
                        return@execute
                    }
                    transfer(sender, from.cast(), target, amount, EconomySettings.defaultCurrency)
                }

                dynamic(comment = "currency") {
                    execute<ProxyCommandSender> { sender, context, argument ->
                        val from = sender.requirePlayer() ?: return@execute
                        val targetName = context.getOrNull("player") ?: return@execute
                        val target = resolveOfflinePlayer(targetName) ?: run {
                            sender.msg("<red>未找到玩家: $targetName")
                            return@execute
                        }
                        if (target.uniqueId == from.uniqueId) {
                            sender.msg("<red>不能给自己转账。")
                            return@execute
                        }
                        val amount = parseAmount(context.getOrNull("amount") ?: "") ?: run {
                            sender.msg("<red>金额必须为大于 0 的数字。")
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
                    sender.msg("<red>未找到玩家: $targetName")
                    return@execute
                }
                val currency = EconomySettings.defaultCurrency
                val balance = EconomyUtils.getBalance(target, currency)
                sender.msg("<green>${target.name ?: target.uniqueId} 余额: <white>${formatMoney(balance)} <gray>(${EconomySettings.getDefinition(currency).plural})")
            }
            dynamic(comment = "currency") {
                execute<ProxyCommandSender> { sender, context, argument ->
                    if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                    val targetName = context.getOrNull("player") ?: return@execute
                    val target = resolveOfflinePlayer(targetName) ?: run {
                        sender.msg("<red>未找到玩家: $targetName")
                        return@execute
                    }
                    val currency = EconomySettings.resolveCurrency(argument.toString())
                    val balance = EconomyUtils.getBalance(target, currency)
                    sender.msg("<green>${target.name ?: target.uniqueId} 余额: <white>${formatMoney(balance)} <gray>(${EconomySettings.getDefinition(currency).plural})")
                }
            }
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
            EconomyProvider.reload()
            sender.msg("<green>经济系统配置已重载。")
        }
    }

    @CommandBody
    val cbank = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.msg("<gold>=== 经济央行命令帮助 ===")
            sender.msg("<aqua>/economy cbank view <gray>- 查看中心银行状态")
            sender.msg("<aqua>/economy cbank inject <金额> <gray>- 向中心银行注资")
            sender.msg("<aqua>/economy cbank drain <金额> <gray>- 从中心银行缩表")
        }

        literal("view") {
            execute<ProxyCommandSender> { sender, _, _ ->
                if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                sender.showCentralBankStatus()
            }
        }

        literal("inject") {
            dynamic(comment = "amount") {
                execute<ProxyCommandSender> { sender, context, _ ->
                    if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                    val amount = parseAmount(context.getOrNull("amount") ?: "", false) ?: run {
                        sender.msg("<red>金额格式无效。")
                        return@execute
                    }
                    sender.injectCentralBank(amount)
                }
            }
        }

        literal("drain") {
            dynamic(comment = "amount") {
                execute<ProxyCommandSender> { sender, context, _ ->
                    if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                    val amount = parseAmount(context.getOrNull("amount") ?: "", false) ?: run {
                        sender.msg("<red>金额格式无效。")
                        return@execute
                    }
                    sender.drainCentralBank(amount)
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
    val tax = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.msg("<gold>=== 经济税务命令帮助 ===")
            sender.msg("<aqua>/economy tax status <gray>- 查看收益池与应缴税统计")
            sender.msg("<aqua>/economy tax settle <gray>- 立即执行一次统一结税")
            sender.msg("<aqua>/economy tax reload <gray>- 重载税务配置")
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
                sender.msg("<green>税务配置已重载。")
            }
        }
    }

    private fun resolveTargetByName(sender: ProxyCommandSender, playerName: String?): OfflinePlayer? {
        val targetName = playerName?.trim().orEmpty()
        if (targetName.isBlank()) {
            sender.msg("<red>玩家名不能为空。")
            return null
        }
        val target = resolveOfflinePlayer(targetName)
        if (target == null) {
            sender.msg("<red>未找到玩家: $targetName")
        }
        return target
    }

    private fun mutateBalance(sender: ProxyCommandSender, playerName: String?, rawAmount: String?, rawCurrency: String, mode: Mode) {
        val target = resolveTargetByName(sender, playerName) ?: return
        val amount = parseAmount(rawAmount ?: "", allowZero = mode == Mode.SET) ?: run {
            sender.msg("<red>金额格式无效。")
            return
        }
        val currency = EconomySettings.resolveCurrency(rawCurrency)

        when (mode) {
            Mode.ADD -> {
                if (!EconomyUtils.canDeposit(target, amount, currency)) {
                    sender.msg("<red>目标无法接收存款（央行储备不足）。")
                    return
                }
                if (!EconomyUtils.depositInternal(target, amount, currency)) {
                    sender.msg("<red>增加余额失败。")
                    return
                }
            }

            Mode.REMOVE -> {
                if (!EconomyUtils.canWithdraw(target, amount, currency)) {
                    sender.msg("<red>目标余额不足或央行储备不足。")
                    return
                }
                if (!EconomyUtils.withdraw(target, amount, currency)) {
                    sender.msg("<red>扣除余额失败。")
                    return
                }
            }

            Mode.SET -> {
                val resp = EconomyUtils.setBalance(target, amount, currency)
                if (!resp.transactionSuccess()) {
                    sender.msg("<red>设置余额失败: ${resp.errorMessage}")
                    return
                }
            }
        }

        val finalBalance = EconomyUtils.getBalance(target, currency)
        sender.msg("<green>已更新 <white>${target.name ?: target.uniqueId} <green>余额为 <white>${formatMoney(finalBalance)} <gray>(${EconomySettings.getDefinition(currency).plural})")
    }

    private fun giveFromCentralBank(sender: ProxyCommandSender, playerName: String?, rawAmount: String?) {
        val target = resolveTargetByName(sender, playerName) ?: return
        val amount = parseAmount(rawAmount ?: "") ?: run {
            sender.msg("<red>金额必须为大于 0 的数字。")
            return
        }
        val balance = CentralBankService.depositToPlayer(target.uniqueId, amount)
        if (balance == null) {
            sender.msg("<red>中心银行余额不足，发放失败。")
            return
        }
        TaxService.recordGenericIncome(target.uniqueId, amount)
        sender.msg("<green>已通过中心银行发放给 <white>${target.name ?: target.uniqueId} <green>金额 <white>${formatMoney(amount)}")
    }

    internal fun transfer(sender: ProxyCommandSender, from: org.bukkit.entity.Player, target: OfflinePlayer, amount: BigDecimal, currency: String) {
        val resp = EconomyUtils.transfer(from, target, amount, currency)
        if (resp.type != net.milkbowl.vault2.economy.EconomyResponse.ResponseType.SUCCESS) {
            sender.msg("<red>转账失败: ${resp.errorMessage}")
            return
        }
        sender.msg("<green>已向 <white>${target.name ?: target.uniqueId} <green>转账 <white>${formatMoney(amount)} <gray>(${EconomySettings.getDefinition(currency).plural})")
        target.player?.sendMessage(TextBridge.toLegacy(TextUtils.parseMiniMessage("<green>你收到来自 <white>${from.name} <green>的转账 <white>${formatMoney(amount)} <gray>(${EconomySettings.getDefinition(currency).plural})")))
    }

    internal fun parseAmount(raw: String, allowZero: Boolean = false): BigDecimal? {
        val value = raw.trim().toBigDecimalOrNull() ?: return null
        if (allowZero) return if (value < BigDecimal.ZERO) null else value
        return if (value <= BigDecimal.ZERO) null else value
    }

    private fun requireAdmin(sender: ProxyCommandSender, errorMsg: String): Boolean {
        if (sender.hasPermission(ADMIN_PERMISSION)) return true
        sender.msg(errorMsg)
        return false
    }

    private fun requireTaxAdmin(sender: ProxyCommandSender): Boolean {
        return requireAdmin(sender, "<red>你没有权限执行该经济税务操作。")
    }

    private enum class Mode {
        ADD,
        REMOVE,
        SET,
    }
}
