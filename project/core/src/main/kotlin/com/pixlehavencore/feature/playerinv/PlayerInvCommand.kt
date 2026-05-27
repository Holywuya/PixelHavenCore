package com.pixlehavencore.feature.playerinv

import com.pixlehavencore.util.ADMIN_PERMISSION
import com.pixlehavencore.util.PlaceholderUtils.resolvePlaceholders
import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePermission
import com.pixlehavencore.util.requirePlayer
import com.pixlehavencore.util.resolveOfflinePlayer
import org.bukkit.OfflinePlayer
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.suggestPlayers
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.function.submit

@CommandHeader(name = "playerinv", aliases = ["pi"], permissionDefault = PermissionDefault.TRUE)
object PlayerInvCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!PlayerInvSettings.enabled) {
                sender.msg(PlayerInvSettings.disabledMessage)
                return@execute
            }
            val player = sender.requirePlayer() ?: return@execute
            PlayerInvService.openSelfAsync(player.cast()) { opened ->
                if (opened) {
                    if (PlayerInvSettings.openSelfMessage.isNotBlank()) {
                        sender.msg(PlayerInvSettings.openSelfMessage)
                    }
                } else {
                    sender.msg(PlayerInvSettings.saveFailedMessage)
                }
            }
        }
    }

    @CommandBody
    val help = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.msg("&6=== 仓库命令帮助 ===")
            sender.msg("&b/playerinv &7- 打开自己的仓库")
            sender.msg("&b/playerinv open <玩家> &7- 管理员打开玩家仓库")
            sender.msg("&b/playerinv size <玩家> <大小> &7- 管理员设置玩家仓库大小")
            sender.msg("&b/playerinv shared create <名称> &7- 创建共享仓库")
            sender.msg("&b/playerinv shared open <名称> &7- 打开共享仓库")
            sender.msg("&b/playerinv shared add/remove <名称> <玩家> &7- 管理成员")
            sender.msg("&b/playerinv shared members <名称> &7- 查看共享成员")
            sender.msg("&b/playerinv shared upgrade <名称> &7- 升级共享仓库")
            sender.msg("&b/playerinv shared quota <玩家> <数量> &7- 管理员发放创建次数")
            sender.msg("&b/playerinv shared admin-open <名称> &7- 管理员强制打开共享仓库")
            sender.msg("&b/playerinv shared owner <玩家> &7- 管理员查看该玩家的共享仓库列表")
            sender.msg("&b/playerinv shared list <玩家> &7- 管理员列出共享仓库")
            sender.msg("&b/playerinv reload &7- 重载配置")
        }
    }

    @CommandBody
    val open = subCommand {
        dynamic(comment = "player") {
            suggestPlayers()
            execute<ProxyCommandSender> { sender, _, argument ->
                if (!PlayerInvSettings.enabled) {
                    sender.msg(PlayerInvSettings.disabledMessage)
                    return@execute
                }
                val viewer = sender.requirePlayer() ?: return@execute
                if (!sender.requirePermission(ADMIN_PERMISSION)) {
                    return@execute
                }
                val targetName = argument.toString().trim()
                val target = resolveOfflinePlayer(targetName)
                if (target == null) {
                    sender.msg(PlayerInvSettings.playerNotFoundMessage.resolvePlaceholders("{player}" to targetName))
                    return@execute
                }

                PlayerInvService.openOtherAsync(viewer.cast(), target) { opened ->
                    if (opened) {
                        sender.msg(PlayerInvSettings.openOtherMessage.resolvePlaceholders("{player}" to (target.name ?: target.uniqueId.toString())))
                    } else {
                        sender.msg(PlayerInvSettings.saveFailedMessage)
                    }
                }
            }
        }
    }

    @CommandBody
    val size = subCommand {
        dynamic(comment = "player") {
            suggestPlayers()
            dynamic(comment = "size") {
                execute<ProxyCommandSender> { sender, context, argument ->
                    if (!PlayerInvSettings.enabled) {
                        sender.msg(PlayerInvSettings.disabledMessage)
                        return@execute
                    }
                    if (!sender.requirePermission(ADMIN_PERMISSION)) {
                        return@execute
                    }
                    val targetName = context.getOrNull("player") ?: run {
                        sender.msg("&c用法: /playerinv size <玩家> <大小>")
                        return@execute
                    }
                    val target = resolveOfflinePlayer(targetName)
                    if (target == null) {
                        sender.msg(PlayerInvSettings.playerNotFoundMessage.resolvePlaceholders("{player}" to targetName))
                        return@execute
                    }
                    val size = argument.toString().trim().toIntOrNull()
                    if (size == null) {
                        sender.msg("&c请输入有效仓库大小（9~54，且为9的倍数）")
                        return@execute
                    }
                    val normalized = PlayerInvService.normalizeSize(size)
                    submit(async = true) {
                        val success = PlayerInvService.setPersonalSize(target, normalized)
                        submit {
                            if (!success) {
                                sender.msg("&c设置仓库大小失败！")
                                return@submit
                            }
                            sender.msg("&a已将玩家 &f${target.name ?: target.uniqueId} &a仓库大小设置为 &f$normalized")
                        }
                    }
                }
            }
        }
    }

    @CommandBody
    val shared = subCommand {
        literal("create") {
            dynamic(comment = "name") {
                execute<ProxyCommandSender> { sender, _, argument ->
                    if (!PlayerInvSettings.enabled) {
                        sender.msg(PlayerInvSettings.disabledMessage)
                        return@execute
                    }
                    val player = sender.requirePlayer() ?: return@execute
                    val sharedName = argument.toString().trim()
                    submit(async = true) {
                        val result = PlayerInvService.createShared(player.cast(), sharedName)
                        submit {
                            when (result) {
                                PlayerInvService.SharedCreateResult.OK -> sender.msg(PlayerInvSettings.sharedCreatedMessage.resolvePlaceholders("{name}" to sharedName))
                                PlayerInvService.SharedCreateResult.NO_QUOTA -> sender.msg(PlayerInvSettings.sharedCreateNoQuotaMessage)
                                PlayerInvService.SharedCreateResult.EXISTS -> sender.msg(PlayerInvSettings.sharedExistsMessage.resolvePlaceholders("{name}" to sharedName))
                                PlayerInvService.SharedCreateResult.INVALID_NAME -> sender.msg("&c共享仓库名称不能为空！")
                                PlayerInvService.SharedCreateResult.FAILED -> sender.msg("&c创建共享仓库失败！")
                            }
                        }
                    }
                }
            }
        }

        literal("open") {
            dynamic(comment = "name") {
                execute<ProxyCommandSender> { sender, _, argument ->
                    if (!PlayerInvSettings.enabled) {
                        sender.msg(PlayerInvSettings.disabledMessage)
                        return@execute
                    }
                    val player = sender.requirePlayer() ?: return@execute
                    val sharedName = argument.toString().trim()
                    PlayerInvService.openSharedAsync(player.cast(), sharedName) { result ->
                        when (result) {
                            PlayerInvService.SharedOpenResult.OK -> Unit
                            PlayerInvService.SharedOpenResult.NOT_FOUND -> sender.msg(PlayerInvSettings.sharedNotFoundMessage.resolvePlaceholders("{name}" to sharedName))
                            PlayerInvService.SharedOpenResult.NO_ACCESS -> sender.msg(PlayerInvSettings.sharedNoAccessMessage.resolvePlaceholders("{name}" to sharedName))
                            PlayerInvService.SharedOpenResult.FAILED -> sender.msg("&c打开共享仓库失败！")
                        }
                    }
                }
            }
        }

        literal("add") {
            dynamic(comment = "name") {
                dynamic(comment = "player") {
                    suggestPlayers()
                    execute<ProxyCommandSender> { sender, context, argument ->
                        val player = sender.requirePlayer() ?: return@execute
                        val sharedName = context.getOrNull("name") ?: run {
                            sender.msg("&c用法: /playerinv shared add <仓库名> <玩家>")
                            return@execute
                        }
                        val targetName = argument.toString().trim()
                        val target = resolveOfflinePlayer(targetName)
                        if (target == null) {
                            sender.msg(PlayerInvSettings.playerNotFoundMessage.resolvePlaceholders("{player}" to targetName))
                            return@execute
                        }
                        submit(async = true) {
                            val result = PlayerInvService.addSharedMember(player.cast(), sharedName, target)
                            submit {
                                when (result) {
                                    PlayerInvService.SharedMemberResult.OK -> sender.msg(
                                        PlayerInvSettings.sharedMemberAddedMessage
                                            .resolvePlaceholders("{name}" to sharedName, "{player}" to (target.name ?: target.uniqueId.toString()))
                                    )

                                    PlayerInvService.SharedMemberResult.NOT_FOUND -> sender.msg(PlayerInvSettings.sharedNotFoundMessage.resolvePlaceholders("{name}" to sharedName))
                                    PlayerInvService.SharedMemberResult.NO_ACCESS -> sender.msg(PlayerInvSettings.sharedNoAccessMessage.resolvePlaceholders("{name}" to sharedName))
                                    else -> sender.msg("&c添加共享成员失败！")
                                }
                            }
                        }
                    }
                }
            }
        }

        literal("remove") {
            dynamic(comment = "name") {
                dynamic(comment = "player") {
                    suggestPlayers()
                    execute<ProxyCommandSender> { sender, context, argument ->
                        val player = sender.requirePlayer() ?: return@execute
                        val sharedName = context.getOrNull("name") ?: run {
                            sender.msg("&c用法: /playerinv shared remove <仓库名> <玩家>")
                            return@execute
                        }
                        val targetName = argument.toString().trim()
                        val target = resolveOfflinePlayer(targetName)
                        if (target == null) {
                            sender.msg(PlayerInvSettings.playerNotFoundMessage.resolvePlaceholders("{player}" to targetName))
                            return@execute
                        }
                        submit(async = true) {
                            val result = PlayerInvService.removeSharedMember(player.cast(), sharedName, target)
                            submit {
                                when (result) {
                                    PlayerInvService.SharedMemberResult.OK -> sender.msg(
                                        PlayerInvSettings.sharedMemberRemovedMessage
                                            .resolvePlaceholders("{name}" to sharedName, "{player}" to (target.name ?: target.uniqueId.toString()))
                                    )

                                    PlayerInvService.SharedMemberResult.CANNOT_REMOVE_OWNER -> sender.msg("&c不能移除共享仓库创建者！")
                                    PlayerInvService.SharedMemberResult.NOT_FOUND -> sender.msg(PlayerInvSettings.sharedNotFoundMessage.resolvePlaceholders("{name}" to sharedName))
                                    PlayerInvService.SharedMemberResult.NO_ACCESS -> sender.msg(PlayerInvSettings.sharedNoAccessMessage.resolvePlaceholders("{name}" to sharedName))
                                    else -> sender.msg("&c移除共享成员失败！")
                                }
                            }
                        }
                    }
                }
            }
        }

        literal("members") {
            dynamic(comment = "name") {
                execute<ProxyCommandSender> { sender, _, argument ->
                    val player = sender.requirePlayer() ?: return@execute
                    val sharedName = argument.toString().trim()
                    submit(async = true) {
                        val result = PlayerInvService.listSharedMembers(player.cast(), sharedName)
                        submit {
                            when (result) {
                                is PlayerInvService.SharedMemberListResult.OK -> {
                                    sender.msg("&6=== 共享仓库成员: $sharedName ===")
                                    if (result.members.isEmpty()) {
                                        sender.msg("&7(无成员)")
                                    } else {
                                        result.members.forEach { member ->
                                            sender.msg("&b${member.playerName} &7- ${if (member.owner) "拥有者" else "成员"}")
                                        }
                                    }
                                }

                                PlayerInvService.SharedMemberListResult.NOT_FOUND -> sender.msg(PlayerInvSettings.sharedNotFoundMessage.resolvePlaceholders("{name}" to sharedName))
                                PlayerInvService.SharedMemberListResult.NO_ACCESS -> sender.msg(PlayerInvSettings.sharedNoAccessMessage.resolvePlaceholders("{name}" to sharedName))
                                PlayerInvService.SharedMemberListResult.FAILED -> sender.msg("&c查询共享成员失败！")
                            }
                        }
                    }
                }
            }
        }

        literal("upgrade") {
            dynamic(comment = "name") {
                execute<ProxyCommandSender> { sender, _, argument ->
                    val player = sender.requirePlayer() ?: return@execute
                    val sharedName = argument.toString().trim()
                    submit(async = true) {
                        val result = PlayerInvService.upgradeShared(player.cast(), sharedName)
                        submit {
                            when (result) {
                                is PlayerInvService.SharedUpgradeResult.OK -> sender.msg(
                                    PlayerInvSettings.sharedUpgradeMessage
                                        .resolvePlaceholders("{name}" to sharedName, "{size}" to result.size.toString())
                                )

                                PlayerInvService.SharedUpgradeResult.NOT_FOUND -> sender.msg(PlayerInvSettings.sharedNotFoundMessage.resolvePlaceholders("{name}" to sharedName))
                                PlayerInvService.SharedUpgradeResult.NO_ACCESS -> sender.msg(PlayerInvSettings.sharedNoAccessMessage.resolvePlaceholders("{name}" to sharedName))
                                PlayerInvService.SharedUpgradeResult.REACHED_MAX -> sender.msg("&e共享仓库已达到最大大小！")
                                PlayerInvService.SharedUpgradeResult.FAILED -> sender.msg("&c升级共享仓库失败！")
                            }
                        }
                    }
                }
            }
        }

        literal("quota") {
            dynamic(comment = "player") {
                suggestPlayers()
                dynamic(comment = "amount") {
                    execute<ProxyCommandSender> { sender, context, argument ->
                        if (!sender.requirePermission(ADMIN_PERMISSION)) {
                            return@execute
                        }
                        val targetName = context.getOrNull("player") ?: run {
                            sender.msg("&c用法: /playerinv shared quota <玩家> <数量>")
                            return@execute
                        }
                        val target = resolveOfflinePlayer(targetName)
                        if (target == null) {
                            sender.msg(PlayerInvSettings.playerNotFoundMessage.resolvePlaceholders("{player}" to targetName))
                            return@execute
                        }
                        val amount = argument.toString().trim().toIntOrNull()
                        if (amount == null || amount <= 0) {
                            sender.msg("&c数量必须为正整数！")
                            return@execute
                        }
                        submit(async = true) {
                            val success = PlayerInvService.grantSharedQuota(target, amount)
                            submit {
                                if (!success) {
                                    sender.msg("&c发放共享仓库创建次数失败！")
                                    return@submit
                                }
                                sender.msg(
                                    PlayerInvSettings.sharedQuotaGrantedMessage
                                        .resolvePlaceholders("{player}" to (target.name ?: target.uniqueId.toString()), "{amount}" to amount.toString())
                                )
                            }
                        }
                    }
                }
            }
        }

        literal("admin-open") {
            dynamic(comment = "name") {
                execute<ProxyCommandSender> { sender, _, argument ->
                    if (!sender.requirePermission(ADMIN_PERMISSION)) {
                        return@execute
                    }
                    val player = sender.requirePlayer() ?: return@execute
                    val sharedName = argument.toString().trim()
                    PlayerInvService.openSharedAsync(player.cast(), sharedName, forceAdmin = true) { result ->
                        when (result) {
                            PlayerInvService.SharedOpenResult.OK -> Unit
                            PlayerInvService.SharedOpenResult.NOT_FOUND -> sender.msg(PlayerInvSettings.sharedNotFoundMessage.resolvePlaceholders("{name}" to sharedName))
                            else -> sender.msg("&c打开共享仓库失败！")
                        }
                    }
                }
            }
        }

        literal("owner") {
            dynamic(comment = "player") {
                suggestPlayers()
                execute<ProxyCommandSender> { sender, _, argument ->
                    if (!sender.requirePermission(ADMIN_PERMISSION)) {
                        return@execute
                    }
                    val targetName = argument.toString().trim()
                    val target = resolveOfflinePlayer(targetName)
                    if (target == null) {
                        sender.msg(PlayerInvSettings.playerNotFoundMessage.resolvePlaceholders("{player}" to targetName))
                        return@execute
                    }
                    submit(async = true) {
                        val names = PlayerInvService.listSharedByOwner(target)
                        submit {
                            if (names.isEmpty()) {
                                sender.msg("&e该玩家没有共享仓库：${target.name ?: target.uniqueId}")
                            } else {
                                sender.msg("&6=== ${target.name ?: target.uniqueId} 的共享仓库 ===")
                                names.forEach { sender.msg("&b- $it") }
                            }
                        }
                    }
                }
            }
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission(ADMIN_PERMISSION)) {
                return@execute
            }
            PlayerInvService.reload()
            sender.msg(PlayerInvSettings.reloadMessage)
        }
    }

}
