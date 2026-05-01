package com.pixlehavencore.feature.playtime

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
            sender.msg("&6=== 在线时长命令帮助 ===")
            sender.msg("&b/playtime &7- 查看自己的在线时长")
            sender.msg("&b/playtime <玩家> &7- 查看他人在线时长")
            sender.msg("&b/playtime top [类型] [数量] &7- 查看排行榜")
            sender.msg("&b/playtime cleanup [天数] &7- 清理旧数据")
            sender.msg("&b/playtime reload &7- 重载配置")
            sender.msg("&7类型: total(默认) / today / week / month")
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
                    sender.msg("&c玩家 $targetName 不存在。")
                    return@execute
                }
                val data = PlaytimeService.queryPlaytime(target.uniqueId)
                if (data != null) {
                    showPlayerData(sender, targetName, data)
                } else {
                    sender.msg("&e未找到玩家 $targetName 的在线时长数据。")
                }
            }
        }
    }

    @CommandBody
    val top = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission("phcore.playtime.top")) return@execute
            handleLeaderboard(sender, "total", PlaytimeSettings.leaderboardDefaultLimit)
        }
        dynamic(comment = "type") {
            suggestion<ProxyCommandSender> { _, _ -> listOf("total", "today", "week", "month") }
            execute<ProxyCommandSender> { sender, context, _ ->
                if (!sender.requirePermission("phcore.playtime.top")) return@execute
                val type = context.getOrNull("type")?.toString() ?: "total"
                handleLeaderboard(sender, type, PlaytimeSettings.leaderboardDefaultLimit)
            }
            dynamic(comment = "limit") {
                execute<ProxyCommandSender> { sender, context, _ ->
                    if (!sender.requirePermission("phcore.playtime.top")) return@execute
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
            if (!sender.requirePermission("phcore.playtime.cleanup")) return@execute
            val days = PlaytimeSettings.cleanupDefaultDays
            showCleanupPreview(sender, days)
        }
        dynamic(comment = "days") {
            execute<ProxyCommandSender> { sender, context, _ ->
                if (!sender.requirePermission("phcore.playtime.cleanup")) return@execute
                val days = context.getOrNull("days")?.toString()?.toIntOrNull() ?: PlaytimeSettings.cleanupDefaultDays
                showCleanupPreview(sender, days)
            }
            literal("confirm") {
                execute<ProxyCommandSender> { sender, context, _ ->
                    if (!sender.requirePermission("phcore.playtime.cleanup")) return@execute
                    val days = context.getOrNull("days")?.toString()?.toIntOrNull() ?: PlaytimeSettings.cleanupDefaultDays
                    sender.msg("&7正在清理超过 $days 天未登录的玩家数据...")
                    PlaytimeService.cleanupExecute(days) { count ->
                        submit { sender.msg("&a清理完成，共删除 $count 条数据。") }
                    }
                }
            }
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission("phcore.playtime.reload")) return@execute
            PlaytimeSettings.reload()
            PlaytimeStorage.reload()
            PlaytimeService.reload()
            sender.msg("&a在线时长模块配置已重载。")
        }
    }

    private fun handleLeaderboard(sender: ProxyCommandSender, type: String, limit: Int) {
        val typeLabel = when (type.lowercase()) {
            "today" -> "今日"
            "week" -> "本周"
            "month" -> "本月"
            else -> "总"
        }
        sender.msg("&6=== 在线时长排行（$typeLabel）Top $limit ===")
        PlaytimeService.queryLeaderboard(type, limit) { entries ->
            submit {
                if (entries.isEmpty()) {
                    sender.msg("&7暂无数据。")
                    return@submit
                }
                entries.forEach { entry ->
                    sender.msg("&e#${entry.rank} &f${entry.playerName} &7- &b${entry.playtimeFormatted}")
                }
            }
        }
    }

    private fun showPlayerData(sender: ProxyCommandSender, name: String, data: PlaytimeData) {
        val session = PlaytimeService.getCurrentSessionSeconds(data.playerUuid)
        sender.msg("&6=== $name 的在线时长 ===")
        sender.msg("&b总时长: &f${PlaytimeSettings.formatSeconds(data.totalSeconds)}")
        sender.msg("&b今日: &f${PlaytimeSettings.formatSeconds(data.todaySeconds)}")
        sender.msg("&b本周: &f${PlaytimeSettings.formatSeconds(data.weekSeconds)}")
        sender.msg("&b本月: &f${PlaytimeSettings.formatSeconds(data.monthSeconds)}")
        if (session > 0) {
            sender.msg("&b本次会话: &f${PlaytimeSettings.formatSeconds(session)}")
        }
    }

    private fun showCleanupPreview(sender: ProxyCommandSender, days: Int) {
        val preview = PlaytimeService.cleanupPreview(days)
        if (preview.isEmpty()) {
            sender.msg("&a没有超过 $days 天未登录的玩家数据。")
            return
        }
        sender.msg("&e以下 &c${preview.size} &e位玩家超过 $days 天未登录：")
        preview.take(10).forEach { (_, name) ->
            sender.msg("&7- $name")
        }
        if (preview.size > 10) {
            sender.msg("&7...及其他 ${preview.size - 10} 位")
        }
        sender.msg("&e使用 &b/playtime cleanup $days confirm &e确认删除。")
    }
}
