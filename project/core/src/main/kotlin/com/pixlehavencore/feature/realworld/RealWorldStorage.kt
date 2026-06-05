package com.pixlehavencore.feature.realworld

import com.pixlehavencore.util.DatabaseUtils
import com.zaxxer.hikari.HikariDataSource
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import taboolib.expansion.MultipleHandler
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object RealWorldStorage {

    private const val PLAYER_TABLE = "ph_realworld_player"
    private const val GLOBAL_TABLE = "ph_realworld_global"
    private const val GLOBAL_ID = 1
    private const val KEY_HYDRATION = "hydration"
    private const val KEY_TEMPERATURE = "last_temperature"
    private const val KEY_FRACTURE = "fracture"

    @Volatile
    private var dataSource: HikariDataSource? = null

    @Volatile
    private var playerHandler: MultipleHandler? = null

    private val playerCache = ConcurrentHashMap<UUID, PlayerEnvState>()
    private val playerLocks = ConcurrentHashMap<UUID, Any>()
    private val dirtyPlayers = ConcurrentHashMap.newKeySet<UUID>()

    @Volatile
    private var globalDirty = false

    @Volatile
    private var lastKnownGlobalSnapshot: GlobalEnvState = GlobalEnvState()

    fun init() {
        stop()
        runCatching {
            playerHandler = DatabaseUtils.newPlayerDataHandler(PLAYER_TABLE, syncTick = 200L)
            dataSource = DatabaseUtils.newHikariDataSource("RealWorldPool", 4, 1)
            migratePlayerTable()
            createGlobalTable()
        }.onFailure { ex ->
            warning("[RealWorld] 初始化存储失败: ${ex.message}")
            stop()
        }
    }

    private fun migratePlayerTable() {
        val ds = dataSource ?: return
        DatabaseUtils.withConnection(ds) { connection ->
            // 检查 value 列是否存在
            val hasValueColumn = connection.prepareStatement("PRAGMA table_info($PLAYER_TABLE)").use { stmt ->
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        if (rs.getString("name") == "value") return@use true
                    }
                    false
                }
            }
            if (!hasValueColumn) {
                connection.prepareStatement("ALTER TABLE $PLAYER_TABLE ADD COLUMN ${quoted("value")} TEXT").use { stmt ->
                    stmt.execute()
                }
                warning("[RealWorld] 已修复 $PLAYER_TABLE 表结构：添加 value 列")
            }
        }
    }

    fun stop() {
        if (dataSource != null) {
            dirtyPlayers.toList().forEach { uuid ->
                val snapshot = getPlayerSnapshot(uuid) ?: return@forEach
                runCatching {
                    savePlayerSnapshot(uuid, snapshot)
                }.onFailure { ex ->
                    warning("[RealWorld] 停止时保存玩家环境数据失败($uuid): ${ex.message}")
                }
            }

            if (globalDirty) {
                runCatching {
                    saveGlobalSnapshot(lastKnownGlobalSnapshot.copy())
                }.onFailure { ex ->
                    warning("[RealWorld] 停止时保存全局环境数据失败: ${ex.message}")
                }
            }
        }

        DatabaseUtils.closeMultipleHandler(playerHandler)
        playerHandler = null
        // 共享连接池由 DatabaseUtils.closeDataSource() 统一管理，不在此处关闭
        dataSource = null
        playerCache.clear()
        dirtyPlayers.clear()
        globalDirty = false
    }

    fun reload() {
        init()
    }

    fun loadPlayer(uuid: UUID): PlayerEnvState {
        getPlayerSnapshot(uuid)?.let { return it }

        val currentHandler = playerHandler
        val user = uuid.toString()
        val loaded = if (currentHandler != null) {
            PlayerEnvState(
                temperature = (currentHandler.database[user, KEY_TEMPERATURE] as? String)?.toDoubleOrNull() ?: 20.0,
                hydration = (currentHandler.database[user, KEY_HYDRATION] as? String)?.toDoubleOrNull() ?: 100.0,
                fracture = (currentHandler.database[user, KEY_FRACTURE] as? String)?.toDoubleOrNull() ?: 0.0,
            )
        } else {
            PlayerEnvState()
        }

        val lock = playerLock(uuid)
        synchronized(lock) {
            val existing = playerCache[uuid]
            if (existing != null) {
                return existing
            }
            playerCache[uuid] = loaded
            return loaded
        }
    }

    fun markPlayerDirty(uuid: UUID) {
        dirtyPlayers.add(uuid)
    }

    fun savePlayer(uuid: UUID) {
        val snapshot = getPlayerSnapshot(uuid) ?: return
        val saved = savePlayerSnapshot(uuid, snapshot)
        if (saved) {
            dirtyPlayers.remove(uuid)
        }
    }

    fun removePlayerFromCache(uuid: UUID) {
        synchronized(playerLock(uuid)) {
            playerCache.remove(uuid)
            dirtyPlayers.remove(uuid)
        }
        playerLocks.remove(uuid)
    }

    fun resetPlayer(uuid: UUID) {
        synchronized(playerLock(uuid)) {
            playerCache[uuid] = PlayerEnvState()
            dirtyPlayers.add(uuid)
        }
    }

    fun getPlayerSnapshot(uuid: UUID): PlayerEnvState? {
        return synchronized(playerLock(uuid)) {
            playerCache[uuid]?.copy()
        }
    }

    fun <T> withPlayerState(uuid: UUID, block: (PlayerEnvState) -> T): T? {
        return synchronized(playerLock(uuid)) {
            val state = playerCache[uuid] ?: return null
            block(state)
        }
    }

    fun loadGlobal(): GlobalEnvState {
        val loaded = DatabaseUtils.withConnection(dataSource) { connection ->
            connection.prepareStatement(
                "SELECT ${quoted("season")}, ${quoted("season_progress")} FROM $GLOBAL_TABLE WHERE ${quoted("id")} = ?"
            ).use { statement ->
                statement.setInt(1, GLOBAL_ID)
                statement.executeQuery().use { result ->
                    if (!result.next()) {
                        return@use GlobalEnvState()
                    }
                    GlobalEnvState(
                        season = Season.fromName(result.getString("season")) ?: Season.SPRING,
                        seasonProgress = result.getDouble("season_progress"),
                    )
                }
            }
        } ?: GlobalEnvState()

        lastKnownGlobalSnapshot = loaded
        return loaded
    }

    fun markGlobalDirty() {
        globalDirty = true
    }

    fun markGlobalDirty(state: GlobalEnvState) {
        lastKnownGlobalSnapshot = state
        globalDirty = true
    }

    fun saveGlobal(state: GlobalEnvState) {
        val snapshot = state.copy()
        val saved = saveGlobalSnapshot(snapshot)
        if (saved) {
            globalDirty = false
            lastKnownGlobalSnapshot = state
        }
    }

    fun flushDirty(globalState: GlobalEnvState) {
        val playerSnapshots = dirtyPlayers.toList().mapNotNull { uuid ->
            getPlayerSnapshot(uuid)?.let { uuid to it }
        }
        val shouldSaveGlobal = globalDirty
        val globalSnapshot = globalState.copy()
        lastKnownGlobalSnapshot = globalState

        submitAsync {
            // 批量保存玩家数据（一次连接，一次 batch）
            if (playerSnapshots.isNotEmpty()) {
                runCatching {
                    batchSavePlayerSnapshots(playerSnapshots)
                }.onFailure { ex ->
                    warning("[RealWorld] 批量保存玩家环境数据失败: ${ex.message}")
                }
                // 逐个检查是否仍与当前状态一致，一致则清除脏标记
                playerSnapshots.forEach { (uuid, snapshot) ->
                    if (samePersistedPlayerState(playerCache[uuid], snapshot)) {
                        dirtyPlayers.remove(uuid)
                    }
                }
            }

            if (shouldSaveGlobal) {
                runCatching {
                    val saved = saveGlobalSnapshot(globalSnapshot)
                    if (saved && samePersistedGlobalState(globalState, globalSnapshot)) {
                        globalDirty = false
                    }
                }.onFailure { ex ->
                    warning("[RealWorld] 保存全局环境数据失败: ${ex.message}")
                }
            }
        }
    }

    fun getPlayerCache(): ConcurrentHashMap<UUID, PlayerEnvState> = playerCache

    private fun playerLock(uuid: UUID): Any {
        return playerLocks.computeIfAbsent(uuid) { Any() }
    }

    private fun createGlobalTable() {
        DatabaseUtils.withConnection(dataSource) { connection ->
            connection.prepareStatement(
                """
                CREATE TABLE IF NOT EXISTS $GLOBAL_TABLE (
                    ${quoted("id")} INT NOT NULL,
                    ${quoted("season")} VARCHAR(16) NOT NULL DEFAULT 'SPRING',
                    ${quoted("season_progress")} DOUBLE NOT NULL DEFAULT 0.0,
                    ${quoted("weather")} VARCHAR(16) NOT NULL DEFAULT 'CLEAR',
                    ${quoted("weather_intensity")} DOUBLE NOT NULL DEFAULT 0.5,
                    ${quoted("updated_at")} TIMESTAMP NOT NULL,
                    PRIMARY KEY (${quoted("id")})
                )
                """.trimIndent()
            ).use { statement ->
                statement.execute()
            }
        }
    }

    private fun savePlayerSnapshot(uuid: UUID, snapshot: PlayerEnvState): Boolean {
        val currentHandler = playerHandler ?: return false
        val user = uuid.toString()
        currentHandler.database[user, KEY_HYDRATION] = snapshot.hydration.toString()
        currentHandler.database[user, KEY_TEMPERATURE] = snapshot.temperature.toString()
        currentHandler.database[user, KEY_FRACTURE] = snapshot.fracture.toString()
        return true
    }

    private fun batchSavePlayerSnapshots(snapshots: List<Pair<UUID, PlayerEnvState>>) {
        val currentHandler = playerHandler ?: return
        for ((uuid, snapshot) in snapshots) {
            val user = uuid.toString()
            currentHandler.database[user, KEY_HYDRATION] = snapshot.hydration.toString()
            currentHandler.database[user, KEY_TEMPERATURE] = snapshot.temperature.toString()
            currentHandler.database[user, KEY_FRACTURE] = snapshot.fracture.toString()
        }
    }

    private fun saveGlobalSnapshot(snapshot: GlobalEnvState): Boolean {
        return DatabaseUtils.withConnection(dataSource) { connection ->
            connection.prepareStatement(globalUpsertSql()).use { statement ->
                statement.setInt(1, GLOBAL_ID)
                statement.setString(2, snapshot.season.name)
                statement.setDouble(3, snapshot.seasonProgress)
                statement.setTimestamp(4, DatabaseUtils.now())
                statement.executeUpdate()
                true
            }
        } ?: false
    }

    private fun samePersistedPlayerState(current: PlayerEnvState?, snapshot: PlayerEnvState): Boolean {
        if (current == null) return false
        val tolerance = 0.001
        return Math.abs(current.hydration - snapshot.hydration) < tolerance &&
            Math.abs(current.temperature - snapshot.temperature) < tolerance &&
            Math.abs(current.fracture - snapshot.fracture) < tolerance
    }

    private fun samePersistedGlobalState(current: GlobalEnvState, snapshot: GlobalEnvState): Boolean {
        return current.season == snapshot.season &&
            current.seasonProgress == snapshot.seasonProgress
    }

    private fun globalUpsertSql(): String {
        return if (DatabaseUtils.isMySql) {
            "INSERT INTO $GLOBAL_TABLE (${quoted("id")}, ${quoted("season")}, ${quoted("season_progress")}, ${quoted("updated_at")}) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE ${quoted("season")} = VALUES(${quoted("season")}), ${quoted("season_progress")} = VALUES(${quoted("season_progress")}), ${quoted("updated_at")} = VALUES(${quoted("updated_at")})"
        } else {
            "INSERT OR REPLACE INTO $GLOBAL_TABLE (${quoted("id")}, ${quoted("season")}, ${quoted("season_progress")}, ${quoted("updated_at")}) VALUES (?, ?, ?, ?)"
        }
    }

    private fun quoted(column: String): String {
        return DatabaseUtils.quoted(column)
    }
}
