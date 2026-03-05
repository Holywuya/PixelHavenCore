package com.pixlehavencore.feature.helpinterceptor

import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.info
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import java.io.File

object HelpInterceptorSettings {

    @Config("feature/help-interceptor.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    var customHelpMessage: List<String> = listOf(
        "&6=== 服务器帮助 ===",
        "&e欢迎来到我们的服务器！",
        "&e如需更多帮助，请联系管理员。",
        "&6==================="
    )
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        customHelpMessage = config.getStringList("custom-help-message").map { it.replace("&", "§") }
    }
}