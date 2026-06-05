package com.pixlehavencore.util

import com.pixlehavencore.PixleHavenSettings
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import taboolib.common.platform.ProxyPlayer
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import taboolib.expansion.MultipleHandler
import taboolib.expansion.getDataContainer
import taboolib.expansion.setupDataContainer
import taboolib.expansion.setupPlayerDatabase
import taboolib.module.configuration.Configuration
import java.io.File
import java.sql.Connection
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

    private var _sharedDataSource: HikariDataSource? = null

    fun sharedDataSource(): HikariDataSource {
        return _sharedDataSource ?: synchronized(this) {
            _sharedDataSource ?: createSharedPool().also { _sharedDataSource = it }
        }
    }

    private fun createSharedPool(): HikariDataSource {
        val poolName = "PixelHavenCore"
        val maxPoolSize = 10
        val minIdle = 1
        info("[DatabaseUtils] 初始化共享连接池 $poolName (max=$maxPoolSize, minIdle=$minIdle)")
        return HikariDataSource(newHikariConfig(poolName, maxPoolSize, minIdle))
    }

    fun newHikariDataSource(
        poolName: String,
        maxPoolSize: Int,
        minIdle: Int
    ): HikariDataSource {
        return sharedDataSource()
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
                maximumPoolSize = maxPoolSize
                minimumIdle = minIdle
                connectionTimeout = 10000
                idleTimeout = 300000
                maxLifetime = 1200000
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

    @Volatile
    private var _databaseSetup = false

    fun setupDatabase() {
        if (_databaseSetup) return
        synchronized(this) {
            if (_databaseSetup) return
            if (isMySql) {
                info("[DatabaseUtils] 注册全局 MySQL 数据库连接")
                setupPlayerDatabase(
                    host = PixleHavenSettings.mysqlHost,
                    port = PixleHavenSettings.mysqlPort.toIntOrNull() ?: 3306,
                    user = PixleHavenSettings.mysqlUser,
                    password = PixleHavenSettings.mysqlPassword,
                    database = PixleHavenSettings.mysqlDatabase,
                )
            } else {
                info("[DatabaseUtils] 注册全局 SQLite 数据库连接")
                setupPlayerDatabase(File(getDataFolder(), PixleHavenSettings.sqliteFile))
            }
            _databaseSetup = true
        }
    }

    /**
     * 创建一个 TabooLib PlayerDatabase 多表处理器（带容器缓存）。
     * 连接复用全局注册的 PlayerDatabase（由 setupDatabase() 在启动时调用一次）。
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

    fun closeDataSource() {
        synchronized(this) {
            _sharedDataSource?.close()
            _sharedDataSource = null
            _databaseSetup = false
        }
    }

    /**
     * 安全关闭 MultipleHandler：取消周期同步任务。
     * 不再关闭底层连接池（由共享池统一管理）。
     */
    fun closeMultipleHandler(handler: MultipleHandler?) {
        if (handler == null) return
        runCatching { handler.stopSync() }.onFailure { ex ->
            warning("[DatabaseUtils] stopSync 失败: ${ex.message}")
        }
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
