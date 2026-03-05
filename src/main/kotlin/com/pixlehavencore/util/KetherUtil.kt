package com.pixlehavencore.util

import org.bukkit.entity.Player
import taboolib.module.kether.KetherShell
import taboolib.module.kether.ScriptOptions

object KetherUtil {

    private val defaultNamespace = listOf("kether")

    fun eval(player: Player, script: String, vars: Map<String, Any?> = emptyMap()) {
        val source = script.trim()
        if (source.isEmpty()) {
            return
        }
        val options = ScriptOptions.builder()
            .namespace(defaultNamespace)
            .sender(player)
            .vars(vars)
            .sandbox(true)
            .build()
        KetherShell.eval(source, options)
    }
}
