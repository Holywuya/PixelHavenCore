package com.pixlehavencore.feature.vanish

import com.pixlehavencore.util.broadcastToPermission
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
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

    /** 普通隐身玩家的 UUID 集合（Folia 线程安全：ConcurrentHashMap.keySet） */
    private val normalVanished: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

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
    private val plugin by lazy {
        Bukkit.getPluginManager().getPlugin("phcore") ?: Bukkit.getPluginManager().plugins.first()
    }

    fun init() {
        // 目前无需额外初始化；状态在内存中维护
    }

    // ------------------------------------------------------------------
    // 公开查询接口
    // ------------------------------------------------------------------

    fun isNormalVanished(player: Player): Boolean = player.uniqueId in normalVanished
    fun isAnyVanished(player: Player): Boolean = isNormalVanished(player)

    /** 返回所有普通隐身玩家（在线）的快照列表 */
    fun getNormalVanishedPlayers(): List<Player> =
        normalVanished.mapNotNull { Bukkit.getPlayer(it) }

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
        normalVanished.add(player.uniqueId)
        applyVanishToAllObservers(player)
        player.submitOnEntity {
            applyVanishingEffect(player)
        }
        notifyAdmins(player, vanishOn = true)
    }

    private fun disableNormalVanish(player: Player) {
        normalVanished.remove(player.uniqueId)
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
        // Folia: 对每个观察者在各自的区域线程上执行 hidePlayer
        Bukkit.getOnlinePlayers().toList().forEach { observer ->
            if (observer.uniqueId == target.uniqueId) return@forEach
            observer.submitOnEntity {
                // 普通隐身：有 see 权限的保持可见
                if (observer.hasPermission("phcore.vanish.see")) {
                    // see 权限者保持能看见（不做 hidePlayer）
                } else {
                    observer.hidePlayer(plugin, target)
                }
            }
        }
    }

    /**
     * 当 target 退出隐身时，对所有在线观察者恢复可见。
     */
    private fun revealToAllObservers(target: Player) {
        // Folia: 对每个观察者在各自的区域线程上执行 showPlayer
        Bukkit.getOnlinePlayers().toList().forEach { observer ->
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

            normalVanished.toList().mapNotNull { Bukkit.getPlayer(it) }.forEach { vanished ->
                if (vanished.uniqueId != observer.uniqueId) {
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
        normalVanished.remove(player.uniqueId)
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
        val message = template.replace("{player}", player.name)
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
