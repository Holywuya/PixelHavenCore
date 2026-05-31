package com.pixlehavencore.feature.realworld

import com.pixlehavencore.feature.realworld.foodcorrosion.FoodCorrosionCommand
import com.pixlehavencore.feature.realworld.fracture.FractureEngine
import com.pixlehavencore.feature.realworld.fracture.FractureSeverity
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
            sender.msg("<gold>=== 真实世界环境命令帮助 ===")
            sender.msg("<aqua>/rw status <gray>- 查看当前季节、天气与在线玩家平均状态")
            sender.msg("<aqua>/rw player <玩家名> <gray>- 查看缓存中的玩家环境状态")
            sender.msg("<aqua>/rw season <季节> <gray>- 强制切换季节")
            sender.msg("<aqua>/rw weather <天气> <gray>- 强制切换天气")
            sender.msg("<aqua>/rw reset <玩家名> <gray>- 重置玩家生存数据")
            sender.msg("<aqua>/rw reload <gray>- 重载真实世界模块")
            sender.msg("<aqua>/rw corrosion status <gray>- 查看食物腐蚀功能状态")
            sender.msg("<gray>管理子命令均需要 <white>phcore.admin <gray>权限")
        }
    }

    @CommandBody
    val status = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute

            val globalState = RealWorldService.getGlobalStateSnapshot()
            if (globalState == null) {
                sender.msg("<yellow>真实世界全局状态尚未初始化。")
                return@execute
            }

            val onlinePlayers = getOnlinePlayers()
            val cachedStates = onlinePlayers.mapNotNull { RealWorldStorage.getPlayerSnapshot(it.uniqueId) }

            sender.msg("<gold>=== 真实世界环境状态 ===")
            sender.msg("<gray>季节: <white>${globalState.season.displayName} <gray>(进度 <white>${formatDecimal(globalState.seasonProgress * 100)}%</white><gray>)")
            val weatherType = RealWorldService.getCurrentWeatherType()
            if (weatherType != null) {
                val forcedHint = if (globalState.forcedWeather != null) " <dark_gray>(强制)" else ""
                sender.msg("<gray>天气: <white>${weatherType.displayName}$forcedHint")
            }
            sender.msg("<gray>在线玩家: <white>${onlinePlayers.size} <gray>人")
            sender.msg("<gray>已缓存状态: <white>${cachedStates.size} <gray>人")
            if (cachedStates.isEmpty()) {
                sender.msg("<gray>平均体温: <white>无缓存数据")
                sender.msg("<gray>平均口渴: <white>无缓存数据")
            } else {
                val averageTemperature = cachedStates.map { it.temperature }.average()
                val averageHydration = cachedStates.map { it.hydration }.average()
                sender.msg("<gray>平均体温: <white>${formatDecimal(averageTemperature)}°C")
                sender.msg("<gray>平均口渴: <white>${formatDecimal(averageHydration)}/100")
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
                    sender.msg("<red>找不到在线玩家 <white>$targetName</white><red>。")
                    return@execute
                }

                val state = RealWorldStorage.getPlayerSnapshot(target.uniqueId)
                if (state == null) {
                    sender.msg("<yellow>玩家 <white>${target.name} <yellow>当前没有缓存环境数据。")
                    return@execute
                }

                sender.msg("<gold>=== 玩家环境状态：${target.name} ===")
                sender.msg("<gray>体温: <white>${formatDecimal(state.temperature)}°C <gray>(${state.temperaturePhase.name})")
                sender.msg("<gray>口渴: <white>${formatDecimal(state.hydration)}/100 <gray>(${state.thirstPhase.name})")
                sender.msg("<gray>骨折: <white>${formatDecimal(state.fracture)}/100 <gray>(${FractureEngine.getFractureDisplayName(FractureEngine.classifyFracture(state.fracture))})")
                sender.msg("<gray>遮蔽: <white>${when (state.shelterType) { ShelterType.NONE -> "无"; ShelterType.CANOPY -> "树冠"; ShelterType.BUILDING -> "建筑" }}")
                sender.msg("<gray>天气遮蔽: <white>${if (state.isWeatherSheltered) "是" else "否"}")
                sender.msg("<gray>热源: <white>${formatHeatSource(state.nearHeatSource)}")
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
                    sender.msg("<red>无效季节。可选值: <white>${Season.entries.joinToString(", ") { it.name }}")
                    return@execute
                }

                RealWorldService.forceSeason(newSeason)
                sender.msg("<green>季节已切换为 <white>${newSeason.displayName}</white><green>。")
            }
        }
    }

    @CommandBody
    val weather = subCommand {
        dynamic(comment = "weather") {
            suggestion<ProxyCommandSender> { _, _ -> WeatherType.entries.map { it.name } + "AUTO" }
            execute<ProxyCommandSender> { sender, _, argument ->
                if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute

                val weatherName = argument.toString().trim()
                if (weatherName.equals("AUTO", ignoreCase = true)) {
                    submit {
                        RealWorldService.clearForcedWeather()
                        sender.msg("<green>天气已恢复为噪声驱动（自动）。")
                    }
                    return@execute
                }

                val newWeather = WeatherType.fromName(weatherName)
                if (newWeather == null) {
                    sender.msg("<red>无效天气。可选值: <white>${WeatherType.entries.joinToString(", ") { it.name }}, AUTO")
                    return@execute
                }

                submit {
                    RealWorldService.forceWeather(newWeather)
                    sender.msg("<green>天气已强制为 <white>${newWeather.displayName}</white><green>。使用 <white>/rw weather AUTO <green>恢复自动。")
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
                    sender.msg("<red>找不到玩家 <white>$targetName</white><red>。")
                    return@execute
                }

                RealWorldService.resetPlayer(target.uniqueId)
                sender.msg("<green>已重置玩家 <white>${target.name ?: targetName}</white> <green>的生存数据。")
            }
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute

            RealWorldService.reload()
            sender.msg("<green>真实世界模块已重载。")
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
                    else -> sender.msg("<red>未知操作。可选值: status")
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
