package com.pixlehavencore.feature.trade

import com.pixlehavencore.bridge.TextBridge
import com.pixlehavencore.util.PlaceholderUtils.resolvePlaceholders
import com.pixlehavencore.util.EconomyUtils
import com.pixlehavencore.util.InventoryUtils
import com.pixlehavencore.util.ItemUtils
import com.pixlehavencore.util.TextUtils
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.submit
import taboolib.platform.util.submit as submitOnEntity
import taboolib.platform.util.submit as submitOnLocation
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import taboolib.platform.util.PlayerSessionMap

object TradeService {

    private val requests = PlayerSessionMap<TradeRequest>({ TradeRequest(it, 0L) }, manualRelease = true)
    private val sessions = PlayerSessionMap<TradeSession>({ throw IllegalStateException() }, manualRelease = true)
    private val moneyInputs = PlayerSessionMap<UUID>({ throw IllegalStateException() }, manualRelease = true)
    private val interactCooldown = PlayerSessionMap<Long>({ 0L })
    private val sessionSuspendClose = PlayerSessionMap<Long>({ 0L })

    private val leftOfferSlots = setOf(10, 11, 12, 19, 20, 21, 28, 29, 30)
    private val rightOfferSlots = setOf(14, 15, 16, 23, 24, 25, 32, 33, 34)
    private const val LEFT_STATUS_SLOT = 46
    private const val LEFT_MONEY_SLOT = 47
    private const val CANCEL_SLOT = 49
    private const val RIGHT_MONEY_SLOT = 51
    private const val RIGHT_STATUS_SLOT = 52
    private const val CENTER_INFO_SLOT = 4

    fun init() {
        reload()
    }

    fun reload() {
        TradeSettings.reload()
        requests.clear()
        // 先退还进行中交易的物品，再清空会话
        sessions.values().filterNotNull().toSet().forEach { session -> abort(session, true) }
        sessions.clear()
        moneyInputs.clear()
        interactCooldown.clear()
        sessionSuspendClose.clear()
    }

    fun requestTrade(sender: Player, target: Player): Boolean {
        if (!TradeSettings.enabled || sender.uniqueId == target.uniqueId) {
            return false
        }
        if (isTrading(sender) || isTrading(target)) {
            sender.sendMessage(TextBridge.toLegacy(TextUtils.parse(TradeSettings.requestAlreadyTradingMessage.resolvePlaceholders("{player}" to target.name))))
            return false
        }
        val reverse = requests.get(sender.uniqueId)
        if (reverse != null && reverse.sender == target.uniqueId && !reverse.isExpired()) {
            requests.remove(sender.uniqueId)
            openTrade(sender, target)
            return true
        }

        cleanupExpiredRequests(sender, target)
        val existing = requests.get(target.uniqueId)
        if (existing != null && existing.sender == sender.uniqueId && !existing.isExpired()) {
            sender.sendMessage(TextBridge.toLegacy(TextUtils.parse(TradeSettings.requestAlreadySentMessage.resolvePlaceholders("{player}" to target.name))))
            return false
        }
        requests.set(target.uniqueId, TradeRequest(sender.uniqueId, System.currentTimeMillis()))
        sendRequestMessage(target, sender)
        sender.sendMessage(TextBridge.toLegacy(TextUtils.parse(TradeSettings.requestSentMessage.resolvePlaceholders("{player}" to target.name))))
        return true
    }

    fun requestTradeByInteract(sender: Player, target: Player): Boolean {
        val now = System.currentTimeMillis()
        val last = interactCooldown.get(sender.uniqueId) ?: 0L
        if (now - last < 350L) {
            return false
        }
        interactCooldown.set(sender.uniqueId, now)
        return requestTrade(sender, target)
    }

    fun acceptTrade(target: Player, requesterName: String): Boolean {
        cleanupExpiredRequests(target)
        val request = requests.get(target.uniqueId) ?: return false
        val requester = Bukkit.getPlayer(request.sender) ?: return false
        if (!requester.name.equals(requesterName, ignoreCase = true)) {
            return false
        }
        if (request.isExpired()) {
            requests.remove(target.uniqueId)
            target.sendMessage(TextBridge.toLegacy(TextUtils.parse(TradeSettings.requestExpiredMessage)))
            return false
        }
        requests.remove(target.uniqueId)
        openTrade(requester, target)
        return true
    }

