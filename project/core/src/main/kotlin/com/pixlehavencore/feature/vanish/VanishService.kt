package com.pixlehavencore.feature.vanish

import com.pixlehavencore.util.PlaceholderUtils.resolvePlaceholders
import com.pixlehavencore.util.broadcastToPermission
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import taboolib.common.platform.function.onlinePlayers
import taboolib.platform.util.PlayerSessionMap
import taboolib.platform.util.submit as submitOnEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 隐身服务：负责管理玩家隐身状态，以及对观察者应用/撤销隐身效果。
 *
 * 状态分层：
 *   - normalVanished：普通隐身，对无 phcore.vanish.see 权限的玩家不可见
 *   - vanishViewers：某管理员通过 /vanish-show 临时看见了特定普通隐身玩家
 */
object VanishService {

    /** 普通隐身玩家（Folia 线程安全：PlayerSessionMap，true = 已隐身） */
    private val normalVanished = PlayerSessionMap<Boolean>({ false })

    /**
     * vanishViewers[observerUUID] = Set<targetUUID>
     * 记录某观察者通过 /vanish-show 显式解除了哪些普通隐身玩家的隐身效果。
     * 注意：仅适用于普通隐身玩家。
     * Folia 线程安全：外层 ConcurrentHashMap，内层 ConcurrentHashMap.newKeySet
     */
    private val vanishViewers = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    // ------------------------------------------------------------------
    // 插件实例（用于 hidePlayer / showPlayer API）
    // ------------------------------------------------------------------
    private lateinit var plugin: org.bukkit.plugin.Plugin

    fun init() {
        // Folia: 在 init()（onEnable 全局调度器）中安全获取插件实例
        plugin = Bukkit.getPluginManager().getPlugin("phcore") ?: Bukkit.getPluginManager().plugins.first()
    }

    // ------------------------------------------------------------------
    // 公开查询接口
    // ------------------------------------------------------------------

    fun isNormalVanished(player: Player): Boolean = normalVanished[player.uniqueId] == true
    fun isAnyVanished(player: Player): Boolean = isNormalVanished(player)

    /** 返回当前所有已隐身玩家的 UUID 集合快照 */
    private fun normalVanishedUuids(): Set<UUID> =
        normalVanished.entries().filter { pair: Pair<UUID, Boolean> -> pair.second }.map { it.first }.toSet()

    /** 返回所有普通隐身玩家（在线）的快照列表 */
    fun getNormalVanishedPlayers(): List<Player> {
        // Folia: 使用 onlinePlayers() 快照避免 Bukkit.getPlayer() 的线程安全问题
        val vanishedSet = normalVanishedUuids()
        if (vanishedSet.isEmpty()) return emptyList()
        return onlinePlayers().mapNotNull { proxy ->
            proxy.cast<Player>()?.takeIf { it.uniqueId in vanishedSet }
        }
    }

    // ------------------------------------------------------------------
    // 隐身切换
    // ------------------------------------------------------------------

    /**
     * 切换普通隐身状态。
     * @return true 表示现在已隐身，false 表示已现身
     */
    fun toggleNormalVanish(player: Player): Boolean {
        return if (isNormalVanished(player)) {
            disableNormalVanish(player)
            false
        } else {
            enableNormalVanish(player)
            true
        }
    }

    // ------------------------------------------------------------------
    // 内部状态变更
    // ------------------------------------------------------------------

    private fun enableNormalVanish(player: Player) {
        normalVanished[player.uniqueId] = true
        applyVanishToAllObservers(player)
        player.submitOnEntity {
            applyVanishingEffect(player)
        }
        notifyAdmins(player, vanishOn = true)
    }

    private fun disableNormalVanish(player: Player) {
        normalVanished[player.uniqueId] = false
        // 清理该玩家在所有观察者的 viewerMap 中的记录
        vanishViewers.values.forEach { it.remove(player.uniqueId) }
        revealToAllObservers(player)
        player.submitOnEntity {
            removeVanishingEffect(player)
        }
        notifyAdmins(player, vanishOn = false)
    }

    // ------------------------------------------------------------------
    // 对观察者应用/撤销隐身
    // ------------------------------------------------------------------

    /**
     * 当 target 进入隐身时，对所有在线观察者（除自身）隐藏/显示 target。
     * 普通隐身：只对无 see 权限者隐藏。
     */
    private fun applyVanishToAllObservers(target: Player) {
        // Folia: 使用 onlinePlayers() 快照 + submitOnEntity 在各自区域线程执行 hidePlayer
        onlinePlayers().mapNotNull { it.cast<Player>() }.forEach { observer ->
            if (observer.uniqueId == target.uniqueId) return@forEach
            observer.submitOnEntity {
                if (!observer.hasPermission("phcore.vanish.see")) {
                    observer.hidePlayer(plugin, target)
                }
            }
        }
    }

