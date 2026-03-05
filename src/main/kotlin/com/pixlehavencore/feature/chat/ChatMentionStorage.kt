package com.pixlehavencore.feature.chat

import com.pixlehavencore.util.ensureDataContainer
import taboolib.common.platform.ProxyPlayer
import taboolib.expansion.getDataContainer

object ChatMentionStorage {

    private const val KEY_OPT_OUT = "chat_mention_opt_out"

    fun isOptOut(player: ProxyPlayer): Boolean {
        ensureContainer(player)
        val container = player.getDataContainer()
        return container[KEY_OPT_OUT]?.toBoolean() ?: false
    }

    fun setOptOut(player: ProxyPlayer, value: Boolean) {
        ensureContainer(player)
        val container = player.getDataContainer()
        container[KEY_OPT_OUT] = value
    }

    fun toggleOptOut(player: ProxyPlayer): Boolean {
        val next = !isOptOut(player)
        setOptOut(player, next)
        return next
    }

    private fun ensureContainer(player: ProxyPlayer) = player.ensureDataContainer()
}
