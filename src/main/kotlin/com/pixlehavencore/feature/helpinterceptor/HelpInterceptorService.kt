package com.pixlehavencore.feature.helpinterceptor

import org.bukkit.event.player.PlayerCommandPreprocessEvent
import taboolib.common.platform.event.SubscribeEvent

object HelpInterceptorService {

    fun init() {
        if (!HelpInterceptorSettings.enabled) {
            return
        }
        // 事件监听器会自动注册
    }

    @SubscribeEvent
    fun onPlayerCommand(event: PlayerCommandPreprocessEvent) {
        val message = event.message.lowercase().trim()

        // 检查是否是/help指令（包括/help, /minecraft:help等）
        if (message == "/help" || message == "/minecraft:help" || message.startsWith("/help ") || message.startsWith("/minecraft:help ")) {
            event.isCancelled = true

            // 发送自定义帮助消息
            HelpInterceptorSettings.customHelpMessage.forEach { line ->
                event.player.sendMessage(line)
            }
        }
    }
}