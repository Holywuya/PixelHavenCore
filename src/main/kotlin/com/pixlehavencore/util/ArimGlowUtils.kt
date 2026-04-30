package com.pixlehavencore.util

import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import taboolib.common.platform.function.warning
import top.maplex.arim.Arim

/**
 * 统一封装 Arim Glow，减少业务模块直接依赖底层实现。
 */
object ArimGlowUtils {

    fun setEntityGlowing(entity: Entity, receiver: Player, color: NamedTextColor?) {
        runCatching {
            Arim.glow.setGlowing(entity, receiver, color)
        }.onFailure { ex ->
            warning("[ArimGlow] 设置实体发光失败(entity=${entity.uniqueId}, receiver=${receiver.name}): ${ex.message}")
        }
    }

    fun setEntityGlowingForAll(entity: Entity, color: NamedTextColor?) {
        Bukkit.getOnlinePlayers().toList().forEach { player ->
            setEntityGlowing(entity, player, color)
        }
    }
}
