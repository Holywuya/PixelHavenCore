package com.pixlehavencore.feature.durability

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListenerCommon
import com.github.retrooper.packetevents.event.PacketListenerPriority
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning

object DurabilityService {

    private var listenerHandle: PacketListenerCommon? = null

    fun init() {
        if (!DurabilitySettings.enabled) return
        runCatching {
            val api = PacketEvents.getAPI()
            if (!api.isInitialized) {
                warning("[Durability] PacketEvents 未初始化，跳过注册")
                return
            }
            listenerHandle = api.eventManager.registerListener(
                DurabilityPacketListener,
                PacketListenerPriority.NORMAL,
            )
            info("[Durability] 耐久显示模块已注册")
        }.onFailure { ex ->
            warning("[Durability] 注册失败: ${ex.message}")
        }
    }

    fun stop() {
        runCatching {
            val handle = listenerHandle ?: return
            PacketEvents.getAPI().eventManager.unregisterListener(handle)
            listenerHandle = null
        }
    }

    fun isAvailable(): Boolean = listenerHandle != null
}
