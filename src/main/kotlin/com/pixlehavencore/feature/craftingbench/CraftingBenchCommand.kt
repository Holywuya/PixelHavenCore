package com.pixlehavencore.feature.craftingbench

import com.pixlehavencore.util.CraftEngineItemsUtil
import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePermission
import com.pixlehavencore.util.requirePlayer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.suggestPlayers
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.function.submit

@CommandHeader(name = "craftingbench", aliases = ["cbench"], permissionDefault = PermissionDefault.TRUE)
object CraftingBenchCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.msg("&6=== 制作台模块帮助 ===")
            sender.msg("&b/craftingbench queue &7- 查看自己的制作任务")
            sender.msg("&b/craftingbench cancel <id> &7- 取消自己的制作任务")
            sender.msg("&b/craftingbench claim &7- 领取离线完成产物")
            sender.msg("&b/craftingbench tiers &7- 查看可用工作台等级")
            sender.msg("&b/craftingbench open <tier> &7- 打开指定工作台 GUI")
            sender.msg("&b/craftingbench give <player> <tier> &7- 发放对应工作台物品")
            sender.msg("&b/craftingbench admin-open <player> <tier> &7- 为玩家打开指定工作台 GUI")
            sender.msg("&b/craftingbench edit <配方ID> &7- 编辑配方GUI")
            sender.msg("&b/craftingbench reload &7- 重载制作台配置")
            sender.msg("&7当前状态：&f${if (CraftingBenchService.isEnabled()) "已启用" else "未启用"}")
        }
    }

    @CommandBody
    val queue = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            val player = sender.requirePlayer() ?: return@execute
            val tasks = CraftingBenchService.getPlayerTasks(player.uniqueId)
            val claimCount = CraftingBenchService.getPendingClaimCount(player.uniqueId)
            if (tasks.isEmpty()) {
                sender.msg("&e你当前没有制作任务。")
                if (claimCount > 0) {
                    sender.msg("&7待领取产物：&f$claimCount")
                }
                return@execute
            }
            sender.msg("&6=== 你的制作任务 ===")
            tasks.forEach { task ->
                sender.msg("&7#${task.taskId} &f${task.recipeId} &7剩余 &f${task.remainingTicks / 20.0}s")
            }
            if (claimCount > 0) {
                sender.msg("&7待领取产物：&f$claimCount")
            }
        }
    }

    @CommandBody
    val claim = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            val player = sender.requirePlayer() ?: return@execute
            val bukkitPlayer = Bukkit.getPlayer(player.uniqueId) ?: run {
                sender.msg("&c无法解析玩家实例。")
                return@execute
            }
            CraftingBenchService.flushPendingClaims(bukkitPlayer)
        }
    }

    @CommandBody
    val tiers = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            val tierIds = CraftingBenchSettings.getTierIds()
            if (tierIds.isEmpty()) {
                sender.msg("&e当前未配置任何工作台等级。")
                return@execute
            }
            sender.msg("&6=== 工作台等级 ===")
            tierIds.forEach { tierId ->
                val tier = CraftingBenchSettings.getTier(tierId) ?: return@forEach
                val blockId = CraftingBenchSettings.getPrimaryBlockIdByTier(tierId) ?: "(未映射方块)"
                sender.msg("&7$tierId &f${tier.displayName} &7速度:&f${tier.speedModifier} &7方块:&f$blockId")
            }
        }
    }

    @CommandBody
    val open = subCommand {
        dynamic(comment = "tier") {
            execute<ProxyCommandSender> { sender, _, argument ->
                val player = sender.requirePlayer() ?: return@execute
                val bukkitPlayer = Bukkit.getPlayer(player.uniqueId) ?: run {
                    sender.msg("&c无法解析玩家实例。")
                    return@execute
                }
                val tierId = argument.toString().trim()
                val tier = CraftingBenchSettings.getTier(tierId) ?: run {
                    sender.msg("&c未知工作台等级: $tierId")
                    return@execute
                }
                CraftingBenchMenu.open(bukkitPlayer, tier)
            }
        }
    }

    @CommandBody
    val give = subCommand {
        dynamic(comment = "player") {
            suggestPlayers()
            dynamic(comment = "tier") {
                execute<ProxyCommandSender> { sender, context, argument ->
                    if (!sender.requirePermission("phcore.craftingbench.admin")) {
                        return@execute
                    }
                    val targetName = context.getOrNull("player")?.toString()?.trim().orEmpty()
                    val target = Bukkit.getPlayerExact(targetName) ?: run {
                        sender.msg("&c玩家不存在或不在线: $targetName")
                        return@execute
                    }
                    val tierId = argument.toString().trim()
                    val tier = CraftingBenchSettings.getTier(tierId) ?: run {
                        sender.msg("&c未知工作台等级: $tierId")
                        return@execute
                    }
                    val blockId = CraftingBenchSettings.getPrimaryBlockIdByTier(tierId) ?: run {
                        sender.msg("&c该等级未映射任何 CraftEngine 方块: $tierId")
                        return@execute
                    }
                    val item = CraftEngineItemsUtil.getItem(blockId, target) ?: run {
                        sender.msg("&c无法构建工作台物品: $blockId")
                        return@execute
                    }
                    deliverAdminItem(target, item)
                    sender.msg("&a已发放 &f${tier.displayName} &a给 &f${target.name}&a。")
                }
            }
        }
    }

    @CommandBody
    val adminOpen = subCommand {
        dynamic(comment = "player") {
            suggestPlayers()
            dynamic(comment = "tier") {
                execute<ProxyCommandSender> { sender, context, argument ->
                    if (!sender.requirePermission("phcore.craftingbench.admin")) {
                        return@execute
                    }
                    val targetName = context.getOrNull("player")?.toString()?.trim().orEmpty()
                    val target = Bukkit.getPlayerExact(targetName) ?: run {
                        sender.msg("&c玩家不存在或不在线: $targetName")
                        return@execute
                    }
                    val tierId = argument.toString().trim()
                    val tier = CraftingBenchSettings.getTier(tierId) ?: run {
                        sender.msg("&c未知工作台等级: $tierId")
                        return@execute
                    }
                    CraftingBenchMenu.open(target, tier)
                    sender.msg("&a已为 &f${target.name} &a打开 &f${tier.displayName} &a界面。")
                }
            }
        }
    }

    @CommandBody
    val cancel = subCommand {
        dynamic("id") {
            execute<ProxyCommandSender> { sender, context, _ ->
                val player = sender.requirePlayer() ?: return@execute
                val taskId = context.getOrNull("id")?.toString()?.toLongOrNull() ?: run {
                    sender.msg("&c任务 ID 无效。")
                    return@execute
                }
                val bukkitPlayer = Bukkit.getPlayer(player.uniqueId) ?: run {
                    sender.msg("&c无法解析玩家实例。")
                    return@execute
                }
                sender.msg("&a${CraftingBenchService.cancelTask(bukkitPlayer, taskId)}")
            }
        }
    }

    @CommandBody
    val adminGui = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.msg("&c该功能已重构，请使用 /craftingbench edit <配方ID>")
        }
    }

    @CommandBody
    val edit = subCommand {
        dynamic(comment = "recipeId") {
            suggestion<ProxyCommandSender> { _, _ ->
                CraftingBenchService.getAllRecipeIds()
            }
            execute<ProxyCommandSender> { sender, _, argument ->
                if (!sender.requirePermission("phcore.craftingbench.admin")) return@execute
                val player = sender.requirePlayer() ?: return@execute
                val recipeId = argument.toString().trim()
                val bukkitPlayer = Bukkit.getPlayer(player.uniqueId) ?: return@execute
                if (CraftingBenchService.getRecipe(recipeId) == null) {
                    player.sendMessage("§e配方 '$recipeId' 不存在，将创建新配方。")
                    AdminGuiService.createEditSession(player.uniqueId, null).apply {
                        id = recipeId
                    }
                }
                AdminGuiMenu.openRecipeEditor(bukkitPlayer, recipeId)
            }
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission("phcore.craftingbench.admin")) {
                return@execute
            }
            submit(async = true) {
                CraftingBenchService.reload()
                submit {
                    sender.msg("&a制作台配置已重载。")
                }
            }
        }
    }

    private fun deliverAdminItem(player: Player, item: org.bukkit.inventory.ItemStack) {
        val leftovers = player.inventory.addItem(item)
        leftovers.values.forEach { left ->
            player.world.dropItemNaturally(player.location, left)
        }
    }
}
