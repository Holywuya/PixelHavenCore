package com.pixlehavencore.feature.title

import com.pixlehavencore.util.ADMIN_PERMISSION
import com.pixlehavencore.util.PlaceholderUtils.resolvePlaceholders
import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePermission
import com.pixlehavencore.util.requirePlayer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.function.onlinePlayers
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand

@CommandHeader(name = "title", aliases = ["titles", "ch"], permissionDefault = PermissionDefault.TRUE)
object TitleCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.msg("<dark_gray>[<gold>称号系统<dark_gray>] <gray>使用 /title help 查看帮助")
        }
    }

    @CommandBody
    val help = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.msg("<dark_gray>=== <gold>称号系统帮助 <dark_gray>===")
            sender.msg("<yellow>/title open <gray>- 打开称号选择界面")
            sender.msg("<yellow>/title equip <id> <gray>- 装备指定称号")
            sender.msg("<yellow>/title unequip <gray>- 卸下当前称号")
            sender.msg("<yellow>/title list <gray>- 查看拥有的称号")
            sender.msg("<yellow>/title give <玩家> <id> [时长] <gray>- 发放称号 (管理员)")
            sender.msg("<yellow>/title take <玩家> <id> <gray>- 移除称号 (管理员)")
            sender.msg("<yellow>/title reload <gray>- 重载配置 (管理员)")
        }
    }

    @CommandBody
    val open = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!TitleSettings.enabled) {
                sender.msg("<red>称号模块未启用。")
                return@execute
            }
            val player = sender.requirePlayer() ?: return@execute
            TitleMenu.open(player.cast<Player>())
        }
    }

    @CommandBody
    val equip = subCommand {
        dynamic(comment = "titleId") {
            suggestion<ProxyCommandSender> { _, _ -> TitleService.getAllTitleIds() }
            execute<ProxyCommandSender> { sender, context, _ ->
                if (!TitleSettings.enabled) {
                    sender.msg("<red>称号模块未启用。")
                    return@execute
                }
                val player = sender.requirePlayer() ?: return@execute
                val titleId = context.getOrNull("titleId")?.toString() ?: return@execute
                val result = TitleService.activateTitle(player.cast<Player>(), titleId)
                when (result) {
                    TitleResult.Success -> {}
                    TitleResult.NotOwned -> sender.msg(TitleSettings.msgNotOwned)
                    TitleResult.Expired -> sender.msg(TitleSettings.msgExpired)
                    TitleResult.NoPermission -> sender.msg(TitleSettings.msgNoPermission)
                    TitleResult.NotFound -> sender.msg("<red>称号不存在。")
                    else -> sender.msg("<red>操作失败。")
                }
            }
        }
    }

    @CommandBody
    val unequip = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!TitleSettings.enabled) {
                sender.msg("<red>称号模块未启用。")
                return@execute
            }
            val player = sender.requirePlayer() ?: return@execute
            TitleService.deactivateTitle(player.cast<Player>())
        }
    }

    @CommandBody
    val list = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!TitleSettings.enabled) {
                sender.msg("<red>称号模块未启用。")
                return@execute
            }
            val player = sender.requirePlayer() ?: return@execute
            val previews = TitleService.getTitlePreviews(player.cast<Player>())
            if (previews.isEmpty()) {
                sender.msg("<gray>你还没有任何称号。")
                return@execute
            }
            sender.msg("<dark_gray>=== <gold>拥有的称号 <dark_gray>===")
            previews.forEach { preview ->
                val activeTag = if (preview.isActive) " <green>[已装备]" else ""
                sender.msg("<gray>- ${preview.definition.displayName}$activeTag")
            }
        }
    }

    @CommandBody
    val give = subCommand {
        dynamic(comment = "player") {
            suggestion<ProxyCommandSender> { _, _ -> onlinePlayers().mapNotNull { it.cast<Player>()?.name } }
            dynamic(comment = "titleId") {
                suggestion<ProxyCommandSender> { _, _ -> TitleService.getAllTitleIds() }
                execute<ProxyCommandSender> { sender, context, _ ->
                    if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                    val playerName = context.getOrNull("player")?.toString() ?: return@execute
                    val titleId = context.getOrNull("titleId")?.toString() ?: return@execute
                    handleGive(sender, playerName, titleId, "permanent")
                }
                dynamic(comment = "duration") {
                    execute<ProxyCommandSender> { sender, context, _ ->
                        if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                        val playerName = context.getOrNull("player")?.toString() ?: return@execute
                        val titleId = context.getOrNull("titleId")?.toString() ?: return@execute
                        val duration = context.getOrNull("duration")?.toString() ?: "permanent"
                        handleGive(sender, playerName, titleId, duration)
                    }
                }
            }
        }
    }

    @CommandBody
    val take = subCommand {
        dynamic(comment = "player") {
            suggestion<ProxyCommandSender> { _, _ -> onlinePlayers().mapNotNull { it.cast<Player>()?.name } }
            dynamic(comment = "titleId") {
                suggestion<ProxyCommandSender> { _, _ -> TitleService.getAllTitleIds() }
                execute<ProxyCommandSender> { sender, context, _ ->
                    if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                    val playerName = context.getOrNull("player")?.toString() ?: return@execute
                    val titleId = context.getOrNull("titleId")?.toString() ?: return@execute
                    handleTake(sender, playerName, titleId)
                }
            }
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
            TitleService.reload()
            sender.msg(TitleSettings.msgReload)
        }
    }

    private fun handleGive(sender: ProxyCommandSender, playerName: String, titleId: String, durationStr: String) {
        if (!TitleSettings.enabled) {
            sender.msg("<red>称号模块未启用。")
            return
        }
        val duration = TitleService.parseDuration(durationStr)
        val expiresAt = if (duration == 0L) 0L else System.currentTimeMillis() + duration
        val target = onlinePlayers().mapNotNull { it.cast<Player>() }.find { it.name.equals(playerName, ignoreCase = true) }
        val targetUuid = target?.uniqueId ?: Bukkit.getOfflinePlayer(playerName).uniqueId
        val result = if (target != null) {
            TitleService.grantTitle(target, titleId, expiresAt)
        } else {
            TitleService.grantTitleOffline(targetUuid, playerName, titleId, expiresAt)
        }
        when (result) {
            TitleResult.Success -> {
                val def = TitleSettings.getTitle(titleId)
                val msg = TitleSettings.msgGiven
                    .resolvePlaceholders("{title}" to (def?.displayName ?: titleId), "{player}" to playerName)
                sender.msg(msg)
            }
            TitleResult.NotFound -> sender.msg("<red>称号不存在。")
            else -> sender.msg("<red>操作失败。")
        }
    }

    private fun handleTake(sender: ProxyCommandSender, playerName: String, titleId: String) {
        if (!TitleSettings.enabled) {
            sender.msg("<red>称号模块未启用。")
            return
        }
        val target = onlinePlayers().mapNotNull { it.cast<Player>() }.find { it.name.equals(playerName, ignoreCase = true) }
        val targetUuid = target?.uniqueId ?: Bukkit.getOfflinePlayer(playerName).uniqueId
        val result = TitleService.revokeTitle(targetUuid, titleId)
        when (result) {
            TitleResult.Success -> {
                val def = TitleSettings.getTitle(titleId)
                val msg = TitleSettings.msgRemoved
                    .resolvePlaceholders("{title}" to (def?.displayName ?: titleId), "{player}" to playerName)
                sender.msg(msg)
            }
            TitleResult.NotFound -> sender.msg("<red>称号不存在。")
            else -> sender.msg("<red>操作失败。")
        }
    }
}
