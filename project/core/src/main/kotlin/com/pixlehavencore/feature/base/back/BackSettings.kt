package com.pixlehavencore.feature.base.back

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object BackSettings {

    @Config("feature/base/back.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    var warmupSeconds: Int = 3
        private set

    var cancelOnMove: Boolean = true
        private set

    var cancelOnDamage: Boolean = true
        private set

    var unsafeTeleport: Boolean = false
        private set

    var msgNoLocation: String = "&c没有可返回的位置。"
        private set

    var msgWarmupStarting: String = "&a将在 {time} 秒后传送... 请勿移动"
        private set

    var msgWarmupCancelled: String = "&c传送已取消！"
        private set

    var msgTeleported: String = "&a已传送到死亡位置。"
        private set

    var msgAlreadyWarmingUp: String = "&c传送预热中，请稍候。"
        private set

    var msgDeathButton: String = "&c你已死亡！ &a[点击此处返回死亡位置]"
        private set

    var msgDeathHover: String = "&a点击回到死亡点"
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        warmupSeconds = config.getInt("warmupSeconds", 3)
        cancelOnMove = config.getBoolean("cancelOnMove", true)
        cancelOnDamage = config.getBoolean("cancelOnDamage", true)
        unsafeTeleport = config.getBoolean("unsafeTeleport", false)
        msgNoLocation = config.getString("msgNoLocation") ?: "&c没有可返回的位置。"
        msgWarmupStarting = config.getString("msgWarmupStarting") ?: "&a将在 {time} 秒后传送... 请勿移动"
        msgWarmupCancelled = config.getString("msgWarmupCancelled") ?: "&c传送已取消！"
        msgTeleported = config.getString("msgTeleported") ?: "&a已传送到死亡位置。"
        msgAlreadyWarmingUp = config.getString("msgAlreadyWarmingUp") ?: "&c传送预热中，请稍候。"
        msgDeathButton = config.getString("msgDeathButton") ?: "&c你已死亡！ &a[点击此处返回死亡位置]"
        msgDeathHover = config.getString("msgDeathHover") ?: "&a点击回到死亡点"
    }
}