    fun denyTrade(target: Player, requesterName: String): Boolean {
        cleanupExpiredRequests(target)
        val request = requests.get(target.uniqueId) ?: return false
        val requester = Bukkit.getPlayer(request.sender) ?: return false
        if (!requester.name.equals(requesterName, ignoreCase = true)) {
            return false
        }
        requests.remove(target.uniqueId)
        requester.sendMessage(TextBridge.toLegacy(TextUtils.parse(TradeSettings.requestDeniedSenderMessage.resolvePlaceholders("{player}" to target.name))))
        target.sendMessage(TextBridge.toLegacy(TextUtils.parse(TradeSettings.requestDeniedReceiverMessage.resolvePlaceholders("{player}" to requester.name))))
        return true
    }

    fun isTrading(player: Player): Boolean {
        return sessions.contains(player.uniqueId)
    }

    private fun cleanupExpiredRequests(vararg related: Player) {
        val now = System.currentTimeMillis()
        val relatedIds = related.map { it.uniqueId }.toSet()
        requests.forEach { uuid, request ->
            val expired = now - request.createdAt > TradeSettings.requestTimeoutSeconds * 1000L
            if (expired) {
                if (relatedIds.isEmpty() || uuid in relatedIds || request.sender in relatedIds) {
                    Bukkit.getPlayer(request.sender)?.sendMessage(TextBridge.toLegacy(TextUtils.parse(TradeSettings.requestExpiredMessage)))
                }
                requests.remove(uuid)
            }
        }
    }

    private fun sendRequestMessage(target: Player, sender: Player) {
        target.sendMessage(TextBridge.toLegacy(TextUtils.parse(TradeSettings.requestMessage.resolvePlaceholders("{player}" to sender.name))))
        val accept = TextUtils.parse(TradeSettings.requestAcceptButtonMessage)
            .clickEvent(ClickEvent.runCommand("/trade accept ${sender.name}"))
            .hoverEvent(HoverEvent.showText(TextUtils.parse(TradeSettings.requestAcceptHoverMessage.resolvePlaceholders("{player}" to sender.name))))

        val deny = TextUtils.parse(" " + TradeSettings.requestDenyButtonMessage)
            .clickEvent(ClickEvent.runCommand("/trade deny ${sender.name}"))
            .hoverEvent(HoverEvent.showText(TextUtils.parse(TradeSettings.requestDenyHoverMessage.resolvePlaceholders("{player}" to sender.name))))

        target.sendMessage(TextBridge.toLegacy(accept.append(deny)))
    }

    fun openTrade(left: Player, right: Player) {
        val titleStr = TextBridge.toLegacy(TextUtils.parse(TradeSettings.title))
        val leftInventory = Bukkit.createInventory(null, 54, titleStr)
        val rightInventory = Bukkit.createInventory(null, 54, titleStr)
        val session = TradeSession(left.uniqueId, right.uniqueId, leftInventory, rightInventory)
        sessions.set(left.uniqueId, session)
        sessions.set(right.uniqueId, session)
        render(session)
        left.openInventory(leftInventory)
        right.openInventory(rightInventory)
        left.sendMessage(TextBridge.toLegacy(TextUtils.parse(TradeSettings.tradeStartedMessage.resolvePlaceholders("{player}" to right.name))))
        right.sendMessage(TextBridge.toLegacy(TextUtils.parse(TradeSettings.tradeStartedMessage.resolvePlaceholders("{player}" to left.name))))
    }

    fun isTradeInventory(player: Player, inventory: Inventory): Boolean {
        val session = sessions.get(player.uniqueId) ?: return false
        return playerInventory(session, player.uniqueId) === inventory
    }

    fun handleClick(player: Player, rawSlot: Int): Boolean {
        val session = sessions.get(player.uniqueId) ?: return false
        val isLeft = player.uniqueId == session.left
        val ownSlots = if (isLeft) leftOfferSlots else rightOfferSlots
        val ownMoneySlot = if (isLeft) LEFT_MONEY_SLOT else RIGHT_MONEY_SLOT
        val ownStatusSlot = if (isLeft) LEFT_STATUS_SLOT else RIGHT_STATUS_SLOT
        val topSize = playerInventory(session, player.uniqueId).size

        if (rawSlot >= topSize) {
            return false
        }

        when (rawSlot) {
            ownStatusSlot -> {
                session.confirm(player.uniqueId)
                render(session, preserveOffers = true)
                if (session.leftConfirmed.get() && session.rightConfirmed.get()) {
                    completeTrade(session)
                }
                return true
            }
            ownMoneySlot -> {
                requestMoneyInput(player, session)
                return true
            }
            CANCEL_SLOT -> {
                abort(session, true)
                return true
            }
            CENTER_INFO_SLOT -> return true
        }

        if (session.isLocked(player.uniqueId)) {
            return true
        }
        if (rawSlot !in ownSlots) {
            return true
        }

        session.resetConfirm()
        player.submitOnEntity(delay = 1L) { render(session, preserveOffers = true) }
        return false
    }

