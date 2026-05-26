package com.pixlehavencore.feature.realworld.foodcorrosion

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListenerCommon
import com.github.retrooper.packetevents.event.PacketListenerPriority
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning

object FoodCorrosionService {

    private var listenerHandle: PacketListenerCommon? = null

    fun init() {
        if (!FoodCorrosionSettings.enabled) return
        registerPacketListener()
        info("[FoodCorrosion] 食物腐蚀模块已启动")
    }

    fun stop() {
        unregisterPacketListener()
    }

    fun reload() {
        unregisterPacketListener()
        if (!FoodCorrosionSettings.enabled) return
        registerPacketListener()
        info("[FoodCorrosion] 食物腐蚀模块已重载")
    }

    fun isAvailable(): Boolean = listenerHandle != null

    private fun registerPacketListener() {
        runCatching {
            val api = PacketEvents.getAPI()
            if (!api.isInitialized) {
                warning("[FoodCorrosion] PacketEvents 未初始化，跳过 Lore 显示注册")
                return
            }
            listenerHandle = api.eventManager.registerListener(
                FoodCorrosionPacketListener,
                PacketListenerPriority.NORMAL,
            )
            info("[FoodCorrosion] Packet 监听器已注册")
        }.onFailure { ex ->
            warning("[FoodCorrosion] 注册失败: ${ex.message}")
        }
    }

    private fun unregisterPacketListener() {
        runCatching {
            val handle = listenerHandle ?: return
            PacketEvents.getAPI().eventManager.unregisterListener(handle)
            listenerHandle = null
        }
    }
}
