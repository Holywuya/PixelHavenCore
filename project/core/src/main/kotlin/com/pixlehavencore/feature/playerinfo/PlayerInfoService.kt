package com.pixlehavencore.feature.playerinfo

import com.pixlehavencore.bridge.TextBridge
import com.pixlehavencore.feature.playtime.PlaytimeService
import com.pixlehavencore.feature.playtime.PlaytimeSettings
import com.pixlehavencore.util.EconomyUtils
import com.pixlehavencore.util.OfflineInventoryUtils
import com.pixlehavencore.util.PlaceholderUtils.resolvePlaceholders
import com.pixlehavencore.util.TextUtils
import com.pixlehavencore.feature.playerinv.PlayerInvService
import com.pixlehavencore.feature.playerinv.PlayerInvSettings
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.persistence.PersistentDataType
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import taboolib.platform.util.modifyMeta
import taboolib.platform.util.submit as submitOnEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PlayerInfoService {

    private val sessions = ConcurrentHashMap<Int, Session>()
    private val actionKey = NamespacedKey("phcore", "playerinfo_action")

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

    private const val DASHBOARD_ROWS = 3
    private const val INV_ROWS = 6
    private const val EC_ROWS = 3

    private val decorativeSlots3Row = (0..8) + (18..26)
    private val decorativeSlotsInv = (0..8) + (45..53)
    private val decorativeSlotsEC = (0..8) + (18..26)

    fun init() {
        PlayerInfoSettings.init()
        sessions.clear()
    }

    fun reload() {
        init()
    }

    fun stop() {
        sessions.clear()
    }

    // ══════════════════════════════════════
    // Dashboard
    // ══════════════════════════════════════

    fun openDashboard(viewer: Player, target: OfflinePlayer) {
        if (!PlayerInfoSettings.enabled) return
        val targetName = target.name ?: target.uniqueId.toString()
        val title = TextUtils.parse(PlayerInfoSettings.dashboardTitle.resolvePlaceholders("{player}" to targetName))

        submit(async = true) {
            val playtimeData = PlaytimeService.queryPlaytime(target.uniqueId)
            val balance = EconomyUtils.getBalance(target)

            viewer.submitOnEntity {
                if (!viewer.isOnline) return@submitOnEntity

                val inventory = Bukkit.createInventory(null, DASHBOARD_ROWS * 9, title)
                val filler = buildDecorativeItem()
                decorativeSlots3Row.forEach { inventory.setItem(it, filler) }

                inventory.setItem(10, buildHeadItem(target, targetName))
                inventory.setItem(11, buildInfoItem(Material.CLOCK, "&e首次加入", formatTimestamp(target.firstPlayed)))
                inventory.setItem(12, buildInfoItem(Material.COMPASS, "&e上次在线", if (target.isOnline) "&a在线中" else formatTimestamp(target.lastSeen)))
                inventory.setItem(13, buildInfoItem(Material.BOOK, "&e总在线时长", PlaytimeSettings.formatSeconds(playtimeData?.totalSeconds ?: 0L)))
                inventory.setItem(14, buildInfoItem(Material.GOLD_INGOT, "&e金币余额", "&6${balance.toPlainString()} 金币"))
                inventory.setItem(15, buildActionItem(Material.CHEST, "&e查看背包", listOf("&7点击打开背包"), "inv"))
                inventory.setItem(16, buildActionItem(Material.ENDER_CHEST, "&e查看末影箱", listOf("&7点击打开末影箱"), "ec"))
                inventory.setItem(17, buildActionItem(Material.CHEST_MINECART, "&e个人仓库", listOf("&7点击打开目标玩家仓库"), "ware"))

                sessions[System.identityHashCode(inventory)] = Session(
                    viewer = viewer.uniqueId,
                    target = target.uniqueId,
                    type = SessionType.DASHBOARD
                )
                viewer.openInventory(inventory)
            }
        }
    }

    // ══════════════════════════════════════
    // Warehouse
    // ══════════════════════════════════════

    private fun openWarehouse(viewer: Player, target: OfflinePlayer) {
        if (!PlayerInvSettings.enabled) {
            viewer.sendMessage(TextUtils.parse("&c仓库模块未启用"))
            return
        }
        PlayerInvService.openOtherAsync(viewer, target) { opened ->
            if (!opened) {
                viewer.sendMessage(TextUtils.parse("&c打开目标玩家仓库失败"))
            }
        }
    }

    // ══════════════════════════════════════
    // Inventory View
    // ══════════════════════════════════════

    fun openInventoryView(viewer: Player, target: OfflinePlayer) {
        if (!PlayerInfoSettings.enabled) return
        val targetName = target.name ?: target.uniqueId.toString()
        val title = TextUtils.parse(PlayerInfoSettings.invTitle.resolvePlaceholders("{player}" to targetName))

        val online = target.player
        if (online != null) {
            online.submitOnEntity {
                val contents = online.inventory.contents.copyOf()
                viewer.submitOnEntity {
                    if (!viewer.isOnline) return@submitOnEntity
                    openInvWindow(viewer, title, contents, target)
                }
            }
        } else {
            submit(async = true) {
                val snapshot = OfflineInventoryUtils.load(target)
                viewer.submitOnEntity {
                    if (!viewer.isOnline) return@submitOnEntity
                    openInvWindow(viewer, title, snapshot?.inventory ?: arrayOfNulls(41), target)
                }
            }
        }
    }

    private fun openInvWindow(viewer: Player, title: net.kyori.adventure.text.Component, contents: Array<ItemStack?>, target: OfflinePlayer) {
        val inventory = Bukkit.createInventory(null, INV_ROWS * 9, title)
        val targetName = target.name ?: target.uniqueId.toString()

        val filler = buildDecorativeItem()
        decorativeSlotsInv.forEach { inventory.setItem(it, filler) }

        for (i in 0 until minOf(contents.size, 41)) {
            inventory.setItem(i, contents[i])
        }
        inventory.setItem(49, buildActionItem(Material.BARRIER, "&c返回", listOf("&7返回玩家信息界面"), "back"))

        sessions[System.identityHashCode(inventory)] = Session(
            viewer = viewer.uniqueId,
            target = target.uniqueId,
            type = SessionType.INVENTORY
        )
        viewer.openInventory(inventory)
    }

    // ══════════════════════════════════════
    // Ender Chest View
    // ══════════════════════════════════════

    fun openEnderChestView(viewer: Player, target: OfflinePlayer) {
        if (!PlayerInfoSettings.enabled) return
        val targetName = target.name ?: target.uniqueId.toString()
        val title = TextUtils.parse(PlayerInfoSettings.ecTitle.resolvePlaceholders("{player}" to targetName))

        val online = target.player
        if (online != null) {
            online.submitOnEntity {
                val contents = online.enderChest.contents.copyOf()
                viewer.submitOnEntity {
                    if (!viewer.isOnline) return@submitOnEntity
                    openECWindow(viewer, title, contents, target)
                }
            }
        } else {
            submit(async = true) {
                val snapshot = OfflineInventoryUtils.load(target)
                viewer.submitOnEntity {
                    if (!viewer.isOnline) return@submitOnEntity
                    openECWindow(viewer, title, snapshot?.enderChest ?: arrayOfNulls(27), target)
                }
            }
        }
    }

    private fun openECWindow(viewer: Player, title: net.kyori.adventure.text.Component, contents: Array<ItemStack?>, target: OfflinePlayer) {
        val inventory = Bukkit.createInventory(null, EC_ROWS * 9, title)
        val targetName = target.name ?: target.uniqueId.toString()

        val filler = buildDecorativeItem()
        decorativeSlotsEC.forEach { inventory.setItem(it, filler) }

        for (i in 0 until minOf(contents.size, 27)) {
            inventory.setItem(i, contents[i])
        }
        inventory.setItem(22, buildActionItem(Material.BARRIER, "&c返回", listOf("&7返回玩家信息界面"), "back"))

        sessions[System.identityHashCode(inventory)] = Session(
            viewer = viewer.uniqueId,
            target = target.uniqueId,
            type = SessionType.ENDER_CHEST
        )
        viewer.openInventory(inventory)
    }

    // ══════════════════════════════════════
    // Event Handlers
    // ══════════════════════════════════════

    @SubscribeEvent
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val session = sessions[System.identityHashCode(event.view.topInventory)] ?: return
        if (session.viewer != player.uniqueId) return

        event.isCancelled = true

        if (event.clickedInventory != event.view.topInventory) return

        val action = getAction(event.currentItem) ?: return
        when (action) {
            "inv" -> {
                player.closeInventory()
                openInventoryView(player, Bukkit.getOfflinePlayer(session.target))
            }
            "ec" -> {
                player.closeInventory()
                openEnderChestView(player, Bukkit.getOfflinePlayer(session.target))
            }
            "back" -> {
                player.closeInventory()
                openDashboard(player, Bukkit.getOfflinePlayer(session.target))
            }
            "ware" -> {
                player.closeInventory()
                openWarehouse(player, Bukkit.getOfflinePlayer(session.target))
            }
        }
    }

    @SubscribeEvent
    fun onDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        val owner = sessions[System.identityHashCode(event.view.topInventory)] ?: return
        if (owner.viewer == player.uniqueId && event.rawSlots.any { it < event.view.topInventory.size }) {
            event.isCancelled = true
        }
    }

    @SubscribeEvent
    fun onClose(event: InventoryCloseEvent) {
        sessions.remove(System.identityHashCode(event.inventory))
    }

    // ══════════════════════════════════════
    // Item Builders
    // ══════════════════════════════════════

    private fun buildDecorativeItem(): ItemStack {
        val item = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        TextBridge.setDisplayName(item, TextUtils.parseItem("&7"))
        return item
    }

    private fun buildHeadItem(target: OfflinePlayer, name: String): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        item.modifyMeta<SkullMeta> {
            owningPlayer = target
        }
        val statusText = if (target.isOnline) "&a在线" else "&7离线"
        TextBridge.setDisplayName(item, TextUtils.parseItem("&e$name"))
        @Suppress("UNCHECKED_CAST")
        TextBridge.setLore(item, TextUtils.parseItemLore(listOf(
            "&7UUID: &f${target.uniqueId}",
            "&7状态: $statusText"
        )) as List<net.kyori.adventure.text.Component>)
        return item
    }

    private fun buildInfoItem(material: Material, name: String, vararg loreLines: String): ItemStack {
        val item = ItemStack(material)
        TextBridge.setDisplayName(item, TextUtils.parseItem(name))
        @Suppress("UNCHECKED_CAST")
        TextBridge.setLore(item, TextUtils.parseItemLore(loreLines.toList()) as List<net.kyori.adventure.text.Component>)
        return item
    }

    private fun buildActionItem(material: Material, name: String, lore: List<String>, action: String): ItemStack {
        val item = ItemStack(material)
        TextBridge.setDisplayName(item, TextUtils.parseItem(name))
        @Suppress("UNCHECKED_CAST")
        TextBridge.setLore(item, TextUtils.parseItemLore(lore) as List<net.kyori.adventure.text.Component>)
        item.modifyMeta<ItemMeta> {
            persistentDataContainer.set(actionKey, PersistentDataType.STRING, action)
        }
        return item
    }

    private fun getAction(item: ItemStack?): String? {
        val meta = item?.itemMeta ?: return null
        return meta.persistentDataContainer.get(actionKey, PersistentDataType.STRING)
    }

    // ══════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════

    private fun formatTimestamp(millis: Long): String {
        if (millis <= 0) return "&7未知"
        return "&f${dateFormat.format(Instant.ofEpochMilli(millis))}"
    }

    // ══════════════════════════════════════
    // Data
    // ══════════════════════════════════════

    private data class Session(
        val viewer: UUID,
        val target: UUID,
        val type: SessionType
    )

    private enum class SessionType {
        DASHBOARD,
        INVENTORY,
        ENDER_CHEST
    }
}
