package com.pixlehavencore.feature.playerinv

import com.pixlehavencore.util.ItemUtils
import org.bukkit.Material
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object PlayerInvSettings {

    @Config("feature/playerinv.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    var defaultRows: Int = 2
        private set

    var maxRows: Int = 6
        private set

    var personalRowsByPermission: List<RowPermissionRule> = emptyList()
        private set

    var sharedInitialRows: Int = 3
        private set

    var sharedMaxRows: Int = 6
        private set

    var sharedUnlockCost: Double = 1000.0
        private set

    var sharedLockedMaterial: Material = Material.BARRIER
        private set

    var title: String = "&8随身仓库 - {player}"
        private set

    var sharedTitle: String = "&8共享仓库 - {name}"
        private set

    var databaseTable: String = "player_inv"
        private set

    var sharedTable: String = "shared_inv"
        private set

    var sharedMemberTable: String = "shared_inv_member"
        private set



    var commandAliasShared: String = "wh"
        private set

    var disabledMessage: String = "&c仓库模块当前已禁用"
        private set

    var noPermissionMessage: String = "&c你没有权限使用仓库"
        private set

    var playerNotFoundMessage: String = "&c未找到玩家 {player}"
        private set

    var openSelfMessage: String = ""
        private set

    var openOtherMessage: String = "&a已打开玩家 &f{player} &a的仓库"
        private set

    var saveFailedMessage: String = "&c仓库保存失败，请稍后重试"
        private set

    var reloadMessage: String = "&a仓库配置已重载"
        private set

    var sharedCreateNoQuotaMessage: String = "&c你没有共享仓库创建次数"
        private set

    var sharedCreatedMessage: String = "&a共享仓库 &f{name} &a创建成功"
        private set

    var sharedExistsMessage: String = "&c共享仓库名称已存在 {name}"
        private set

    var sharedNotFoundMessage: String = "&c共享仓库不存在 {name}"
        private set

    var sharedNoAccessMessage: String = "&c你没有权限访问共享仓库 {name}"
        private set

    var sharedMemberAddedMessage: String = "&a已添加成员 &f{player} &a到共享仓库 &f{name}&a"
        private set

    var sharedMemberRemovedMessage: String = "&a已从共享仓库 &f{name} &a移除成员 &f{player}&a"
        private set

    var sharedUpgradeMessage: String = "&a共享仓库 &f{name} &a已升级到 &f{size} &a格"
        private set

    var sharedQuotaGrantedMessage: String = "&a已给予玩家 &f{player} &a共享仓库创建次数 &f{amount}&a"
        private set

    var sortDoneMessage: String = "&a仓库已整理"
        private set

    var sharedSortNotOwnerMessage: String = "&c只有共享仓库创建者可以整理"
        private set

    var sharedUnlockNeedMoneyMessage: String = "&c解锁失败，余额不足。需 &f{cost}"
        private set

    var sharedUnlockSuccessMessage: String = "&a已解锁共享仓库格子，消耗 &f{cost}"
        private set

    var sharedLockedName: String = "&c未解锁格子"
        private set

    var sharedLockedLore: List<String> = listOf("&7解锁费用: &f{cost}", "&e左键点击解锁")
        private set

    var sharedManagerTitle: String = "&8共享仓库管理 - {name}"
        private set

    var sharedManagerMembersItem: String = "PLAYER_HEAD"
        private set

    var sharedManagerAddItem: String = "LIME_WOOL"
        private set

    var sharedManagerRemoveItem: String = "RED_WOOL"
        private set

    var sharedManagerBackItem: String = "ARROW"
        private set

    var sharedManagerEntryItem: String = "COMPARATOR"
        private set

    var sharedManagerToggleVisibilityItem: String = "LIME_CONCRETE"
        private set

    var memberPreviewLimit: Int = 5
        private set

    var sharedManageHintLore: List<String> = listOf("&7仅创建者可使用")
        private set

    var sharedMembersName: String = "&b成员列表"
        private set

    var sharedMembersLore: List<String> = listOf("&7当前成员:", "{members}", "&e点击聊天输出完整列表")
        private set

    var sharedMembersPublicName: String = "&e公开仓库"
        private set

    var sharedMembersPublicLore: List<String> = listOf("&7此仓库为公开状态", "&7所有玩家均可访问")
        private set

    var sharedAddName: String = "&a添加使用"
        private set

    var sharedAddLore: List<String> = listOf("&7点击后在聊天输入玩家ID")
        private set

    var sharedRemoveName: String = "&c删除使用"
        private set

    var sharedRemoveLore: List<String> = listOf("&7点击后在聊天输入玩家ID")
        private set

    var sharedBackName: String = "&e返回仓库"
        private set

    var sharedBackLore: List<String> = listOf("&7返回共享仓库界面")
        private set

    var sharedToggleToPublicName: String = "&a切换为公开"
        private set

    var sharedToggleToPublicLore: List<String> = listOf("&7点击将此仓库设为公开", "&7所有人均可访问")
        private set

    var sharedToggleToPrivateName: String = "&c切换为私有"
        private set

    var sharedToggleToPrivateLore: List<String> = listOf("&7点击将此仓库设为私有", "&7仅成员可访问")
        private set

    var sharedEntryName: String = "&e仓库管理"
        private set

    var sharedEntryLore: List<String> = listOf("&7仅创建者可点击", "&e点击打开管理界面")
        private set

    var chatInputAddPrompt: String = "&e请输入要添加的玩家ID，输入 &fcancel &e取消"
        private set

    var chatInputRemovePrompt: String = "&e请输入要移除的玩家ID，输入 &fcancel &e取消"
        private set

    var chatInputCancelled: String = "&e已取消输入"
        private set

    var chatInputDone: String = "&a操作完成"
        private set

    var chatInputPlayerNotFound: String = "&c未找到玩家 {player}"
        private set

    var sharedManageNoPermission: String = "&c只有共享仓库创建者可以管理"
        private set

    var sharedSetPublicMessage: String = "&a共享仓库 &f{name} &a已设置为公开"
        private set

    var sharedSetPrivateMessage: String = "&a共享仓库 &f{name} &a已设置为私有"
        private set

    var sharedMembersChatHeader: String = "&6=== 共享仓库成员: {name} ==="
        private set

    var sharedMembersChatItem: String = "&b- {player}"
        private set

    var personalOverflowFallbackMessage: String = "&e邮件系统不可用，超额物品已返还至你的背包/脚下"
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)

        defaultRows = config.getInt("personal.default-rows", 2).coerceIn(1, 6)
        maxRows = config.getInt("personal.max-rows", 6).coerceIn(defaultRows, 6)

        personalRowsByPermission = config.getMapList("personal.rows-by-permission")
            .mapNotNull { map ->
                val permission = map["permission"]?.toString()?.trim().orEmpty()
                val rows = map["rows"]?.toString()?.toIntOrNull()?.coerceIn(defaultRows, maxRows)
                if (permission.isBlank() || rows == null) {
                    null
                } else {
                    RowPermissionRule(permission, rows)
                }
            }
            .sortedByDescending { it.rows }

        sharedInitialRows = config.getInt("shared.initial-rows", 3).coerceIn(1, 6)
        sharedMaxRows = config.getInt("shared.max-rows", 6).coerceIn(sharedInitialRows, 6)
        sharedUnlockCost = config.getDouble("shared.unlock.cost-per-slot", 1000.0).coerceAtLeast(0.0)
        sharedLockedMaterial = ItemUtils.matchMaterial(config.getString("shared.unlock.locked-material", "BARRIER"), Material.BARRIER) ?: Material.BARRIER

        title = config.getString("title") ?: "&8随身仓库 - {player}"
        sharedTitle = config.getString("shared-title") ?: "&8共享仓库 - {name}"

        databaseTable = sanitizeTableName(config.getString("database.table") ?: "player_inv")
        sharedTable = sanitizeTableName(config.getString("database.shared-table") ?: "shared_inv")
        sharedMemberTable = sanitizeTableName(config.getString("database.shared-member-table") ?: "shared_inv_member")

        commandAliasShared = config.getString("commands.shared-alias")?.trim()?.ifBlank { "pi" } ?: "pi"

        disabledMessage = config.getString("messages.disabled") ?: "&c仓库模块当前已禁用"
        noPermissionMessage = config.getString("messages.no-permission") ?: "&c你没有权限使用仓库"
        playerNotFoundMessage = config.getString("messages.player-not-found") ?: "&c未找到玩家 {player}"
        openSelfMessage = config.getString("messages.open-self") ?: ""
        openOtherMessage = config.getString("messages.open-other") ?: "&a已打开玩家 &f{player} &a的仓库"
        saveFailedMessage = config.getString("messages.save-failed") ?: "&c仓库保存失败，请稍后重试"
        reloadMessage = config.getString("messages.reload") ?: "&a仓库配置已重载"

        sharedCreateNoQuotaMessage = config.getString("messages.shared-create-no-quota") ?: "&c你没有共享仓库创建次数"
        sharedCreatedMessage = config.getString("messages.shared-created") ?: "&a共享仓库 &f{name} &a创建成功"
        sharedExistsMessage = config.getString("messages.shared-exists") ?: "&c共享仓库名称已存在 {name}"
        sharedNotFoundMessage = config.getString("messages.shared-not-found") ?: "&c共享仓库不存在 {name}"
        sharedNoAccessMessage = config.getString("messages.shared-no-access") ?: "&c你没有权限访问共享仓库 {name}"
        sharedMemberAddedMessage = config.getString("messages.shared-member-added") ?: "&a已添加成员 &f{player} &a到共享仓库 &f{name}&a"
        sharedMemberRemovedMessage = config.getString("messages.shared-member-removed") ?: "&a已从共享仓库 &f{name} &a移除成员 &f{player}&a"
        sharedUpgradeMessage = config.getString("messages.shared-upgrade") ?: "&a共享仓库 &f{name} &a已升级到 &f{size} &a格"
        sharedQuotaGrantedMessage = config.getString("messages.shared-quota-granted") ?: "&a已给予玩家 &f{player} &a共享仓库创建次数 &f{amount}&a"
        sortDoneMessage = config.getString("messages.sort-done") ?: "&a仓库已整理"
        sharedSortNotOwnerMessage = config.getString("messages.shared-sort-not-owner") ?: "&c只有共享仓库创建者可以整理"
        sharedUnlockNeedMoneyMessage = config.getString("messages.shared-unlock-need-money") ?: "&c解锁失败，余额不足。需 &f{cost}"
        sharedUnlockSuccessMessage = config.getString("messages.shared-unlock-success") ?: "&a已解锁共享仓库格子，消耗 &f{cost}"
        sharedLockedName = config.getString("messages.shared-locked-name") ?: "&c未解锁格子"
        sharedLockedLore = config.getStringList("messages.shared-locked-lore").ifEmpty { listOf("&7解锁费用: &f{cost}", "&e左键点击解锁") }

        sharedManagerTitle = config.getString("shared.manage.title") ?: "&8共享仓库管理 - {name}"
        sharedManagerMembersItem = config.getString("shared.manage.items.members.material") ?: "PLAYER_HEAD"
        sharedManagerAddItem = config.getString("shared.manage.items.add.material") ?: "LIME_WOOL"
        sharedManagerRemoveItem = config.getString("shared.manage.items.remove.material") ?: "RED_WOOL"
        sharedManagerBackItem = config.getString("shared.manage.items.back.material") ?: "ARROW"
        sharedManagerEntryItem = config.getString("shared.manage.items.entry.material") ?: "COMPARATOR"
        sharedManagerToggleVisibilityItem = config.getString("shared.manage.items.toggle-visibility.material") ?: "LIME_CONCRETE"
        memberPreviewLimit = config.getInt("shared.manage.member-preview-limit", 5).coerceAtLeast(1)
        sharedManageHintLore = config.getStringList("shared.manage.hint-lore").ifEmpty { listOf("&7仅创建者可使用") }
        sharedMembersName = config.getString("shared.manage.items.members.name") ?: "&b成员列表"
        sharedMembersLore = config.getStringList("shared.manage.items.members.lore").ifEmpty {
            listOf("&7当前成员:", "{members}", "&e点击聊天输出完整列表")
        }
        sharedMembersPublicName = config.getString("shared.manage.items.members.public-name") ?: "&e公开仓库"
        sharedMembersPublicLore = config.getStringList("shared.manage.items.members.public-lore").ifEmpty {
            listOf("&7此仓库为公开状态", "&7所有玩家均可访问")
        }
        sharedAddName = config.getString("shared.manage.items.add.name") ?: "&a添加使用"
        sharedAddLore = config.getStringList("shared.manage.items.add.lore").ifEmpty { listOf("&7点击后在聊天输入玩家ID") }
        sharedRemoveName = config.getString("shared.manage.items.remove.name") ?: "&c删除使用"
        sharedRemoveLore = config.getStringList("shared.manage.items.remove.lore").ifEmpty { listOf("&7点击后在聊天输入玩家ID") }
        sharedBackName = config.getString("shared.manage.items.back.name") ?: "&e返回仓库"
        sharedBackLore = config.getStringList("shared.manage.items.back.lore").ifEmpty { listOf("&7返回共享仓库界面") }
        sharedToggleToPublicName = config.getString("shared.manage.items.toggle-visibility.to-public-name") ?: "&a切换为公开"
        sharedToggleToPublicLore = config.getStringList("shared.manage.items.toggle-visibility.to-public-lore").ifEmpty {
            listOf("&7点击将此仓库设为公开", "&7所有人均可访问")
        }
        sharedToggleToPrivateName = config.getString("shared.manage.items.toggle-visibility.to-private-name") ?: "&c切换为私有"
        sharedToggleToPrivateLore = config.getStringList("shared.manage.items.toggle-visibility.to-private-lore").ifEmpty {
            listOf("&7点击将此仓库设为私有", "&7仅成员可访问")
        }
        sharedEntryName = config.getString("shared.manage.items.entry.name") ?: "&e仓库管理"
        sharedEntryLore = config.getStringList("shared.manage.items.entry.lore").ifEmpty { listOf("&7仅创建者可点击", "&e点击打开管理界面") }

        chatInputAddPrompt = config.getString("messages.chat-input-add-prompt") ?: "&e请输入要添加的玩家ID，输入 &fcancel &e取消"
        chatInputRemovePrompt = config.getString("messages.chat-input-remove-prompt") ?: "&e请输入要移除的玩家ID，输入 &fcancel &e取消"
        chatInputCancelled = config.getString("messages.chat-input-cancelled") ?: "&e已取消输入"
        chatInputDone = config.getString("messages.chat-input-done") ?: "&a操作完成"
        chatInputPlayerNotFound = config.getString("messages.chat-input-player-not-found") ?: "&c未找到玩家 {player}"
        sharedManageNoPermission = config.getString("messages.shared-manage-no-permission") ?: "&c只有共享仓库创建者可以管理"
        sharedSetPublicMessage = config.getString("messages.shared-set-public") ?: "&a共享仓库 &f{name} &a已设置为公开"
        sharedSetPrivateMessage = config.getString("messages.shared-set-private") ?: "&a共享仓库 &f{name} &a已设置为私有"
        sharedMembersChatHeader = config.getString("messages.shared-members-chat-header") ?: "&6=== 共享仓库成员: {name} ==="
        sharedMembersChatItem = config.getString("messages.shared-members-chat-item") ?: "&b- {player}"
        personalOverflowFallbackMessage = config.getString("messages.personal-overflow-fallback")
            ?: "&e邮件系统不可用，超额物品已返还至你的背包/脚下"
    }

    fun resolvePersonalRows(player: PlayerLike): Int {
        val byPerm = personalRowsByPermission.firstOrNull { player.hasPermission(it.permission) }?.rows ?: defaultRows
        return byPerm.coerceIn(defaultRows, maxRows)
    }

    private fun sanitizeTableName(raw: String): String {
        val cleaned = raw.trim().replace(Regex("[^A-Za-z0-9_]"), "")
        return cleaned.ifBlank { "player_inv" }
    }

    data class RowPermissionRule(
        val permission: String,
        val rows: Int
    )

    fun interface PlayerLike {
        fun hasPermission(permission: String): Boolean
    }
}
