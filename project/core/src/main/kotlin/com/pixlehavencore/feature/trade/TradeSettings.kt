package com.pixlehavencore.feature.trade

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object TradeSettings {

    @Config("feature/face-trade.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    var requestTimeoutSeconds: Int = 30
        private set

    var title: String = "&8面对面交易"
        private set

    var requestMessage: String = "&e{player} &f向你发起了交易请求，输入 &a/trade accept {player} &f接受。"
        private set

    var requestAcceptButtonMessage: String = "&a[点击接受交易]"
        private set

    var requestDenyButtonMessage: String = "&c[点击拒绝交易]"
        private set

    var requestAcceptHoverMessage: String = "&a点击接受来自 {player} 的交易请求"
        private set

    var requestDenyHoverMessage: String = "&c点击拒绝来自 {player} 的交易请求"
        private set

    var requestSentMessage: String = "&a已向 &f{player} &a发送交易请求。"
        private set

    var requestDeniedSenderMessage: String = "&c{player} 拒绝了你的交易请求。"
        private set

    var requestDeniedReceiverMessage: String = "&e你已拒绝来自 &f{player} &e的交易请求。"
        private set

    var requestNoActiveMessage: String = "&c你没有来自该玩家的有效交易请求。"
        private set

    var requestAlreadySentMessage: String = "&c你最近已经向该玩家发送过交易请求。"
        private set

    var requestAlreadyTradingMessage: String = "&c你或目标玩家正在交易中。"
        private set

    var requestExpiredMessage: String = "&c交易请求已过期。"
        private set

    var tradeStartedMessage: String = "&a你已与 &f{player} &a开始交易。"
        private set

    var tradeCancelledMessage: String = "&c交易已取消。"
        private set

    var tradeCompletedMessage: String = "&a交易已完成。"
        private set

    var moneyInputPromptMessage: String = "&e请输入本次报价金额，输入 &fcancel &e取消。当前报价: &f{current}"
        private set

    var moneyInputInvalidMessage: String = "&c请输入有效的非负数字金额，输入 cancel 取消。"
        private set

    var moneyInputCancelledMessage: String = "&e已取消本次金额输入。"
        private set

    var inventoryFullMessage: String = "&c交易失败：对方或你的背包空间不足。"
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        requestTimeoutSeconds = config.getInt("requestTimeoutSeconds", 30).coerceAtLeast(5)
        title = config.getString("title") ?: "&8面对面交易"
        requestMessage = config.getString("messages.request") ?: "&e{player} &f向你发起了交易请求。"
        requestAcceptButtonMessage = config.getString("messages.requestAcceptButton") ?: "&a[点击接受交易]"
        requestDenyButtonMessage = config.getString("messages.requestDenyButton") ?: "&c[点击拒绝交易]"
        requestAcceptHoverMessage = config.getString("messages.requestAcceptHover") ?: "&a点击接受来自 {player} 的交易请求"
        requestDenyHoverMessage = config.getString("messages.requestDenyHover") ?: "&c点击拒绝来自 {player} 的交易请求"
        requestSentMessage = config.getString("messages.requestSent") ?: "&a已向 &f{player} &a发送交易请求。"
        requestDeniedSenderMessage = config.getString("messages.requestDeniedSender") ?: "&c{player} 拒绝了你的交易请求。"
        requestDeniedReceiverMessage = config.getString("messages.requestDeniedReceiver") ?: "&e你已拒绝来自 &f{player} &e的交易请求。"
        requestNoActiveMessage = config.getString("messages.requestNoActive") ?: "&c你没有来自该玩家的有效交易请求。"
        requestAlreadySentMessage = config.getString("messages.requestAlreadySent") ?: "&c你最近已经向该玩家发送过交易请求。"
        requestAlreadyTradingMessage = config.getString("messages.requestAlreadyTrading") ?: "&c你或目标玩家正在交易中。"
        requestExpiredMessage = config.getString("messages.requestExpired") ?: "&c交易请求已过期。"
        tradeStartedMessage = config.getString("messages.started") ?: "&a你已与 &f{player} &a开始交易。"
        tradeCancelledMessage = config.getString("messages.cancelled") ?: "&c交易已取消。"
        tradeCompletedMessage = config.getString("messages.completed") ?: "&a交易已完成。"
        moneyInputPromptMessage = config.getString("messages.moneyInputPrompt") ?: "&e请输入本次报价金额，输入 &fcancel &e取消。当前报价: &f{current}"
        moneyInputInvalidMessage = config.getString("messages.moneyInputInvalid") ?: "&c请输入有效的非负数字金额，输入 cancel 取消。"
        moneyInputCancelledMessage = config.getString("messages.moneyInputCancelled") ?: "&e已取消本次金额输入。"
        inventoryFullMessage = config.getString("messages.inventoryFull") ?: "&c交易失败：对方或你的背包空间不足。"
    }
}
