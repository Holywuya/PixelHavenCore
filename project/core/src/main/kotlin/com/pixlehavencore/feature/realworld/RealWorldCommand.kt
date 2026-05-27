package com.pixlehavencore.feature.realworld

import com.pixlehavencore.feature.realworld.foodcorrosion.FoodCorrosionCommand
import com.pixlehavencore.util.ADMIN_PERMISSION
import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePermission
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.command.suggestPlayers
import taboolib.common.platform.function.onlinePlayers
import taboolib.common.platform.function.submit

@CommandHeader(name = "realworld", aliases = ["rw"], permissionDefault = PermissionDefault.TRUE)
object RealWorldCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.msg("&6=== 真实世界环境命令帮助 ===")
            sender.msg("&b/rw status &7- 查看当前季节、天气与在线玩家平均状态")
            sender.msg("&b/rw player <玩家名> &7- 查看缓存中的玩家环境状态")
            sender.msg("&b/rw season <季节> &7- 强制切换季节")
            sender.msg("&b/rw weather <天气> &7- 强制切换天气")
            sender.msg("&b/rw reset <玩家名> &7- 重置玩家生存数据")
            sender.msg("&b/rw reload &7- 重载真实世界模块")
            sender.msg("&b/rw corrosion status &7- 查看食物腐蚀功能状态")
            sender.msg("&7管理子命令均需要 &fphcore.admin &7权限")
        }
    }

    @CommandBody
    val status = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute

            val globalState = RealWorldService.getGlobalStateSnapshot()
            if (globalState == null) {
                sender.msg("&e真实世界全局状态尚未初始化。")
                return@execute
            }

            val onlinePlayers = getOnlinePlayers()
            val cachedStates = onlinePlayers.mapNotNull { RealWorldStorage.getPlayerSnapshot(it.uniqueId) }

            sender.msg("&6=== 真实世界环境状态 ===")
            sender.msg("&7季节: &f${globalState.season.displayName} &7(进度 &f${formatDecimal(globalState.seasonProgress * 100)}%&7)")
            sender.msg("&7天气: &f${globalState.weather.displayName} &7(强度 &f${formatDecimal(globalState.weatherIntensity)}&7)")
            val pendingWeather = globalState.pendingWeather
            if (pendingWeather != null && globalState.warningRemainingSeconds > 0.0) {
                sender.msg("&7预警: &f${pendingWeather.displayName} &7将在 &f${kotlin.math.ceil(globalState.warningRemainingSeconds).toInt()} &7秒后到来")
            }
            sender.msg("&7在线玩家: &f${onlinePlayers.size} &7人")
            sender.msg("&7已缓存状态: &f${cachedStates.size} &7人")
            if (cachedStates.isEmpty()) {
                sender.msg("&7平均体温: &f无缓存数据")
                sender.msg("&7平均口渴: &f无缓存数据")
            } else {
                val averageTemperature = cachedStates.map { it.temperature }.average()
                val averageHydration = cachedStates.map { it.hydration }.average()
                sender.msg("&7平均体温: &f${formatDecimal(averageTemperature)}°C")
                sender.msg("&7平均口渴: &f${formatDecimal(averageHydration)}/100")
            }
        }
    }

    @CommandBody
    val player = subCommand {
        dynamic(comment = "player") {
            suggestPlayers()
            execute<ProxyCommandSender> { sender, _, argument ->
                if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute

                val targetName = argument.toString().trim()
                val target = findOnlinePlayer(targetName)
                if (target == null) {
                    sender.msg("&c找不到在线玩家 &f$targetName&c。")
                    return@execute
                }

                val state = RealWorldStorage.getPlayerSnapshot(target.uniqueId)
                if (state == null) {
                    sender.msg("&e玩家 &f${target.name} &e当前没有缓存环境数据。")
                    return@execute
                }

                sender.msg("&6=== 玩家环境状态：${target.name} ===")
                sender.msg("&7体温: &f${formatDecimal(state.temperature)}°C &7(${state.temperaturePhase.name})")
                sender.msg("&7口渴: &f${formatDecimal(state.hydration)}/100 &7(${state.thirstPhase.name})")
                sender.msg("&7遮蔽: &f${if (state.isSheltered) "是" else "否"}")
                sender.msg("&7天气遮蔽: &f${if (state.isWeatherSheltered) "是" else "否"}")
                sender.msg("&7热源: &f${formatHeatSource(state.nearHeatSource)}")
            }
        }
    }

    @CommandBody
    val season = subCommand {
        dynamic(comment = "season") {
            suggestion<ProxyCommandSender> { _, _ -> Season.entries.map { it.name } }
            execute<ProxyCommandSender> { sender, _, argument ->
                if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute

                val seasonName = argument.toString().trim()
                val newSeason = Season.fromName(seasonName)
                if (newSeason == null) {
                    sender.msg("&c无效季节。可选值: &f${Season.entries.joinToString(", ") { it.name }}")
                    return@execute
                }

                RealWorldService.forceSeason(newSeason)
                sender.msg("&a季节已切换为 &f${newSeason.displayName}&a。")
            }
        }
    }

    @CommandBody
    val weather = subCommand {
        dynamic(comment = "weather") {
            suggestion<ProxyCommandSender> { _, _ -> WeatherType.entries.map { it.name } }
            execute<ProxyCommandSender> { sender, _, argument ->
                if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute

                val weatherName = argument.toString().trim()
                val newWeather = WeatherType.fromName(weatherName)
                if (newWeather == null) {
                    sender.msg("&c无效天气。可选值: &f${WeatherType.entries.joinToString(", ") { it.name }}")
                    return@execute
                }

                submit {
                    RealWorldService.forceWeather(newWeather)
                    sender.msg("&a天气已切换为 &f${newWeather.displayName}&a。")
                }
            }
        }
    }

    @CommandBody
    val reset = subCommand {
        dynamic(comment = "player") {
            suggestPlayers()
            execute<ProxyCommandSender> { sender, _, argument ->
                if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute

                val targetName = argument.toString().trim()
                val onlineTarget = findOnlinePlayer(targetName)
                val target = onlineTarget ?: Bukkit.getOfflinePlayer(targetName)
                if (onlineTarget == null && !target.hasPlayedBefore()) {
                    sender.msg("&c找不到玩家 &f$targetName&c。")
                    return@execute
                }

                RealWorldService.resetPlayer(target.uniqueId)
                sender.msg("&a已重置玩家 &f${target.name ?: targetName} &a的生存数据。")
            }
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute

            RealWorldService.reload()
            sender.msg("&a真实世界模块已重载。")
        }
    }

    @CommandBody
    val corrosion = subCommand {
        dynamic(comment = "action") {
            suggestion<ProxyCommandSender> { _, _ -> listOf("status") }
            execute<ProxyCommandSender> { sender, _, argument ->
                if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute

                val action = argument.toString().trim().lowercase()
                when (action) {
                    "status" -> FoodCorrosionCommand.sendStatus(sender)
                    else -> sender.msg("&c未知操作。可选值: status")
                }
            }
        }
    }

    private fun findOnlinePlayer(name: String) =
        getOnlinePlayers().firstOrNull { it.name.equals(name, ignoreCase = true) }

    private fun getOnlinePlayers(): List<Player> {
        return onlinePlayers().mapNotNull { proxy ->
            proxy.cast<Player>()
        }
    }

    private fun formatHeatSource(source: HeatSource?): String {
        return source?.name ?: "无"
    }

    private fun formatDecimal(value: Double): String {
        return "%.1f".format(value)
    }
}