    fun handleBottomClick(player: Player, action: org.bukkit.event.inventory.InventoryAction, currentItem: ItemStack?, shift: Boolean): Boolean {
        val session = sessions.get(player.uniqueId) ?: return false
        if (session.isLocked(player.uniqueId)) {
            return true
        }
        val isLeft = player.uniqueId == session.left
        val ownSlots = if (isLeft) leftOfferSlots else rightOfferSlots
        if (shift && action == org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY && currentItem != null && currentItem.type != Material.AIR) {
            val inventory = playerInventory(session, player.uniqueId)
            val empty = ownSlots.firstOrNull { inventory.getItem(it) == null }
            if (empty != null) {
                inventory.setItem(empty, currentItem.clone())
                currentItem.amount = 0
                session.resetConfirm()
                render(session, preserveOffers = true)
            }
            return true
        }
        return false
    }

    fun handleMoneyInput(player: Player, message: String): Boolean {
        val sessionId = moneyInputs.get(player.uniqueId) ?: return false
        val session = sessions.get(player.uniqueId) ?: run {
            moneyInputs.remove(player.uniqueId)
            return true
        }
        if (session.id != sessionId) {
            moneyInputs.remove(player.uniqueId)
            return true
        }

        val input = message.trim()
        if (input.equals("cancel", ignoreCase = true)) {
            moneyInputs.remove(player.uniqueId)
            player.sendMessage(TextBridge.toLegacy(TextUtils.parse(TradeSettings.moneyInputCancelledMessage)))
            player.submitOnEntity { reopen(player, session) }
            return true
        }
        val amount = input.toBigDecimalOrNull()
        if (amount == null || amount < BigDecimal.ZERO) {
            player.sendMessage(TextBridge.toLegacy(TextUtils.parse(TradeSettings.moneyInputInvalidMessage)))
            return true
        }

        val finalAmount = amount.min(EconomyUtils.getBalance(player))
        session.moneyOffers[player.uniqueId] = finalAmount
        session.resetConfirm()
        moneyInputs.remove(player.uniqueId)
        player.submitOnEntity { reopen(player, session) }
        return true
    }

    fun handleClose(player: Player) {
        val session = sessions.get(player.uniqueId) ?: return
        val suspendUntil = sessionSuspendClose.get(player.uniqueId) ?: 0L
        if (System.currentTimeMillis() < suspendUntil) {
            return
        }
        if (moneyInputs.contains(player.uniqueId)) {
            return
        }
        abort(session, true)
    }

    fun handlePlayerQuit(player: Player) {
        val session = sessions.get(player.uniqueId)
        if (session != null) {
            abort(session, false)
        }
        requests.remove(player.uniqueId)
    }

    private fun requestMoneyInput(player: Player, session: TradeSession) {
        moneyInputs.set(player.uniqueId, session.id)
        sessionSuspendClose.set(player.uniqueId, System.currentTimeMillis() + 3000L)
        player.closeInventory()
        player.sendMessage(
            TextBridge.toLegacy(TextUtils.parse(TradeSettings.moneyInputPromptMessage
                .resolvePlaceholders("{current}" to formatMoney(session.moneyOffers[player.uniqueId] ?: BigDecimal.ZERO))))
        )
    }

    private fun reopen(player: Player, session: TradeSession) {
        render(session, preserveOffers = true)
        sessionSuspendClose.set(player.uniqueId, System.currentTimeMillis() + 1500L)
        player.openInventory(playerInventory(session, player.uniqueId))
    }

