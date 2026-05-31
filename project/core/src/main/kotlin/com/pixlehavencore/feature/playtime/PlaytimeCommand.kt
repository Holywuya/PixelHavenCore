package com.pixlehavencore.feature.playtime

import com.pixlehavencore.util.ADMIN_PERMISSION
import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePermission
import com.pixlehavencore.util.requirePlayer
import org.bukkit.Bukkit
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.suggestPlayers
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.function.submit
import java.util.UUID

@CommandHeader(name = "playtime", aliases = ["pt", "onlinetime"], permissionDefault = PermissionDefault.TRUE)
object PlaytimeCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.msg("<gold>=== 在线时长命令帮助 ===")
            sender.msg("<aqua>/playtime <gray>- 查看自己的在线时长")
            sender.msg("<aqua>/playtime <玩家> <gray>- 查看他人在线时长")
            sender.msg("<aqua>/playtime top [类型] [数量] <gray>- 查看排行榜")
            sender.msg("<aqua>/playtime cleanup [天数] <gray>- 清理旧数据")
            sender.msg("<aqua>/playtime reload <gray>- 重载配置")
            sender.msg("<gray>类型: total(默认) / today / week / month")
        }
    }

    @CommandBody
    val query = subCommand {
        dynamic(comment = "player") {
            suggestPlayers()
            execute<ProxyCommandSender> { sender, context, _ ->
                val targetName = context.getOrNull("player")?.toString() ?: return@execute
                if (!sender.requirePermission("phcore.playtime.other")) return@execute
                val target = Bukkit.getOfflinePlayer(targetName)
                if (!target.hasPlayedBefore() && !target.isOnline) {
                    sender.msg("<red>玩家 $targetName 不存在。")
                    return@execute
                }
                val data = PlaytimeService.queryPlaytime(target.uniqueId)
                if (data != null) {
                    showPlayerData(sender, targetName, data)
                } else {
                    sender.msg("<yellow>未找到玩家 $targetName 的在线时长数据。")
                }
            }
        }
    }

    @CommandBody
    val top = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            handleLeaderboard(sender, "total", PlaytimeSettings.leaderboardDefaultLimit)
        }
        dynamic(comment = "type") {
            suggestion<ProxyCommandSender> { _, _ -> listOf("total", "today", "week", "month") }
            execute<ProxyCommandSender> { sender, context, _ ->
                val type = context.getOrNull("type")?.toString() ?: "total"
                handleLeaderboard(sender, type, PlaytimeSettings.leaderboardDefaultLimit)
            }
            dynamic(comment = "limit") {
                execute<ProxyCommandSender> { sender, context, _ ->
                    val type = context.getOrNull("type")?.toString() ?: "total"
                    val limit = context.getOrNull("limit")?.toString()?.toIntOrNull() ?: PlaytimeSettings.leaderboardDefaultLimit
                    handleLeaderboard(sender, type, limit)
                }
            }
        }
    }

    @CommandBody
    val cleanup = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
            val days = PlaytimeSettings.cleanupDefaultDays
            showCleanupPreview(sender, days)
        }
        dynamic(comment = "days") {
            execute<ProxyCommandSender> { sender, context, _ ->
                if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                val days = context.getOrNull("days")?.toString()?.toIntOrNull() ?: PlaytimeSettings.cleanupDefaultDays
                showCleanupPreview(sender, days)
            }
            literal("confirm") {
                execute<ProxyCommandSender> { sender, context, _ ->
                    if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                    val days = context.getOrNull("days")?.toString()?.toIntOrNull() ?: PlaytimeSettings.cleanupDefaultDays
                    sender.msg("<gray>正在清理超过 $days 天未登录的玩家数据...")
                    PlaytimeService.cleanupExecute(days) { count ->
                        submit { sender.msg("<green>清理完成，共删除 $count 条数据。") }
                    }
                }
            }
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
            PlaytimeSettings.reload()
            PlaytimeStorage.reload()
            PlaytimeService.reload()
            sender.msg("<green>在线时长模块配置已重载。")
        }
    }

    private fun handleLeaderboard(sender: ProxyCommandSender, type: String, limit: Int) {
        val typeLabel = when (type.lowercase()) {
            "today" -> "今日"
            "week" -> "本周"
            "month" -> "本月"
            else -> "总"
        }
        sender.msg("<gold>=== 在线时长排行（$typeLabel）Top $limit ===")
        PlaytimeService.queryLeaderboard(type, limit) { entries ->
            submit {
                if (entries.isEmpty()) {
                    sender.msg("<gray>暂无数据。")
                    return@submit
                }
                entries.forEach { entry ->
                    sender.msg("<yellow>#${entry.rank} <white>${entry.playerName} <gray>- <aqua>${entry.playtimeFormatted}")
                }
            }
        }
    }

    private fun showPlayerData(sender: ProxyCommandSender, name: String, data: PlaytimeData) {
        val session = PlaytimeService.getCurrentSessionSeconds(data.playerUuid)
        sender.msg("<gold>=== $name 的在线时长 ===")
        sender.msg("<aqua>总时长: <white>${PlaytimeSettings.formatSeconds(data.totalSeconds)}")
        sender.msg("<aqua>今日: <white>${PlaytimeSettings.formatSeconds(data.todaySeconds)}")
        sender.msg("<aqua>本周: <white>${PlaytimeSettings.formatSeconds(data.weekSeconds)}")
        sender.msg("<aqua>本月: <white>${PlaytimeSettings.formatSeconds(data.monthSeconds)}")
        if (session > 0) {
            sender.msg("<aqua>本次会话: <white>${PlaytimeSettings.formatSeconds(session)}")
        }
    }

    private fun showCleanupPreview(sender: ProxyCommandSender, days: Int) {
        val preview = PlaytimeService.cleanupPreview(days)
        if (preview.isEmpty()) {
            sender.msg("<green>没有超过 $days 天未登录的玩家数据。")
            return
        }
        sender.msg("<yellow>以下 <red>${preview.size} <yellow>位玩家超过 $days 天未登录：")
        preview.take(10).forEach { (_, name) ->
            sender.msg("<gray>- $name")
        }
        if (preview.size > 10) {
            sender.msg("<gray>...及其他 ${preview.size - 10} 位")
        }
        sender.msg("<yellow>使用 <aqua>/playtime cleanup $days confirm <yellow>确认删除。")
    }
}
