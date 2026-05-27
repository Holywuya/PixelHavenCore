package com.pixlehavencore.feature.realworld.foodcorrosion

import com.pixlehavencore.util.msg
import taboolib.common.platform.ProxyCommandSender

object FoodCorrosionCommand {

    fun sendStatus(sender: ProxyCommandSender) {
        sender.msg("&6=== 食物腐蚀状态 ===")
        sender.msg("&7启用: &f${if (FoodCorrosionSettings.enabled) "是" else "否"}")
        sender.msg("&7默认保质期: &f${FoodCorrosionSettings.defaultDays} 天")
        sender.msg("&7过期物品: &f${FoodCorrosionSettings.expiredItem}")
        sender.msg("&7Lore格式: &f${FoodCorrosionSettings.loreFormat}")
        sender.msg("&7Packet监听: &f${if (FoodCorrosionService.isAvailable()) "已注册" else "未注册"}")
        sender.msg("&7排除物品: &f${FoodCorrosionSettings.excludedItems.joinToString(", ")}")
        if (FoodCorrosionSettings.itemDays.isNotEmpty()) {
            sender.msg("&7专属保质期:")
            FoodCorrosionSettings.itemDays.forEach { (material, days) ->
                sender.msg("&7  $material: &f${days}天")
            }
        }
    }
}
