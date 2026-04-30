package com.pixlehavencore.feature.chat

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object SimpleChatState {

    /** 最近私聊对象映射：A->B, B->A */
    val lastMessageSender: MutableMap<UUID, UUID> = ConcurrentHashMap()

    /** @提醒音效冷却 */
    val atSoundCooldowns: MutableMap<UUID, Long> = ConcurrentHashMap()
}
