package com.pixlehavencore

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object PixleHavenSettings {

    @Config("settings.yml")
    private lateinit var config: Configuration

    var databaseType: String = "sqlite"
        private set

    var sqliteFile: String = "veinminer.db"
        private set

    var mysqlHost: String = "localhost"
        private set

    var mysqlPort: String = "3306"
        private set

    var mysqlDatabase: String = "veinminer"
        private set

    var mysqlUser: String = "root"
        private set

    var mysqlPassword: String = "root"
        private set

    var redisEnabled: Boolean = false
        private set

    var redisHost: String = "localhost"
        private set

    var redisPort: Int = 6379
        private set

    var redisUser: String = ""
        private set

    var redisPassword: String = ""
        private set

    var redisConnect: Int = 32
        private set

    var redisTimeout: Int = 1000
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        databaseType = config.getString("database.type")?.lowercase() ?: "sqlite"
        sqliteFile = config.getString("database.sqlite.file") ?: "veinminer.db"
        mysqlHost = config.getString("database.mysql.host") ?: "localhost"
        mysqlPort = config.getString("database.mysql.port") ?: "3306"
        mysqlDatabase = config.getString("database.mysql.database") ?: "veinminer"
        mysqlUser = config.getString("database.mysql.user") ?: "root"
        mysqlPassword = config.getString("database.mysql.password") ?: "root"
        redisEnabled = config.getBoolean("database.redis.enable", false)
        redisHost = config.getString("database.redis.host") ?: "localhost"
        redisPort = config.getInt("database.redis.port", 6379).coerceAtLeast(1)
        redisUser = config.getString("database.redis.user") ?: ""
        redisPassword = config.getString("database.redis.password") ?: ""
        redisConnect = config.getInt("database.redis.connect", 32).coerceAtLeast(1)
        redisTimeout = config.getInt("database.redis.timeout", 1000).coerceAtLeast(1)
    }
}