    private fun completeTrade(session: TradeSession) {
        val left = Bukkit.getPlayer(session.left) ?: return abort(session, true)
        val right = Bukkit.getPlayer(session.right) ?: return abort(session, true)

        val leftItems = snapshotOwnerItems(session, session.left)
        val rightItems = snapshotOwnerItems(session, session.right)
        if (!canFit(right, leftItems) || !canFit(left, rightItems)) {
            left.sendMessage(TextBridge.toLegacy(TextUtils.parse(TradeSettings.inventoryFullMessage)))
            right.sendMessage(TextBridge.toLegacy(TextUtils.parse(TradeSettings.inventoryFullMessage)))
            return
        }

        val leftMoney = session.moneyOffers[session.left] ?: BigDecimal.ZERO
        val rightMoney = session.moneyOffers[session.right] ?: BigDecimal.ZERO
        if (leftMoney > BigDecimal.ZERO && !EconomyUtils.withdraw(left, leftMoney)) {
            left.sendMessage(TextBridge.toLegacy(TextUtils.parse("&c你的余额不足，交易取消。")))
            return abort(session, true)
        }
        if (rightMoney > BigDecimal.ZERO && !EconomyUtils.withdraw(right, rightMoney)) {
            if (leftMoney > BigDecimal.ZERO) EconomyUtils.depositInternal(left, leftMoney)
            right.sendMessage(TextBridge.toLegacy(TextUtils.parse("&c你的余额不足，交易取消。")))
            return abort(session, true)
        }

        if (rightMoney > BigDecimal.ZERO && !EconomyUtils.deposit(left, rightMoney)) {
            rollbackMoney(left, right, leftMoney, rightMoney)
            return abort(session, true)
        }
        if (leftMoney > BigDecimal.ZERO && !EconomyUtils.deposit(right, leftMoney)) {
            if (rightMoney > BigDecimal.ZERO) EconomyUtils.withdraw(left, rightMoney)
            rollbackMoney(left, right, leftMoney, rightMoney)
            return abort(session, true)
        }

        deliverItems(right, leftItems)
        deliverItems(left, rightItems)
        clearOfferInventories(session)
        unregister(session)
        left.closeInventory()
        right.closeInventory()
        left.sendMessage(TextBridge.toLegacy(TextUtils.parse(TradeSettings.tradeCompletedMessage)))
        right.sendMessage(TextBridge.toLegacy(TextUtils.parse(TradeSettings.tradeCompletedMessage)))
    }

    private fun abort(session: TradeSession, closeInventory: Boolean) {
        val left = Bukkit.getPlayer(session.left)
        val right = Bukkit.getPlayer(session.right)
        refund(left, snapshotOwnerItems(session, session.left))
        refund(right, snapshotOwnerItems(session, session.right))
        clearOfferInventories(session)
        unregister(session)
        if (closeInventory) {
            left?.closeInventory()
            right?.closeInventory()
        }
        left?.sendMessage(TextBridge.toLegacy(TextUtils.parse(TradeSettings.tradeCancelledMessage)))
        right?.sendMessage(TextBridge.toLegacy(TextUtils.parse(TradeSettings.tradeCancelledMessage)))
    }

    private fun unregister(session: TradeSession) {
        sessions.remove(session.left)
        sessions.remove(session.right)
        moneyInputs.forEach { uuid, sessionId ->
            if (sessionId == session.id) moneyInputs.remove(uuid)
        }
        sessionSuspendClose.remove(session.left)
        sessionSuspendClose.remove(session.right)
    }

    private fun rollbackMoney(left: Player, right: Player, leftMoney: BigDecimal, rightMoney: BigDecimal) {
        if (leftMoney > BigDecimal.ZERO) EconomyUtils.depositInternal(left, leftMoney)
        if (rightMoney > BigDecimal.ZERO) EconomyUtils.depositInternal(right, rightMoney)
    }

    private fun playerInventory(session: TradeSession, player: UUID): Inventory {
        return if (player == session.left) session.leftInventory else session.rightInventory
    }

    private fun snapshotOwnerItems(session: TradeSession, owner: UUID): List<ItemStack> {
        val slots = if (owner == session.left) leftOfferSlots else rightOfferSlots
        return snapshotItems(playerInventory(session, owner), slots)
    }

    private fun clearOfferInventories(session: TradeSession) {
        val offerSlots = leftOfferSlots + rightOfferSlots
        clearInventory(session.leftInventory, offerSlots)
        clearInventory(session.rightInventory, offerSlots)
    }

