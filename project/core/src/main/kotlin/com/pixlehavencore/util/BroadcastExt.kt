package com.pixlehavencore.util

import com.pixlehavencore.bridge.TextBridge
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import taboolib.common.platform.function.onlinePlayers
import taboolib.common.platform.function.submit
import taboolib.platform.util.submit as submitOnEntity
import java.util.UUID

/**
 * 广播带颜色代码的消息给所有在线玩家。
 * 替换 NotificationService 中的私有 broadcastMessage()。
 */
fun broadcastColored(message: String) {
    broadcastComponent(TextUtils.parse(message))
}

fun broadcastComponent(component: Component) {
    submit {
        onlinePlayers().toList().forEach { proxy ->
            val player = proxy.cast<Player>() ?: return@forEach
            player.submitOnEntity {
                player.sendMessage(component)
            }
        }
    }
}

/**
 * 广播带颜色代码的消息给拥有指定权限的所有在线玩家，
 * 可选地排除特定 UUID（例如隐身通知中排除隐身玩家自身）。
 *
 * 替换 VanishService.notifyAdmins() 中的内联广播逻辑。
 */
fun broadcastToPermission(message: String, permission: String, exclude: UUID? = null) {
    val component = TextUtils.parse(message)
    submit {
        onlinePlayers().toList().forEach { proxy ->
            if (exclude != null && proxy.uniqueId == exclude) return@forEach
            if (!proxy.hasPermission(permission)) return@forEach
            val player = proxy.cast<Player>() ?: return@forEach
            player.submitOnEntity {
                player.sendMessage(component)
            }
        }
    }
}
