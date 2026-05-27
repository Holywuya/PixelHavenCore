package com.pixlehavencore.feature.flight

import com.pixlehavencore.util.PlaceholderUtils.resolvePlaceholders
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
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.command.suggestPlayers

@CommandHeader(name = "flight", aliases = ["fly"], permissionDefault = PermissionDefault.TRUE)
object FlightCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!FlightSettings.enabled) {
                sender.msg(FlightSettings.msgModuleDisabled)
                return@execute
            }
            val player = sender.requirePlayer() ?: return@execute
            val enabled = FlightService.toggleFlight(player.cast())
            sender.msg(if (enabled) FlightSettings.msgFlightOn else FlightSettings.msgFlightOff)
        }
    }

    @CommandBody
    val on = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!FlightSettings.enabled) {
                sender.msg(FlightSettings.msgModuleDisabled)
                return@execute
            }
            val player = sender.requirePlayer() ?: return@execute
            FlightService.enableFlight(player.cast())
            sender.msg(FlightSettings.msgFlightOn)
        }
    }

    @CommandBody
    val off = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!FlightSettings.enabled) {
                sender.msg(FlightSettings.msgModuleDisabled)
                return@execute
            }
            val player = sender.requirePlayer() ?: return@execute
            FlightService.disableFlight(player.cast())
            sender.msg(FlightSettings.msgFlightOff)
        }
    }

    @CommandBody
    val check = subCommand {
        dynamic(comment = "player") {
            suggestPlayers()
            execute<ProxyCommandSender> { sender, _, argument ->
                val targetName = argument.toString().trim()
                val target = Bukkit.getPlayerExact(targetName)
                if (target == null) {
                    sender.msg(FlightSettings.msgPlayerNotFound.resolvePlaceholders("{player}" to targetName))
                    return@execute
                }
                showFlightInfo(sender, target.name, target.uniqueId)
            }
        }
        execute<ProxyCommandSender> { sender, _, _ ->
            val player = sender.requirePlayer() ?: return@execute
            showFlightInfo(sender, player.name, player.uniqueId)
        }
    }

    @CommandBody
    val set = subCommand {
        dynamic(comment = "player") {
            suggestPlayers()
            dynamic(comment = "time") {
                execute<ProxyCommandSender> { sender, context, argument ->
                    if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                    val targetName = context.getOrNull("player") ?: return@execute
                    val target = Bukkit.getPlayerExact(targetName) ?: run {
                        sender.msg(FlightSettings.msgPlayerNotFound.resolvePlaceholders("{player}" to targetName))
                        return@execute
                    }
                    val seconds = parseTimeArgument(argument.toString())
                    if (seconds == null || seconds < 0) {
                        sender.msg(FlightSettings.msgInvalidTime)
                        return@execute
                    }
                    FlightService.setRemainingSeconds(target, seconds)
                    sender.msg(FlightSettings.msgAdminSet.resolvePlaceholders(
                        "{player}" to target.name,
                        "{time}" to FlightService.formatTime(seconds)
                    ))
                }
            }
        }
    }

    @CommandBody
    val add = subCommand {
        dynamic(comment = "player") {
            suggestPlayers()
            dynamic(comment = "time") {
                execute<ProxyCommandSender> { sender, context, argument ->
                    if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                    val targetName = context.getOrNull("player") ?: return@execute
                    val target = Bukkit.getPlayerExact(targetName) ?: run {
                        sender.msg(FlightSettings.msgPlayerNotFound.resolvePlaceholders("{player}" to targetName))
                        return@execute
                    }
                    val seconds = parseTimeArgument(argument.toString())
                    if (seconds == null || seconds <= 0) {
                        sender.msg(FlightSettings.msgInvalidTime)
                        return@execute
                    }
                    FlightService.addBonusSeconds(target, seconds)
                    sender.msg(FlightSettings.msgAdminAdd.resolvePlaceholders(
                        "{player}" to target.name,
                        "{time}" to FlightService.formatTime(seconds)
                    ))
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
                val target = Bukkit.getPlayerExact(targetName) ?: run {
                    sender.msg(FlightSettings.msgPlayerNotFound.resolvePlaceholders("{player}" to targetName))
                    return@execute
                }
                FlightService.resetPlayer(target)
                sender.msg(FlightSettings.msgAdminReset.resolvePlaceholders("{player}" to target.name))
            }
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
            FlightService.reload()
            sender.msg(FlightSettings.msgReloadSuccess)
        }
    }

    private fun showFlightInfo(sender: ProxyCommandSender, playerName: String, uuid: java.util.UUID) {
        val data = FlightService.getPlayerData(uuid)
        if (data == null) {
            sender.msg(FlightSettings.msgPlayerNotFound.resolvePlaceholders("{player}" to playerName))
            return
        }
        sender.msg(FlightSettings.msgCheckResult.resolvePlaceholders(
            "{player}" to playerName,
            "{time}" to FlightService.formatTime(data.effectiveSeconds),
            "{daily}" to data.remainingSeconds.toString(),
            "{bonus}" to data.bonusSeconds.toString()
        ))
    }

    private fun parseTimeArgument(input: String): Int? {
        val trimmed = input.trim().lowercase()
        trimmed.toIntOrNull()?.let { return it }
        val regex = Regex("(\\d+)([hms])")
        val matches = regex.findAll(trimmed).toList()
        if (matches.isEmpty()) return null
        var total = 0
        for (m in matches) {
            val value = m.groupValues[1].toIntOrNull() ?: return null
            total += when (m.groupValues[2]) {
                "h" -> value * 3600
                "m" -> value * 60
                "s" -> value
                else -> return null
            }
        }
        return total
    }
}
