package com.pixlehavencore.feature.base

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object BackSettings {

    @Config("feature/base-command.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    var cooldownSeconds: Int = 30
        private set

    var warmupSeconds: Int = 3
        private set

    var cancelOnMove: Boolean = true
        private set

    var cancelOnDamage: Boolean = true
        private set

    var unsafeTeleport: Boolean = false
        private set

    var msgModuleDisabled: String = "&c基础模块已禁用。"
        private set

    var msgNoLocation: String = "&c没有可返回的位置。"
        private set

    var msgCooldown: String = "&c请等待 {time} 秒后再使用。"
        private set

    var msgWarmupStarting: String = "&a将在 {time} 秒后传送... 请勿移动"
        private set

    var msgWarmupCancelled: String = "&c传送已取消！"
        private set

    var msgTeleported: String = "&a已传送到上一个位置。"
        private set

    var msgAlreadyWarmingUp: String = "&c传送预热中，请稍候。"
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        cooldownSeconds = config.getInt("back.cooldownSeconds", 30)
        warmupSeconds = config.getInt("back.warmupSeconds", 3)
        cancelOnMove = config.getBoolean("back.cancelOnMove", true)
        cancelOnDamage = config.getBoolean("back.cancelOnDamage", true)
        unsafeTeleport = config.getBoolean("back.unsafeTeleport", false)
        msgModuleDisabled = config.getString("messages.moduleDisabled") ?: "&c基础模块已禁用。"
        msgNoLocation = config.getString("back.msgNoLocation") ?: "&c没有可返回的位置。"
        msgCooldown = config.getString("back.msgCooldown") ?: "&c请等待 {time} 秒后再使用。"
        msgWarmupStarting = config.getString("back.msgWarmupStarting") ?: "&a将在 {time} 秒后传送... 请勿移动"
        msgWarmupCancelled = config.getString("back.msgWarmupCancelled") ?: "&c传送已取消！"
        msgTeleported = config.getString("back.msgTeleported") ?: "&a已传送到上一个位置。"
        msgAlreadyWarmingUp = config.getString("back.msgAlreadyWarmingUp") ?: "&c传送预热中，请稍候。"
    }
}
