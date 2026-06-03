package com.pixlehavencore

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import taboolib.common.platform.function.warning

object PixleHavenSettings {

    @Config("settings.yml")
    private lateinit var config: Configuration

    /**
     * 数据库配置快照，确保并发读取的一致性
     */
    data class DatabaseConfig(
        val databaseType: String = "sqlite",
        val sqliteFile: String = "pixelhavencore.db",
        val mysqlHost: String = "localhost",
        val mysqlPort: String = "3306",
        val mysqlDatabase: String = "pixelhavencore",
        val mysqlUser: String = "",
        val mysqlPassword: String = ""
    )

    @Volatile
    private var dbConfig = DatabaseConfig()

    // 保持向后兼容的属性访问器
    val databaseType: String get() = dbConfig.databaseType
    val sqliteFile: String get() = dbConfig.sqliteFile
    val mysqlHost: String get() = dbConfig.mysqlHost
    val mysqlPort: String get() = dbConfig.mysqlPort
    val mysqlDatabase: String get() = dbConfig.mysqlDatabase
    val mysqlUser: String get() = dbConfig.mysqlUser
    val mysqlPassword: String get() = dbConfig.mysqlPassword

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        val newConfig = DatabaseConfig(
            databaseType = config.getString("database.type")?.lowercase() ?: "sqlite",
            sqliteFile = config.getString("database.sqlite.file") ?: "pixelhavencore.db",
            mysqlHost = config.getString("database.mysql.host") ?: "localhost",
            mysqlPort = config.getString("database.mysql.port") ?: "3306",
            mysqlDatabase = config.getString("database.mysql.database") ?: "pixelhavencore",
            mysqlUser = config.getString("database.mysql.user") ?: "",
            mysqlPassword = config.getString("database.mysql.password") ?: ""
        )
        
        // 检查 MySQL 配置是否完整
        if (newConfig.databaseType == "mysql") {
            if (newConfig.mysqlUser.isBlank()) {
                warning("[配置] MySQL 用户名未配置！请在 settings.yml 中设置 database.mysql.user")
            }
            if (newConfig.mysqlPassword.isBlank()) {
                warning("[配置] MySQL 密码未配置！请在 settings.yml 中设置 database.mysql.password")
            }
        }
        
        // 原子性地更新配置快照
        dbConfig = newConfig
    }
}