    /**
     * 当 target 退出隐身时，对所有在线观察者恢复可见。
     */
    private fun revealToAllObservers(target: Player) {
        // Folia: 使用 onlinePlayers() 快照 + submitOnEntity 在各自区域线程执行 showPlayer
        onlinePlayers().mapNotNull { it.cast<Player>() }.forEach { observer ->
            if (observer.uniqueId == target.uniqueId) return@forEach
            observer.submitOnEntity {
                observer.showPlayer(plugin, target)
            }
        }
    }

    // ------------------------------------------------------------------
    // 新玩家加入时的处理：对新玩家隐藏当前所有隐身玩家
    // ------------------------------------------------------------------

    /**
     * 当新玩家加入服务器时，对其隐藏所有当前隐身玩家（根据权限区分）。
     * 须在 PlayerJoinEvent 中调用。
     */
    fun applyVanishToNewObserver(observer: Player) {
        observer.submitOnEntity {
            val hasSeePermission = observer.hasPermission("phcore.vanish.see")
            if (hasSeePermission) {
                return@submitOnEntity
            }

            // Folia: 使用 onlinePlayers() 快照安全查找，避免 Bukkit.getPlayer() 的线程安全问题
            val vanishedSet = normalVanishedUuids()
            if (vanishedSet.isEmpty()) return@submitOnEntity
            onlinePlayers().mapNotNull { it.cast<Player>() }.forEach { vanished ->
                if (vanished.uniqueId in vanishedSet && vanished.uniqueId != observer.uniqueId) {
                    observer.hidePlayer(plugin, vanished)
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // /vanish-show 功能
    // ------------------------------------------------------------------

    /**
     * 让 observer 临时看见普通隐身的 target。
     * @return ShowResult 枚举
     */
    fun showPlayerTo(observer: Player, target: Player): ShowResult {
        return when {
            !isNormalVanished(target) -> ShowResult.NOT_VANISHED
            else -> {
                vanishViewers.getOrPut(observer.uniqueId) { ConcurrentHashMap.newKeySet() }.add(target.uniqueId)
                observer.submitOnEntity {
                    observer.showPlayer(plugin, target)
                }
                ShowResult.OK
            }
        }
    }

    /**
     * 让 observer 临时看见所有普通隐身玩家。
     * @return 实际显示的玩家数量
     */
    fun showAllNormalVanishedTo(observer: Player): Int {
        val targets = getNormalVanishedPlayers()
        observer.submitOnEntity {
            targets.forEach { target ->
                vanishViewers.getOrPut(observer.uniqueId) { ConcurrentHashMap.newKeySet() }.add(target.uniqueId)
                observer.showPlayer(plugin, target)
            }
        }
        return targets.size
    }

    // ------------------------------------------------------------------
    // 玩家退出时清理状态
    // ------------------------------------------------------------------

    /**
     * 玩家退出服务器时调用：从所有状态集合中清除其记录。
     * 同时也清理该玩家作为观察者的 viewerMap。
     */
    fun handlePlayerQuit(player: Player) {
        // normalVanished 由 PlayerSessionMap 自动清理，无需手动 remove
        vanishViewers.remove(player.uniqueId)
        // 从其他观察者的 viewerMap 中也清除（避免内存泄漏）
        vanishViewers.values.forEach { it.remove(player.uniqueId) }
    }

    // ------------------------------------------------------------------
    // 管理员通知
    // ------------------------------------------------------------------

    private fun notifyAdmins(player: Player, vanishOn: Boolean) {
        if (!VanishSettings.enabled) return
        val template = if (vanishOn) VanishSettings.msgAdminNotifyOn else VanishSettings.msgAdminNotifyOff
        val message = template.resolvePlaceholders("{player}" to player.name)
        broadcastToPermission(message, "phcore.vanish.notify", exclude = player.uniqueId)
    }

    private fun applyVanishingEffect(player: Player) {
        val effectType = PotionEffectType.INVISIBILITY ?: return
        player.addPotionEffect(
            PotionEffect(
                effectType,
                Int.MAX_VALUE,
                0,
                false,
                false,
                false
            )
        )
    }

    private fun removeVanishingEffect(player: Player) {
        val effectType = PotionEffectType.INVISIBILITY ?: return
        if (player.hasPotionEffect(effectType)) {
            player.removePotionEffect(effectType)
        }
    }

    // ------------------------------------------------------------------
    // 枚举
    // ------------------------------------------------------------------

    enum class ShowResult {
        OK,
        NOT_VANISHED
    }
}
