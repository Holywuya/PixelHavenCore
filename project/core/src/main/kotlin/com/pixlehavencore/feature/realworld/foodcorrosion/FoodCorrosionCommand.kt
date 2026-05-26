package com.pixlehavencore.feature.realworld.foodcorrosion

import com.pixlehavencore.util.msg
import taboolib.common.platform.ProxyCommandSender

object FoodCorrosionCommand {

    fun sendStatus(sender: ProxyCommandSender) {
        sender.msg("&6=== 食物腐蚀状态 ===")
        sender.msg("&7启用: &f${if (FoodCorrosionSettings.enabled) "是" else "否"}")
        sender.msg("&7腐蚀最大值: &f${FoodCorrosionSettings.maxCorrosion}")
        sender.msg("&7默认速率: &f${FoodCorrosionSettings.defaultRate} 点/Tick")
        sender.msg("&7总天数: &f${FoodCorrosionSettings.totalDays}")
        sender.msg("&7Lore格式: &f${FoodCorrosionSettings.loreFormat}")
        sender.msg("&7Packet监听: &f${if (FoodCorrosionService.isAvailable()) "已注册" else "未注册"}")
        sender.msg("&7排除物品: &f${FoodCorrosionSettings.excludedItems.joinToString(", ")}")
        if (FoodCorrosionSettings.itemRates.isNotEmpty()) {
            sender.msg("&7专属速率:")
            FoodCorrosionSettings.itemRates.forEach { (material, rate) ->
                sender.msg("&7  $material: &f$rate")
            }
        }
    }
}