    private fun snapshotItems(inventory: Inventory, slots: Set<Int>): List<ItemStack> {
        return slots.mapNotNull { inventory.getItem(it)?.clone() }
    }

    private fun refund(player: Player?, items: List<ItemStack>) {
        if (player == null) {
            return
        }
        // Folia: 在玩家区域线程上执行背包操作和掉落
        player.submitOnEntity {
            items.forEach { item ->
                val leftovers = player.inventory.addItem(item)
                if (leftovers.isNotEmpty()) {
                    val location = player.location
                    val world = player.world
                    location.submitOnLocation {
                        leftovers.values.forEach { extra -> world.dropItemNaturally(location, extra) }
                    }
                }
            }
        }
    }

    private fun deliverItems(player: Player, items: List<ItemStack>) {
        // Folia: 在玩家区域线程上执行背包操作和掉落
        player.submitOnEntity {
            items.forEach { item ->
                val leftovers = player.inventory.addItem(item)
                if (leftovers.isNotEmpty()) {
                    val location = player.location
                    val world = player.world
                    location.submitOnLocation {
                        leftovers.values.forEach { extra -> world.dropItemNaturally(location, extra) }
                    }
                }
            }
        }
    }

    private fun canFit(player: Player, items: List<ItemStack>): Boolean {
        var clone = InventoryUtils.compact(player.inventory.contents)
        items.forEach { item ->
            val next = InventoryUtils.addToVirtual(clone, item)
            if (next.isEmpty()) {
                return false
            }
            clone = next
        }
        return true
    }

    private fun clearInventory(inventory: Inventory, slots: Set<Int>) {
        slots.forEach { inventory.setItem(it, null) }
    }

    private fun render(session: TradeSession, preserveOffers: Boolean = false) {
        val leftItems = if (preserveOffers) snapshotOwnerItems(session, session.left) else emptyList()
        val rightItems = if (preserveOffers) snapshotOwnerItems(session, session.right) else emptyList()
        val left = Bukkit.getPlayer(session.left)
        val right = Bukkit.getPlayer(session.right)

        renderView(
            inventory = session.leftInventory,
            selfName = left?.name ?: "Unknown",
            otherName = right?.name ?: "Unknown",
            selfItems = leftItems,
            otherItems = rightItems,
            selfConfirmed = session.leftConfirmed.get(),
            otherConfirmed = session.rightConfirmed.get(),
            selfMoney = session.moneyOffers[session.left] ?: BigDecimal.ZERO,
            otherMoney = session.moneyOffers[session.right] ?: BigDecimal.ZERO
        )

        renderView(
            inventory = session.rightInventory,
            selfName = right?.name ?: "Unknown",
            otherName = left?.name ?: "Unknown",
            selfItems = rightItems,
            otherItems = leftItems,
            selfConfirmed = session.rightConfirmed.get(),
            otherConfirmed = session.leftConfirmed.get(),
            selfMoney = session.moneyOffers[session.right] ?: BigDecimal.ZERO,
            otherMoney = session.moneyOffers[session.left] ?: BigDecimal.ZERO
        )
    }

    private fun renderView(
        inventory: Inventory,
        selfName: String,
        otherName: String,
        selfItems: List<ItemStack>,
        otherItems: List<ItemStack>,
        selfConfirmed: Boolean,
        otherConfirmed: Boolean,
        selfMoney: BigDecimal,
        otherMoney: BigDecimal
    ) {
        for (slot in 0 until inventory.size) {
            inventory.setItem(slot, ItemUtils.namedItem(Material.GRAY_STAINED_GLASS_PANE, "&7 "))
        }
        (leftOfferSlots + rightOfferSlots).forEach { inventory.setItem(it, null) }
        restoreOfferItems(inventory, leftOfferSlots, selfItems)
        restoreOfferItems(inventory, rightOfferSlots, otherItems)

        inventory.setItem(CENTER_INFO_SLOT, infoItem(selfName, otherName))
        inventory.setItem(LEFT_STATUS_SLOT, confirmItem(true, selfConfirmed, otherConfirmed, selfMoney))
        inventory.setItem(RIGHT_STATUS_SLOT, confirmItem(false, otherConfirmed, selfConfirmed, otherMoney))
        inventory.setItem(LEFT_MONEY_SLOT, moneyItem(true, selfMoney))
        inventory.setItem(RIGHT_MONEY_SLOT, moneyItem(false, otherMoney))
        inventory.setItem(CANCEL_SLOT, cancelItem())
    }

