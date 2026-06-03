package com.pixlehavencore.feature.security

import com.pixlehavencore.util.PlaceholderUtils.resolvePlaceholders
import com.pixlehavencore.util.TextUtils
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import taboolib.common.platform.event.SubscribeEvent
import taboolib.platform.util.submit as submitOnEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object SecurityService {

    private val opened = ConcurrentHashMap<Int, UUID>()

    fun init() {
        SecuritySettings.init()
        opened.clear()
    }

    fun reload() {
        init()
    }

    fun stop() {
        opened.clear()
    }

    fun openInventory(viewer: Player, target: OfflinePlayer): Boolean {
        if (!SecuritySettings.enabled) return false
        val online = target.player ?: return false
        val title: Component = TextUtils.parse(SecuritySettings.invTitle.resolvePlaceholders("{player}" to (target.name ?: target.uniqueId.toString())))
        // 先在目标玩家所在线程抓取背包快照，再切到查看者线程开窗，避免跨线程直读 inventory。
        online.submitOnEntity {
            val contents = online.inventory.contents.copyOf(54)
            viewer.submitOnEntity {
                if (!viewer.isOnline) {
                    return@submitOnEntity
                }
                val inventory = Bukkit.createInventory(null as org.bukkit.inventory.InventoryHolder?, 54, title)
                inventory.setContents(contents.copyOf(54))
                opened[System.identityHashCode(inventory)] = viewer.uniqueId
                viewer.openInventory(inventory)
            }
        }
        return true
    }

    fun openEnderChest(viewer: Player, target: OfflinePlayer): Boolean {
        if (!SecuritySettings.enabled) return false
        val online = target.player ?: return false
        val title: Component = TextUtils.parse(SecuritySettings.ecTitle.resolvePlaceholders("{player}" to (target.name ?: target.uniqueId.toString())))
        // 同样先在目标线程读取末影箱，再回到查看者线程打开界面。
        online.submitOnEntity {
            val contents = online.enderChest.contents.copyOf(27)
            viewer.submitOnEntity {
                if (!viewer.isOnline) {
                    return@submitOnEntity
                }
                val inventory = Bukkit.createInventory(null as org.bukkit.inventory.InventoryHolder?, 27, title)
                inventory.setContents(contents.copyOf(27))
                opened[System.identityHashCode(inventory)] = viewer.uniqueId
                viewer.openInventory(inventory)
            }
        }
        return true
    }

    @SubscribeEvent
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val owner = opened[System.identityHashCode(event.view.topInventory)] ?: return
        if (owner == player.uniqueId) {
            event.isCancelled = true
        }
    }

    @SubscribeEvent
    fun onDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        val owner = opened[System.identityHashCode(event.view.topInventory)] ?: return
        if (owner == player.uniqueId && event.rawSlots.any { it < event.view.topInventory.size }) {
            event.isCancelled = true
        }
    }

    @SubscribeEvent
    fun onClose(event: InventoryCloseEvent) {
        // 清理 opened Map 防止内存泄漏
        opened.remove(System.identityHashCode(event.inventory))
    }
}
