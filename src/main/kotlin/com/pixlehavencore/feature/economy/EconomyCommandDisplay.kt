package com.pixlehavencore.feature.economy

import com.pixlehavencore.util.msg
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.function.submit
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun ProxyCommandSender.showTaxStatus() {
    val status = TaxService.getStatusSnapshot()
    msg("&6=== 经济税务状态 ===")
    msg("&7模块状态: &f${if (status.enabled) "启用" else "禁用"}")
    msg("&7场景开关: &f菜单=${if (status.menuTradeEnabled) "启用" else "禁用"} &7指令=${if (status.commandTradeEnabled) "启用" else "禁用"} &7玩家=${if (status.playerTradeEnabled) "启用" else "禁用"}")
    msg("&7默认玩家税率: &f${formatRate(status.defaultPlayerTradeTaxRate)}")
    msg("&7阶梯税档数量: &f${status.bracketCount}")
    msg("&7当前收益池总额: &f${formatMoney(status.pendingIncome)}")
    msg("&7当前待缴税额: &f${formatMoney(status.pendingTax)}")
    msg("&7累计欠税总额: &f${formatMoney(status.pendingDebt)}")
    msg("&7持久化状态: &f准备=${if (status.storageReady) "启用" else "禁用"} &7初始化中=${if (status.storageInitializing) "启用" else "禁用"} &7脏数据=${if (status.storageDirty) "启用" else "禁用"}")
    msg("&7持久化错误: &f${if (status.storageLastError.isBlank()) "-" else status.storageLastError}")
    msg("&7定时结税: &f${if (status.settlementEnabled) "启用" else "禁用"} &7时间=${formatClock(status.settlementHour, status.settlementMinute)}")
    msg("&7定时检查间隔: &f${status.settlementCheckIntervalTicks} 刻")
    msg("&7收益池落库间隔: &f${status.poolPersistIntervalTicks} 刻")
    msg("&7下次结税倒计时: &f${formatCountdown(status.nextSettlementSeconds)}")
    msg("&7上次结税时间: &f${formatTimestamp(status.lastSettlementAtEpochMillis)}")
    msg("&7上次实收税额: &f${formatMoney(status.lastSettlementAmount)} &7（${formatSettlementReason(status.lastSettlementReason)}）")
    msg("&7上次结税后欠税: &f${formatMoney(status.lastSettlementOutstandingDebt)}")
}

internal fun ProxyCommandSender.settleTaxNow() {
    submit(async = true) {
        val result = TaxService.settleNow()
        submit {
            if (!result.success) {
                msg("&c统一结税失败: ${result.reason}")
                return@submit
            }
            msg("&a统一结税完成，实收税额: &f${formatMoney(result.settled)} &7欠税: &f${formatMoney(result.outstandingDebt)}")
        }
    }
}

internal fun ProxyCommandSender.showCentralBankStatus() {
    msg("&6=== 中心银行宏观面板 ===")
    msg("&7默认货币: &f${EconomySettings.defaultCurrency}")
    msg("&7储备账户余额: &f${formatMoney(CentralBankService.getReserveBalance())}")
    msg("&7执行账户余额: &f${formatMoney(CentralBankService.getExecutorBalance())}")
    msg("&7最大供给: &f${formatMoney(CentralBankService.getMaxSupply())}")
    msg("&7活跃流通量: &f${formatMoney(CentralBankService.getActiveM0())}")
    msg("&7活跃玩家数: &f${CentralBankService.getActivePlayerCount()}")
    msg("&7玩家总余额: &f${formatMoney(CentralBankService.getTotalPlayerBalance())}")
    msg("&7储备率: &f${CentralBankService.getReserveRate().stripTrailingZeros().toPlainString()}")
    msg("&7本期税额: &f${formatMoney(CentralBankService.getPeriodTaxCollected())}")
}

internal fun ProxyCommandSender.injectCentralBank(amount: BigDecimal) {
    val balance = CentralBankService.inject(amount)
    msg("&a已向中心银行注资 &f${formatMoney(amount)} &a，当前余额: &f${formatMoney(balance)}")
}

internal fun ProxyCommandSender.drainCentralBank(amount: BigDecimal) {
    val balance = CentralBankService.drain(amount)
    if (balance == null) {
        msg("&c中心银行余额不足，无法缩表。")
        return
    }
    msg("&a已从中心银行缩表 &f${formatMoney(amount)} &a，当前余额: &f${formatMoney(balance)}")
}

internal fun formatMoney(amount: BigDecimal): String {
    return EconomySettings.formatAmount(amount, EconomySettings.defaultCurrency)
}

internal fun formatRate(value: Double): String {
    val percent = BigDecimal.valueOf(value)
        .multiply(BigDecimal.valueOf(100))
        .setScale(2, RoundingMode.HALF_UP)
    return "${percent.toPlainString()}%"
}

internal fun formatClock(hour: Int, minute: Int): String {
    return "%02d:%02d".format(hour, minute)
}

internal fun formatCountdown(seconds: Long): String {
    if (seconds < 0L) {
        return "未启用"
    }
    val hour = seconds / 3600
    val minute = (seconds % 3600) / 60
    val second = seconds % 60
    return "%02d:%02d:%02d (%d 秒)".format(hour, minute, second, seconds)
}

internal fun formatTimestamp(epochMillis: Long): String {
    if (epochMillis <= 0L) {
        return "从未"
    }
    val time = Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
    return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
}

internal fun formatSettlementReason(reason: String): String {
    return when (reason.uppercase()) {
        "OK" -> "正常"
        "EMPTY" -> "无收益"
        "PARTIAL" -> "部分收取"
        "DISABLED" -> "未启用"
        else -> reason
    }
}
