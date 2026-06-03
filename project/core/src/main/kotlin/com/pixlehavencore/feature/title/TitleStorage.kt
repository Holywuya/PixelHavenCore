package com.pixlehavencore.feature.title

import com.pixlehavencore.util.ArimJsonUtils
import com.pixlehavencore.util.DatabaseUtils
import com.pixlehavencore.util.PerKeyLock
import com.pixlehavencore.util.cancelTaskSafely
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import taboolib.expansion.MultipleHandler
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object TitleStorage {

    private const val TABLE_NAME = "title_player_data"
    private const val KEY_ACTIVE = "active_title"
    private const val KEY_OWNED = "owned_titles"
    private const val KEY_PLAYER_NAME = "player_name"

    private val stateLock = Any()
    private val dataCache = ConcurrentHashMap<UUID, PlayerTitleState>()
    private val dirtyPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val playerLocks = PerKeyLock<UUID>()

    @Volatile
    private var handler: MultipleHandler? = null
    private var flushTask: Any? = null

    @Volatile
    private var ready: Boolean = false

    fun init() = reload()

    fun reload() {
        submitAsync { reloadInternal() }
    }

    fun stop() = shutdownInternal()

    fun isReady(): Boolean = ready

    fun getData(playerUuid: UUID): PlayerTitleState? = dataCache[playerUuid]

    fun preloadPlayer(playerUuid: UUID, playerName: String, callback: () -> Unit = {}) {
        if (dataCache.containsKey(playerUuid)) {
            callback()
            return
        }
        submitAsync {
            loadPlayerData(playerUuid, playerName)
            submit { callback() }
        }
    }

    fun activateTitle(playerUuid: UUID, titleId: String) {
        synchronized(playerLocks[playerUuid]) {
            val existing = dataCache[playerUuid] ?: return
            dataCache[playerUuid] = existing.copy(activeTitleId = titleId, updatedAt = System.currentTimeMillis())
            dirtyPlayers += playerUuid
        }
    }

    fun deactivateTitle(playerUuid: UUID) {
        synchronized(playerLocks[playerUuid]) {
            val existing = dataCache[playerUuid] ?: return
            dataCache[playerUuid] = existing.copy(activeTitleId = null, updatedAt = System.currentTimeMillis())
            dirtyPlayers += playerUuid
        }
    }

    fun addTitle(playerUuid: UUID, playerName: String, titleId: String, expiresAt: Long) {
        synchronized(playerLocks[playerUuid]) {
            val existing = dataCache[playerUuid]
            val entry = PlayerTitleEntry(titleId, System.currentTimeMillis(), expiresAt)
            if (existing == null) {
                dataCache[playerUuid] = PlayerTitleState(
                    playerUuid = playerUuid,
                    playerName = playerName,
                    activeTitleId = null,
                    ownedTitles = listOf(entry),
                    updatedAt = System.currentTimeMillis(),
                )
            } else {
                val updated = existing.ownedTitles.filter { it.titleId != titleId } + entry
                dataCache[playerUuid] = existing.copy(
                    playerName = playerName,
                    ownedTitles = updated,
                    updatedAt = System.currentTimeMillis(),
                )
            }
            dirtyPlayers += playerUuid
        }
    }

    fun removeTitle(playerUuid: UUID, titleId: String) {
        synchronized(playerLocks[playerUuid]) {
            val existing = dataCache[playerUuid] ?: return
            val updatedOwned = existing.ownedTitles.filter { it.titleId != titleId }
            val updatedActive = if (existing.activeTitleId == titleId) null else existing.activeTitleId
            dataCache[playerUuid] = existing.copy(
                activeTitleId = updatedActive,
                ownedTitles = updatedOwned,
                updatedAt = System.currentTimeMillis(),
            )
            dirtyPlayers += playerUuid
        }
    }

    /**
     * 对首次进入的玩家发放默认称号并自动装备（仅执行一次，数据入库后不再触发）
     */
    fun grantDefaultTitleIfNew(playerUuid: UUID, playerName: String) {
        if (!TitleSettings.defaultTitleEnabled || TitleSettings.defaultTitleId.isBlank()) return
        if (dataCache.containsKey(playerUuid)) return

        val currentHandler = handler ?: return
        val user = playerUuid.toString()
        runCatching {
            val activeTitle = (currentHandler.database[user, KEY_ACTIVE] as? String)?.takeIf { it.isNotBlank() }
            val ownedJson = (currentHandler.database[user, KEY_OWNED] as? String)?.takeIf { it.isNotBlank() }
            if (activeTitle != null || ownedJson != null) return

            val titleId = TitleSettings.defaultTitleId
            addTitle(playerUuid, playerName, titleId, 0L)
            if (TitleSettings.defaultTitleAutoEquip) {
                activateTitle(playerUuid, titleId)
            }
        }.onFailure { ex ->
            warning("[Title] 发放默认称号失败: ${ex.message}")
        }
    }

    fun removeExpired(playerUuid: UUID, now: Long = System.currentTimeMillis()): Boolean {
        synchronized(playerLocks[playerUuid]) {
            val existing = dataCache[playerUuid] ?: return false
            val (expired, valid) = existing.ownedTitles.partition { it.isExpired(now) }
            if (expired.isEmpty()) return false
            val updatedActive = if (expired.any { it.titleId == existing.activeTitleId }) null else existing.activeTitleId
            dataCache[playerUuid] = existing.copy(
                activeTitleId = updatedActive,
                ownedTitles = valid,
                updatedAt = now,
            )
            dirtyPlayers += playerUuid
            return true
        }
    }

    fun cleanupAllExpired() {
        val now = System.currentTimeMillis()
        dataCache.keys.toList().forEach { uuid -> removeExpired(uuid, now) }
    }

    fun getCacheSize(): Int = dataCache.size

    private fun loadPlayerData(playerUuid: UUID, playerName: String) {
        val currentHandler = handler ?: return
        val user = playerUuid.toString()
        runCatching {
            val activeTitle = (currentHandler.database[user, KEY_ACTIVE] as? String)?.takeIf { it.isNotBlank() }
            val ownedJson = (currentHandler.database[user, KEY_OWNED] as? String)?.takeIf { it.isNotBlank() }
            val storedName = (currentHandler.database[user, KEY_PLAYER_NAME] as? String)?.takeIf { it.isNotBlank() }
            val owned = if (ownedJson != null) parseOwnedTitles(ownedJson) else emptyList()
            dataCache[playerUuid] = PlayerTitleState(
                playerUuid = playerUuid,
                playerName = storedName ?: playerName,
                activeTitleId = activeTitle,
                ownedTitles = owned,
                updatedAt = System.currentTimeMillis(),
            )
        }.onFailure { ex ->
            warning("[Title] 加载玩家 $playerName 数据失败: ${ex.message}")
            warning("[Title] 玩家称号数据将无法持久化，请检查数据库配置！")
            // 不创建默认数据，避免覆盖玩家已有的称号数据
        }
    }

    private fun persistPlayer(currentHandler: MultipleHandler, playerUuid: UUID) {
        val data = dataCache[playerUuid] ?: return
        val user = playerUuid.toString()
        currentHandler.database[user, KEY_ACTIVE] = data.activeTitleId ?: ""
        currentHandler.database[user, KEY_OWNED] = if (data.ownedTitles.isEmpty()) "" else ArimJsonUtils.toJson(data.ownedTitles)
        currentHandler.database[user, KEY_PLAYER_NAME] = data.playerName
    }

    private fun flushAll() {
        val ids = dirtyPlayers.toList()
        if (ids.isEmpty()) return
        val currentHandler = handler ?: return
        runCatching {
            ids.forEach { uuid ->
                persistPlayer(currentHandler, uuid)
                dirtyPlayers.remove(uuid)
            }
        }.onFailure { ex ->
            warning("[Title] 保存数据失败: ${ex.message}")
        }
    }

    private fun scheduleFlush() {
        flushTask.cancelTaskSafely()
        flushTask = submitAsync(period = 400L) { flushAll() }
    }

    private fun reloadInternal() {
        synchronized(stateLock) {
            shutdownInternal()
            if (!TitleSettings.enabled) return
            runCatching {
                handler = DatabaseUtils.newPlayerDataHandler(TABLE_NAME, syncTick = 200L)
                loadAll()
                scheduleFlush()
                ready = true
                info("[Title] 存储层初始化完成，已加载 ${dataCache.size} 条数据。")
            }.onFailure { ex ->
                warning("[Title] 初始化失败: ${ex.message}")
                shutdownInternal()
            }
        }
    }

    private fun shutdownInternal() {
        synchronized(stateLock) {
            flushTask.cancelTaskSafely()
            flushTask = null
            if (handler != null) flushAll()
            DatabaseUtils.closeMultipleHandler(handler)
            handler = null
            dataCache.clear()
            dirtyPlayers.clear()
            playerLocks.clear()
            ready = false
        }
    }

    private fun loadAll() {
        val currentHandler = handler ?: return
        runCatching {
            val rows = currentHandler.database.getListByKey(KEY_ACTIVE)
            rows.forEach { (user, _) ->
                val uuid = runCatching { UUID.fromString(user) }.getOrNull() ?: return@forEach
                val activeTitle = (currentHandler.database[user, KEY_ACTIVE] as? String)?.takeIf { it.isNotBlank() }
                val ownedJson = (currentHandler.database[user, KEY_OWNED] as? String)?.takeIf { it.isNotBlank() }
                val playerName = (currentHandler.database[user, KEY_PLAYER_NAME] as? String)?.takeIf { it.isNotBlank() }
                val owned = if (ownedJson != null) parseOwnedTitles(ownedJson) else emptyList()
                dataCache[uuid] = PlayerTitleState(
                    playerUuid = uuid,
                    playerName = playerName ?: uuid.toString().takeLast(8),
                    activeTitleId = activeTitle,
                    ownedTitles = owned,
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }.onFailure { ex ->
            warning("[Title] 批量加载数据失败: ${ex.message}")
        }
    }

    private fun parseOwnedTitles(json: String): List<PlayerTitleEntry> {
        return runCatching {
            ArimJsonUtils.gson().fromJson(json, Array<PlayerTitleEntry>::class.java)?.toList()
        }.getOrNull() ?: emptyList()
    }
}