    private fun restoreOfferItems(inventory: Inventory, slots: Set<Int>, items: List<ItemStack>) {
        val sortedSlots = slots.toList().sorted()
        items.forEachIndexed { index, itemStack ->
            val slot = sortedSlots.getOrNull(index) ?: return@forEachIndexed
            inventory.setItem(slot, itemStack.clone())
        }
    }

    private fun infoItem(left: String, right: String): ItemStack {
        return ItemStack(Material.BOOK).apply {
            itemMeta = itemMeta?.apply {
                customName(TextUtils.parseItem("&e交易信息"))
                lore(listOf(
                    TextUtils.parseItem("&7左侧玩家: &f$left"),
                    TextUtils.parseItem("&7右侧玩家: &f$right"),
                    TextUtils.parseItem("&7双方确认后才会完成交换"),
                    TextUtils.parseItem("&7修改物品或金额会重置确认")
                ))
            }
        }
    }

    private fun confirmItem(leftSide: Boolean, selfConfirmed: Boolean, otherConfirmed: Boolean, money: BigDecimal): ItemStack {
        val material = if (selfConfirmed) Material.LIME_WOOL else Material.RED_WOOL
        val side = if (leftSide) "左侧" else "右侧"
        val otherSide = if (leftSide) "右侧" else "左侧"
        val selfStatus = if (selfConfirmed) "&a已确认" else "&c未确认"
        val otherStatus = if (otherConfirmed) "&a已确认" else "&c未确认"
        return ItemStack(material).apply {
            itemMeta = itemMeta?.apply {
                customName(TextUtils.parseItem("&e$side 确认按钮"))
                lore(listOf(
                    TextUtils.parseItem("&7$side 状态: $selfStatus"),
                    TextUtils.parseItem("&7$otherSide 状态: $otherStatus"),
                    TextUtils.parseItem("&7$side 报价: &f${formatMoney(money)}"),
                    TextUtils.parseItem("&7确认后会锁定该侧的交易操作")
                ))
            }
        }
    }

    private fun moneyItem(leftSide: Boolean, amount: BigDecimal): ItemStack {
        val side = if (leftSide) "左侧" else "右侧"
        return ItemStack(Material.GOLD_INGOT).apply {
            itemMeta = itemMeta?.apply {
                customName(TextUtils.parseItem("&6$side 金币报价: &f${formatMoney(amount)}"))
                lore(listOf(
                    TextUtils.parseItem("&7当前编辑的是${side}的金币报价"),
                    TextUtils.parseItem("&7点击后通过聊天输入金额"),
                    TextUtils.parseItem("&7输入 cancel 取消本次输入"),
                    TextUtils.parseItem("&7税收会在交易完成时结算")
                ))
            }
        }
    }

    private fun cancelItem(): ItemStack {
        return ItemStack(Material.BARRIER).apply {
            itemMeta = itemMeta?.apply {
                customName(TextUtils.parseItem("&c取消交易"))
                lore(listOf(TextUtils.parseItem("&7点击后立即取消并退回双方物品")))
            }
        }
    }

    private fun formatMoney(value: BigDecimal): String {
        return value.setScale(0, RoundingMode.HALF_UP).toPlainString()
    }

    private data class TradeRequest(
        val sender: UUID,
        val createdAt: Long
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - createdAt > TradeSettings.requestTimeoutSeconds * 1000L
        }
    }

    private data class TradeSession(
        val left: UUID,
        val right: UUID,
        val leftInventory: Inventory,
        val rightInventory: Inventory,
        val id: UUID = UUID.randomUUID(),
        val moneyOffers: MutableMap<UUID, BigDecimal> = mutableMapOf(left to BigDecimal.ZERO, right to BigDecimal.ZERO),
        val leftConfirmed: AtomicBoolean = AtomicBoolean(false),
        val rightConfirmed: AtomicBoolean = AtomicBoolean(false)
    ) {
        fun confirm(player: UUID) {
            if (player == left) leftConfirmed.set(true)
            if (player == right) rightConfirmed.set(true)
        }

        fun resetConfirm() {
            leftConfirmed.set(false)
            rightConfirmed.set(false)
        }

        fun isLocked(player: UUID): Boolean {
            return if (player == left) leftConfirmed.get() else rightConfirmed.get()
        }
    }
}
