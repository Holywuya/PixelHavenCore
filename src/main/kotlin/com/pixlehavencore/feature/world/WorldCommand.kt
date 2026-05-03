package com.pixlehavencore.feature.world

import com.pixlehavencore.feature.chat.WorldNameMapper
import com.pixlehavencore.util.PlaceholderUtils.resolvePlaceholders
import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePermission
import com.pixlehavencore.util.requirePlayer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.suggestPlayers
import taboolib.common.platform.command.subCommand


@CommandHeader(name = "world", aliases = ["mfw"], permissionDefault = PermissionDefault.TRUE)
object WorldCommand {

    private fun suggestWorldIds(): List<String> {
        val configured = WorldSettings.allWorldNames()
        return if (WorldSettings.allowUnlistedTeleport) {
            (configured + Bukkit.getWorlds().map { it.name }).distinct()
        } else {
            configured
        }
    }

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.msg("&6=== 世界模块帮助 ===")
            sender.msg("&b/world list &7- 查看可用世界")
            sender.msg("&b/world teleport <世界> [玩家] &7- 传送到指定世界")
            sender.msg("&b/world load <世界> &7- 手动加载指定世界")
            sender.msg("&b/world reload &7- 重载世界模块配置")
            sender.msg("&7当前状态：&f${if (WorldService.isEnabled()) "已启用" else "未启用"}")
        }
    }

    @CommandBody
    val list = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.msg("&6=== 已配置世界 ===")
            WorldSettings.allWorldNames().forEach { worldName ->
                val state = if (Bukkit.getWorld(worldName) != null) "&a已加载" else "&e未加载"
                sender.msg("&7- &f$worldName &7($state)")
            }
        }
    }

    @CommandBody
    val teleport = subCommand {
        dynamic(comment = "world") {
            dynamic(comment = "player", optional = true) {
                suggestPlayers()
                execute<ProxyCommandSender> { sender, context, argument ->
                    val worldName = context.getOrNull("world")?.toString().orEmpty().trim()
                    if (worldName.isBlank()) {
                        sender.msg("&c请输入目标世界。")
                        return@execute
                    }
                    val targetName = argument.toString().trim()
                    if (targetName.isBlank()) {
                        // 未指定目标玩家：发送者必须是玩家，传送自己
                        val player = sender.requirePlayer()?.cast<Player>() ?: return@execute
                        if (!WorldService.teleportSelf(player, worldName)) {
                            sender.msg(WorldSettings.messageModuleDisabled)
                        }
                    } else {
                        // 指定目标玩家：发送者无需是玩家（支持 NPC/控制台调用）
                        if (!sender.requirePermission("phcore.admin")) {
                            return@execute
                        }
                        val target = Bukkit.getPlayerExact(targetName)
                        if (target == null) {
                            sender.msg(WorldSettings.messagePlayerOffline)
                            return@execute
                        }
                        if (!WorldService.teleportOther(target, worldName)) {
                            sender.msg(WorldSettings.messageModuleDisabled)
                        } else {
                            sender.msg(WorldSettings.messageTeleportOther.resolvePlaceholders(
                                "{player}" to target.name,
                                "{world}" to WorldNameMapper.resolve(worldName)
                            ))
                        }
                    }
                }
            }
            execute<ProxyCommandSender> { sender, context, _ ->
                val player = sender.requirePlayer()?.cast<Player>() ?: return@execute
                val worldName = context.getOrNull("world")?.toString().orEmpty().trim()
                if (worldName.isBlank()) {
                    sender.msg("&c请输入目标世界。")
                    return@execute
                }
                if (!WorldService.teleportSelf(player, worldName)) {
                    sender.msg(WorldSettings.messageModuleDisabled)
                }
            }
        }
    }

    @CommandBody
    val load = subCommand {
        dynamic(comment = "world") {
            execute<ProxyCommandSender> { sender, _, argument ->
                if (!sender.requirePermission("phcore.admin")) return@execute
                val worldName = argument.toString().trim()
                if (worldName.isBlank()) {
                    sender.msg("&c请输入世界名。")
                    return@execute
                }
                val world = WorldService.ensureWorldPresent(worldName) ?: Bukkit.getWorld(worldName)
                    ?: runCatching {
                        Bukkit.createWorld(org.bukkit.WorldCreator(worldName))
                    }.getOrNull()
                if (world == null) {
                    sender.msg(WorldSettings.messageWorldMissing.resolvePlaceholders("{world}" to worldName))
                    return@execute
                }
                sender.msg("&a世界已加载：&f${world.name}")
            }
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission("phcore.admin")) return@execute
            WorldService.reload()
            sender.msg(WorldSettings.messageReloadSuccess)
        }
    }
}
