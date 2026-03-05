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

    var itemLibraryEnabled: Boolean = true
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
        itemLibraryEnabled = config.getBoolean("modules.itemLibrary", true)
    }
}
