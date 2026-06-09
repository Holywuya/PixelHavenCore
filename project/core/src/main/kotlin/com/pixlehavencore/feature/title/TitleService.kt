package com.pixlehavencore.feature.title

import com.pixlehavencore.util.PlaceholderUtils.resolvePlaceholders
import com.pixlehavencore.util.cancelTaskSafely
import com.pixlehavencore.util.parseDurationMillis
import org.bukkit.entity.Player
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import com.pixlehavencore.util.TextUtils
import java.util.UUID

object TitleService {

    private var expiryTask: Any? = null

    fun init() {
        stop()
        TitleSettings.init()
        TitleStorage.init()
        startExpiryTask()
    }

    fun reload() {
        stop()
        TitleSettings.reload()
        TitleStorage.reload()
        startExpiryTask()
        info("[Title] 模块已重载。")
    }

    fun stop() {
        expiryTask.cancelTaskSafely()
        expiryTask = null
    }

    fun isEnabled(): Boolean = TitleSettings.enabled

    fun onPlayerJoin(player: Player) {
        TitleStorage.grantDefaultTitleIfNew(player.uniqueId, player.name)
        TitleStorage.preloadPlayer(player.uniqueId, player.name) {
            TitleStorage.removeExpired(player.uniqueId)
        }
    }

    fun onPlayerQuit(player: Player) {}

    fun activateTitle(player: Player, titleId: String): TitleResult {
        if (!TitleSettings.enabled) return TitleResult.Disabled
        val definition = TitleSettings.getTitle(titleId) ?: return TitleResult.NotFound
        val state = TitleStorage.getData(player.uniqueId) ?: return TitleResult.NotLoaded
        if (definition.permission.isNotBlank()) {
            if (!player.hasPermission(definition.permission)) {
                return TitleResult.NoPermission
            }
        } else {
            val entry = state.ownedTitles.find { it.titleId == titleId } ?: return TitleResult.NotOwned
            if (entry.isExpired()) return TitleResult.Expired
        }
        TitleStorage.activateTitle(player.uniqueId, titleId)
        player.sendMessage(TextUtils.parseAll(TitleSettings.msgActivated.resolvePlaceholders("{title}" to definition.displayName)))
        return TitleResult.Success
    }

    fun deactivateTitle(player: Player) {
        if (!TitleSettings.enabled) return
        TitleStorage.deactivateTitle(player.uniqueId)
        player.sendMessage(TextUtils.parseAll(TitleSettings.msgDeactivated))
    }

    fun grantTitle(player: Player, titleId: String, expiresAt: Long): TitleResult {
        if (!TitleSettings.enabled) return TitleResult.Disabled
        if (TitleSettings.getTitle(titleId) == null) return TitleResult.NotFound
        TitleStorage.addTitle(player.uniqueId, player.name, titleId, expiresAt)
        return TitleResult.Success
    }

    fun grantTitleOffline(playerUuid: UUID, playerName: String, titleId: String, expiresAt: Long): TitleResult {
        if (!TitleSettings.enabled) return TitleResult.Disabled
        if (TitleSettings.getTitle(titleId) == null) return TitleResult.NotFound
        TitleStorage.addTitle(playerUuid, playerName, titleId, expiresAt)
        return TitleResult.Success
    }

    fun revokeTitle(playerUuid: UUID, titleId: String): TitleResult {
        if (!TitleSettings.enabled) return TitleResult.Disabled
        if (TitleSettings.getTitle(titleId) == null) return TitleResult.NotFound
        TitleStorage.removeTitle(playerUuid, titleId)
        return TitleResult.Success
    }

    fun getTitlePreviews(player: Player, category: String? = null): List<TitlePreview> {
        val state = TitleStorage.getData(player.uniqueId)
        val definitions = if (category != null) {
            TitleSettings.getTitlesByCategory(category)
        } else {
            TitleSettings.getAllTitles().toList()
        }
        val now = System.currentTimeMillis()
        return definitions.mapNotNull { def ->
            val entry = resolveTitleEntry(player, def, state)
            if (entry == null) return@mapNotNull null
            if (entry.isExpired(now)) return@mapNotNull null
            val isActive = state?.activeTitleId == def.id
            val remaining = if (!entry.isPermanent) {
                (entry.expiresAt - now).coerceAtLeast(0)
            } else null
            TitlePreview(def, entry, isActive, false, remaining)
        }
    }

    /**
     * 解析玩家对某个称号的拥有记录。
     * 对于配置了 permission 的称号，拥有权限即视为拥有（返回虚拟永久记录）。
     * 对于未配置 permission 的称号，返回数据库中的记录。
     */
    fun resolveTitleEntry(player: Player, definition: TitleDefinition, state: PlayerTitleState?): PlayerTitleEntry? {
        if (definition.permission.isNotBlank()) {
            return if (player.hasPermission(definition.permission)) {
                PlayerTitleEntry(definition.id, System.currentTimeMillis(), 0L)
            } else null
        }
        return state?.ownedTitles?.find { it.titleId == definition.id }
    }

    fun getActiveTitle(uuid: UUID): TitleDefinition? {
        val state = TitleStorage.getData(uuid) ?: return null
        val titleId = state.activeTitleId ?: return null
        return TitleSettings.getTitle(titleId)
    }

    fun getActiveTitleDisplay(uuid: UUID): String {
        val title = getActiveTitle(uuid)
        return title?.displayName ?: TitleSettings.msgNoTitleActive
    }

    fun getAllTitleIds(): List<String> = TitleSettings.getAllTitleIds()

    fun getCategories(): List<String> = TitleSettings.getCategories()

    fun parseDuration(input: String): Long = parseDurationMillis(input)

    private fun startExpiryTask() {
        if (!TitleSettings.enabled) return
        expiryTask = submitAsync(period = TitleSettings.expiryCheckTicks) {
            runCatching {
                TitleStorage.cleanupAllExpired()
            }.onFailure { ex ->
                warning("[Title] 过期清理任务异常: ${ex.message}")
            }
        }
    }
}

enum class TitleResult {
    Success,
    Disabled,
    NotFound,
    NotOwned,
    Expired,
    NoPermission,
    NotLoaded,
}
