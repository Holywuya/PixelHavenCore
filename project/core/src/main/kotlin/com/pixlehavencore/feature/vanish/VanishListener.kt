package com.pixlehavencore.feature.vanish

import com.pixlehavencore.util.PlaceholderUtils.resolvePlaceholders
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.platform.event.SubscribeEvent
import com.pixlehavencore.util.TextUtils

/**
 * 隐身模块事件监听器：
 * 1. PlayerJoinEvent  — 对新玩家隐藏所有隐身玩家；若该玩家本身处于隐身状态，广播假的退出消息并压制真实加入消息。
 * 2. PlayerQuitEvent  — 若玩家退出时处于隐身状态，压制退出消息；清理内存状态。
 */
object VanishListener {

    @SubscribeEvent
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!VanishSettings.enabled) return
        val player = event.player

        // 1. 对新加入的观察者隐藏所有当前隐身玩家
        VanishService.applyVanishToNewObserver(player)

        // 2. 若玩家本身处于隐身状态（例如服务器重启前已隐身，此处为重连恢复场景）
        //    发送假的退出消息，并压制真实加入消息
        if (VanishService.isAnyVanished(player)) {
            // 压制真实加入消息（反射兼容新旧 Paper API）
            suppressJoinMessage(event)

            // 若配置开启，对其他玩家广播假的退出消息
            if (VanishSettings.fakeJoinSendFakeQuit) {
                val fakeMsg = TextUtils.parse(
                    VanishSettings.fakeQuitFormat
                        .resolvePlaceholders("{player}" to player.name)
                )
                player.server.broadcast(fakeMsg)
            }
        }
    }

    @SubscribeEvent
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (!VanishSettings.enabled) {
            // 即使模块禁用也要清理状态，防止内存泄漏
            VanishService.handlePlayerQuit(event.player)
            return
        }

        val player = event.player
        val wasVanished = VanishService.isAnyVanished(player)

        // 先清理服务层状态（在广播假消息之前，避免假消息被自己接收）
        VanishService.handlePlayerQuit(player)

        if (wasVanished && VanishSettings.fakeQuitSilent) {
            // 压制真实退出消息（反射兼容新旧 Paper API）
            suppressQuitMessage(event)
        }
    }

    // ------------------------------------------------------------------
    // 私有辅助：压制消息（使用反射兼容所有 Paper/Spigot 版本）
    // ------------------------------------------------------------------

    private fun suppressJoinMessage(event: PlayerJoinEvent) {
        event.joinMessage = null
    }

    private fun suppressQuitMessage(event: PlayerQuitEvent) {
        event.quitMessage = null
    }
}
