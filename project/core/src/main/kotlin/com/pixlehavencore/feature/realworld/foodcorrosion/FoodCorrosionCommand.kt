package com.pixlehavencore.feature.realworld.foodcorrosion

import com.pixlehavencore.util.msg
import taboolib.common.platform.ProxyCommandSender

object FoodCorrosionCommand {

    fun sendStatus(sender: ProxyCommandSender) {
        sender.msg("<gold>=== 食物腐蚀状态 ===")
        sender.msg("<gray>启用: <white>${if (FoodCorrosionSettings.enabled) "是" else "否"}")
        sender.msg("<gray>默认保质期: <white>${FoodCorrosionSettings.defaultDays} 天")
        sender.msg("<gray>过期物品: <white>${FoodCorrosionSettings.expiredItem}")
        sender.msg("<gray>Lore格式: <white>${FoodCorrosionSettings.loreFormat}")
        sender.msg("<gray>Packet监听: <white>${if (FoodCorrosionService.isAvailable()) "已注册" else "未注册"}")
        sender.msg("<gray>排除物品: <white>${FoodCorrosionSettings.excludedItems.joinToString(", ")}")
        if (FoodCorrosionSettings.itemDays.isNotEmpty()) {
            sender.msg("<gray>专属保质期:")
            FoodCorrosionSettings.itemDays.forEach { (material, days) ->
                sender.msg("<gray>  $material: <white>${days}天")
            }
        }
    }
}
