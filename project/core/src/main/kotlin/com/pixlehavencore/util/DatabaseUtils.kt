package com.pixlehavencore.util

import com.pixlehavencore.PixleHavenSettings
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import taboolib.common.platform.ProxyPlayer
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import taboolib.expansion.MultipleHandler
import taboolib.expansion.getDataContainer
import taboolib.expansion.setupDataContainer
import taboolib.module.configuration.Configuration
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Timestamp

object DatabaseUtils {

    val isMySql: Boolean
        get() = PixleHavenSettings.databaseType.equals("mysql", ignoreCase = true)

    fun now(): Timestamp {
        return Timestamp(System.currentTimeMillis())
    }

    fun quoted(column: String): String {
        return if (isMySql) "`$column`" else "\"$column\""
    }

    fun initSqliteDatabase() {
        if (isMySql) return
        try {
            val url = "jdbc:sqlite:${File(getDataFolder(), PixleHavenSettings.sqliteFile).absolutePath}"
            DriverManager.getConnection(url).use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute("PRAGMA journal_mode=WAL;")
                    stmt.execute("PRAGMA synchronous=NORMAL;")
                }
            }
        } catch (e: Exception) {
            warning("初始化 SQLite 数据库失败: ${e.message}")
        }
    }

    fun newHikariDataSource(
        poolName: String,
        maxPoolSize: Int,
        minIdle: Int
    ): HikariDataSource {
        return HikariDataSource(newHikariConfig(poolName, maxPoolSize, minIdle))
    }

    fun newHikariConfig(
        poolName: String,
        maxPoolSize: Int,
        minIdle: Int
    ): HikariConfig {
        return if (isMySql) {
            HikariConfig().apply {
                this.poolName = poolName
                jdbcUrl = "jdbc:mysql://${PixleHavenSettings.mysqlHost}:${PixleHavenSettings.mysqlPort}/${PixleHavenSettings.mysqlDatabase}?useSSL=false&serverTimezone=UTC"
                username = PixleHavenSettings.mysqlUser
                password = PixleHavenSettings.mysqlPassword
                maximumPoolSize = maxPoolSize
                minimumIdle = minIdle
                connectionTimeout = 10000
                idleTimeout = 300000
                maxLifetime = 1200000
            }
        } else {
            val sqliteFile = File(getDataFolder(), PixleHavenSettings.sqliteFile).absolutePath
            HikariConfig().apply {
                this.poolName = poolName
                jdbcUrl = "jdbc:sqlite:$sqliteFile"
                maximumPoolSize = maxPoolSize.coerceAtMost(4)
                minimumIdle = minIdle
                connectionTimeout = 10000
                idleTimeout = 300000
                maxLifetime = 1200000
                connectionInitSql = "PRAGMA journal_mode=WAL; PRAGMA busy_timeout=30000;"
            }
        }
    }

    fun <T> withConnection(dataSource: HikariDataSource?, block: (Connection) -> T): T? {
        val ds = dataSource ?: return null
        ds.connection.use { connection ->
            return block(connection)
        }
    }

    fun <T> queryAsync(block: () -> T, callback: (T) -> Unit) {
        submitAsync {
            val result = block()
            submit { callback(result) }
        }
    }

    fun executeAsync(block: () -> Unit) {
        submitAsync { block() }
    }

    fun executeAsyncWithCallback(block: () -> Unit, callback: () -> Unit) {
        submitAsync {
            block()
            submit { callback() }
        }
    }

    /**
     * 创建一个 TabooLib PlayerDatabase 多表处理器（带容器缓存）。
     */
    fun newPlayerDataHandler(
        table: String,
        sqliteFile: String = PixleHavenSettings.sqliteFile,
        autoHook: Boolean = false,
        syncTick: Long = 80L
    ): MultipleHandler {
        val mysqlPort = PixleHavenSettings.mysqlPort.toIntOrNull() ?: 3306
        val conf = if (isMySql) {
            Configuration.fromMap(
                mapOf(
                    "enable" to true,
                    "host" to PixleHavenSettings.mysqlHost,
                    "port" to mysqlPort,
                    "user" to PixleHavenSettings.mysqlUser,
                    "password" to PixleHavenSettings.mysqlPassword,
                    "database" to PixleHavenSettings.mysqlDatabase,
                    "table" to table
                )
            )
        } else {
            Configuration.fromMap(
                mapOf(
                    "enable" to false,
                    "table" to table
                )
            )
        }
        return MultipleHandler(conf, table = table, dataFile = sqliteFile, autoHook = autoHook, syncTick = syncTick)
    }

    /**
     * 安全关闭 MultipleHandler：取消周期同步任务并释放底层连接池。
     */
    fun closeMultipleHandler(handler: MultipleHandler?) {
        if (handler == null) return
        runCatching { handler.stopSync() }.onFailure { ex ->
            warning("[DatabaseUtils] stopSync 失败: ${ex.message}")
        }
        runCatching {
            val ds = handler.database.dataSource
            if (ds is HikariDataSource) ds.close()
        }.onFailure { ex ->
            warning("[DatabaseUtils] 关闭数据源失败: ${ex.message}")
        }
    }

}
/**
 * 简化 MultipleHandler 生命周期管理的轻量包装器。
 * 封装 handler 创建/关闭/shutdown 状态，提供 get/set 便捷访问。
 */
class DataStore(private val tableName: String, private val syncTick: Long = 200L) {

    @Volatile
    private var handler: MultipleHandler? = null
    private val shuttingDown = java.util.concurrent.atomic.AtomicBoolean(false)

    @Volatile
    var isReady: Boolean = false
        private set

    fun init(initComplete: (Boolean) -> Unit = {}) {
        shuttingDown.set(false)
        reload(initComplete)
    }

    fun reload(initComplete: (Boolean) -> Unit = {}) {
        if (shuttingDown.get()) return
        isReady = false
        submitAsync {
            close()
            runCatching {
                handler = DatabaseUtils.newPlayerDataHandler(tableName, syncTick = syncTick)
            }.onSuccess {
                shuttingDown.set(false)
                isReady = true
                initComplete(true)
            }.onFailure { ex ->
                warning("[DataStore:$tableName] 初始化 PlayerDatabase 失败: ${ex.message}")
                close()
                isReady = true
                initComplete(false)
            }
        }
    }

    fun close(flushBlock: (() -> Unit)? = null) {
        isReady = false
        shuttingDown.set(true)
        flushBlock?.invoke()
        DatabaseUtils.closeMultipleHandler(handler)
        handler = null
    }

    fun isShuttingDown(): Boolean = shuttingDown.get()

    fun get(user: String, key: String): String? {
        return handler?.database?.get(user, key) as? String
    }

    operator fun set(user: String, key: String, value: String) {
        val h = handler ?: return
        h.database[user, key] = value
    }

    fun <T> withHandler(block: (MultipleHandler) -> T): T? {
        return handler?.let(block)
    }

    fun getListByKey(key: String): Map<String, String> {
        return handler?.database?.getListByKey(key) ?: emptyMap()
    }
}

/**
 * 确保玩家数据容器已初始化，供各模块统一复用。
 */
fun ProxyPlayer.ensureDataContainer() {
    runCatching { getDataContainer() }.getOrElse { setupDataContainer() }
}

/**
 * 异步确保玩家数据容器已初始化。
 */
fun ProxyPlayer.ensureDataContainerAsync(callback: () -> Unit) {
    submitAsync {
        runCatching { getDataContainer() }.getOrElse { setupDataContainer() }
        submit { callback() }
    }
}
