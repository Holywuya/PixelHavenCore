package com.pixlehavencore.feature.realworld

import com.pixlehavencore.feature.realworld.foodcorrosion.FoodCorrosionCommand
import com.pixlehavencore.feature.realworld.fracture.FractureEngine
import com.pixlehavencore.feature.realworld.fracture.FractureSeverity
import com.pixlehavencore.feature.realworld.stamina.StaminaEngine
import com.pixlehavencore.feature.realworld.stamina.StaminaSettings
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
            sender.msg("&b/rw stamina <info|set|add|remove|reset> &7- 管理玩家体力")
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
            val weatherType = RealWorldService.getCurrentWeatherType()
            if (weatherType != null) {
                val forcedHint = if (globalState.forcedWeather != null) " &8(强制)" else ""
                sender.msg("&7天气: &f${weatherType.displayName}$forcedHint")
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
                sender.msg("&7骨折: &f${formatDecimal(state.fracture)}/100 &7(${FractureEngine.getFractureDisplayName(FractureEngine.classifyFracture(state.fracture))})")
                sender.msg("&7遮蔽: &f${when (state.shelterType) { ShelterType.NONE -> "无"; ShelterType.CANOPY -> "树冠"; ShelterType.BUILDING -> "建筑" }}")
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
            suggestion<ProxyCommandSender> { _, _ -> WeatherType.entries.map { it.name } + "AUTO" }
            execute<ProxyCommandSender> { sender, _, argument ->
                if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute

                val weatherName = argument.toString().trim()
                if (weatherName.equals("AUTO", ignoreCase = true)) {
                    submit {
                        RealWorldService.clearForcedWeather()
                        sender.msg("&a天气已恢复为噪声驱动（自动）。")
                    }
                    return@execute
                }

                val newWeather = WeatherType.fromName(weatherName)
                if (newWeather == null) {
                    sender.msg("&c无效天气。可选值: &f${WeatherType.entries.joinToString(", ") { it.name }}, AUTO")
                    return@execute
                }

                submit {
                    RealWorldService.forceWeather(newWeather)
                    sender.msg("&a天气已强制为 &f${newWeather.displayName}&a。使用 &f/rw weather AUTO &a恢复自动。")
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
    val stamina = subCommand {
        dynamic(comment = "action") {
            suggestion<ProxyCommandSender> { _, _ -> listOf("info", "set", "add", "remove", "reset") }
            execute<ProxyCommandSender> { sender, _, argument ->
                if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                sender.msg("&c请指定玩家名。用法: /rw stamina <info|set|add|remove|reset> <玩家名>")
            }
            dynamic(comment = "player") {
                suggestPlayers()
                execute<ProxyCommandSender> { sender, _, argument ->
                    if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                    val args = argument.toString().trim().split(" ")
                    val action = args[0].lowercase()
                    val playerName = args.getOrNull(1) ?: run {
                        sender.msg("&c请指定玩家名。")
                        return@execute
                    }
                    val target = findOnlinePlayer(playerName)
                    if (target == null) {
                        sender.msg("&c找不到在线玩家 &f$playerName&c。")
                        return@execute
                    }
                    when (action) {
                        "info" -> {
                            val info = StaminaEngine.getStaminaInfo(target)
                            if (info == null) {
                                sender.msg("&e玩家 &f${target.name} &e当前没有缓存体力数据。")
                                return@execute
                            }
                            sender.msg("&6=== 玩家体力信息：${target.name} ===")
                            sender.msg("&7体力值: &f${"%.1f".format(info.stamina)} / ${"%.1f".format(info.maxStamina)}")
                            sender.msg("&7百分比: &f${"%.1f".format(info.percentage)}%")
                            sender.msg("&7阶段: &f${info.phase.displayName}")
                        }
                        "set", "add", "remove" -> {
                            val value = args.getOrNull(2)?.toDoubleOrNull()
                            if (value == null) {
                                sender.msg("&c请指定数值。用法: /rw stamina $action <玩家名> <数值>")
                                return@execute
                            }
                            when (action) {
                                "set" -> {
                                    StaminaEngine.setStamina(target, value)
                                    sender.msg("&a已设置玩家 &f${target.name} &a的体力为 &f${"%.1f".format(value)}&a。")
                                }
                                "add" -> {
                                    StaminaEngine.addStamina(target, value)
                                    sender.msg("&a已为玩家 &f${target.name} &a增加 &f${"%.1f".format(value)} &a体力。")
                                }
                                "remove" -> {
                                    StaminaEngine.removeStamina(target, value)
                                    sender.msg("&a已从玩家 &f${target.name} &a减少 &f${"%.1f".format(value)} &a体力。")
                                }
                            }
                        }
                        "reset" -> {
                            StaminaEngine.resetStamina(target)
                            sender.msg("&a已重置玩家 &f${target.name} &a的体力。")
                        }
                        else -> sender.msg("&c未知操作: $action")
                    }
                }
            }
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
