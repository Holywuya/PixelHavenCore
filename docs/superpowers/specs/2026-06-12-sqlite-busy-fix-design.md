# SQLite 数据库锁冲突修复设计

## 日期

2026-06-12

## 问题

Folia 多线程调度器环境下，TabooLib 的 DataContainer 定期持久化将经济系统、脉络矿限制、在线时长等多个模块的数据同时异步写入同一个 SQLite 文件，导致大量 `SQLITE_BUSY` 错误。

### 根因

TabooLib 内部管理自己的 HikariCP 连接池用于 DataContainer 持久化。该内部池使用 SQLite 默认设置：
- `journal_mode=DELETE`（而非 WAL），写入时完全锁住数据库文件
- `busy_timeout=0`，遇到锁立即报错不等待

`DatabaseUtils.newHikariConfig()` 中设置的 `PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000;` 仅对项目自行创建的连接池（如 WarehousePool）生效，完全不影响 TabooLib 内部池。

## 方案

在插件启动早期（onEnable），在 TabooLib 初始化 DataContainer 之前，对 SQLite 数据库文件执行持久化 PRAGMA：

```kotlin
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
```

### 改动文件

- `project/core/src/main/kotlin/com/pixlehavencore/util/DatabaseUtils.kt`
  - 新增 `initSqliteDatabase()` 方法
  - 将现有 SQLite HikariConfig 中的 `busy_timeout` 从 `5000` 改为 `30000`（与自建池保持一致）
- 插件主类 `onEnable()`
  - 在早期调用 `initSqliteDatabase()`

### PRAGMA 说明

| PRAGMA | 作用 | 持久性 |
|--------|------|--------|
| `journal_mode=WAL` | Write-Ahead Logging，允许多读一写并发 | 持久（写入数据库文件头） |
| `synchronous=NORMAL` | 写入时不等待 fsync 落盘，提升性能 | 持久 |

### 为什么 WAL 能解决问题

WAL 模式下，写入操作写入 WAL 文件而非直接修改主数据库文件，多个连接可以同时读取，只有 WAL checkpoint 时才需要短暂的写锁。对于简单的 `UPDATE` 操作，写锁持有时间极短（<10ms），几乎不会与其他写入冲突。

### 局限性

- TabooLib 内部连接的 `busy_timeout` 仍为默认值 0，遇到极罕见的并发写入冲突时仍可能报错，但概率极低
- 如需彻底消除，可后续考虑模块独立数据库文件或写入队列

### 数据安全性

`synchronous=NORMAL` 在系统崩溃时有极低概率丢失最后几个写入操作。用户确认三个模块（经济、脉络矿、在线时长）的数据可容忍偶尔失败。
