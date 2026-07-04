package com.pixlehavencore.feature.customcraft

import com.pixlehavencore.util.ADMIN_PERMISSION
import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePermission
import com.pixlehavencore.util.requirePlayer
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand

@CommandHeader(name = "customcraft", permissionDefault = PermissionDefault.TRUE)
object CustomCraftCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
            sender.msg("<gold>=== CustomCraft 帮助 ===")
            sender.msg("<aqua>/customcraft create <id> <gray>- 创建配方（打开编辑 GUI）")
            sender.msg("<aqua>/customcraft edit <id> <gray>- 编辑已有配方")
            sender.msg("<aqua>/customcraft delete <id> <gray>- 删除指定配方")
            sender.msg("<aqua>/customcraft reload <gray>- 重载全部配方")
            sender.msg("<aqua>/customcraft list <gray>- 列出所有配方")
        }
    }

    @CommandBody
    val create = subCommand {
        dynamic(comment = "recipeId") {
            execute<ProxyCommandSender> { sender, _, argument ->
                if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                val player = sender.requirePlayer() ?: return@execute
                val id = argument.toString().trim()
                if (id.isBlank()) {
                    sender.msg("<red>配方 ID 不能为空")
                    return@execute
                }
                CustomCraftEditorMenu.open(player.cast(), id)
            }
        }
    }

    @CommandBody
    val edit = subCommand {
        dynamic(comment = "recipeId") {
            execute<ProxyCommandSender> { sender, _, argument ->
                if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                val player = sender.requirePlayer() ?: return@execute
                val id = argument.toString().trim()
                if (id.isBlank()) {
                    sender.msg("<red>配方 ID 不能为空")
                    return@execute
                }
                val recipe = CustomCraftService.getRecipe(id)
                if (recipe == null) {
                    sender.msg("<red>未找到配方: $id")
                    return@execute
                }
                CustomCraftEditorMenu.open(player.cast(), id, recipe)
            }
        }
    }

    @CommandBody
    val delete = subCommand {
        dynamic(comment = "recipeId") {
            execute<ProxyCommandSender> { sender, _, argument ->
                if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                val id = argument.toString().trim()
                if (id.isBlank()) {
                    sender.msg("<red>配方 ID 不能为空")
                    return@execute
                }
                if (CustomCraftService.deleteRecipe(id)) {
                    sender.msg("<green>配方 &e$id &a已删除")
                } else {
                    sender.msg("<red>未找到配方: $id")
                }
            }
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
            CustomCraftService.reload()
            sender.msg("<green>CustomCraft 配方已重载")
        }
    }

    @CommandBody
    val list = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
            val recipes = CustomCraftService.getAllRecipes()
            if (recipes.isEmpty()) {
                sender.msg("<gray>暂无配方")
            } else {
                sender.msg("<gold>=== 配方列表 (${recipes.size}) ===")
                recipes.forEach { r ->
                    sender.msg("<yellow>${r.id} <gray>- ${r.type.name} (${r.materials.size} 材料)")
                }
            }
        }
    }
}
