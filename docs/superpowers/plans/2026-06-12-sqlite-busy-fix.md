# SQLite 锁冲突修复实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在插件启动时对 SQLite 数据库文件设置 WAL 模式和 synchronous=NORMAL，消除 DataContainer 并发写入锁冲突。

**架构：** 在 `DatabaseUtils` 中新增 `initSqliteDatabase()` 方法，在插件 `onEnable` 早期调用，利用 SQLite PRAGMA 的持久化特性使 TabooLib 内部连接池自动继承 WAL 设置。同时将自建连接池的 `busy_timeout` 从 5 秒提升到 30 秒。

**技术栈：** Kotlin、JDBC (java.sql.DriverManager)、SQLite PRAGMA

---

### 任务 1：在 DatabaseUtils 中新增 initSqliteDatabase() 并调整 busy_timeout

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/util/DatabaseUtils.kt`

- [ ] **步骤 1：添加 import**

在 `import java.sql.Connection` 之后添加：

```kotlin
import java.sql.DriverManager
```

- [ ] **步骤 2：新增 initSqliteDatabase() 方法**

在 `newHikariDataSource()` 方法之前（约第 31 行）插入：

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

- [ ] **步骤 3：调整 busy_timeout**

将第 67 行的 `busy_timeout=5000` 改为 `busy_timeout=30000`：

```kotlin
// 修改前：
connectionInitSql = "PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000;"
// 修改后：
connectionInitSql = "PRAGMA journal_mode=WAL; PRAGMA busy_timeout=30000;"
```

- [ ] **步骤 4：确认修改无误**

阅读 DatabaseUtils.kt 确认 import、新方法、busy_timeout 都已正确修改。

---

### 任务 2：在 onEnable 中调用 initSqliteDatabase()

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/PixleHavenCore.kt`

- [ ] **步骤 1：添加 import**

在 `import com.pixlehavencore.util.ItemUtils` 之后添加：

```kotlin
import com.pixlehavencore.util.DatabaseUtils
```

- [ ] **步骤 2：在 onEnable 早期调用**

在 `PixleHavenSettings.init()` 之后、`VeinminerSettings.init()` 之前插入：

```kotlin
DatabaseUtils.initSqliteDatabase()
```

即 onEnable 变为：

```kotlin
override fun onEnable() {
    ConfigAlignService.alignAll()
    PixleHavenSettings.init()
    DatabaseUtils.initSqliteDatabase()   // ← 新增：在 TabooLib DataContainer 初始化前设置 WAL
    VeinminerSettings.init()
    // ... 其余不变
}
```

- [ ] **步骤 3：确认调用位置正确**

`initSqliteDatabase()` 必须在 `VeinminerLimitService.init()`（第 57 行，内部调用 `setupPlayerDatabase`）和 `PlayerInvService.init()`（第 64 行）之前执行。当前插入位置在第 56 行后，满足条件。

---

### 任务 3：构建验证并提交

- [ ] **步骤 1：构建项目**

```bash
./gradlew build
```

预期：`BUILD SUCCESSFUL`

- [ ] **步骤 2：提交**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/util/DatabaseUtils.kt project/core/src/main/kotlin/com/pixlehavencore/PixleHavenCore.kt
git commit -m "fix(database): 在启动时预初始化 SQLite WAL 模式以解决数据库锁冲突

- 新增 DatabaseUtils.initSqliteDatabase()，在插件启动早期对 SQLite 文件设置 PRAGMA journal_mode=WAL 和 synchronous=NORMAL
- 将自建连接池的 busy_timeout 从 5s 提升到 30s
- 在 onEnable 中调用，确保在 TabooLib DataContainer 初始化前生效"
```
