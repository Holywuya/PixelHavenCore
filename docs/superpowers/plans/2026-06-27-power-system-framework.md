# 工业模块 — 电力系统框架 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 实现工业模块的电力系统框架——领地能量池、发电机注册（被动+燃料型）、SQLite 持久化、Dominion 桥接、定时能量产出调度。

**架构：** 7 个新文件 + 2 个修改。事件驱动：CraftEngine 方块放置/破坏 → Dominion API 查领地 → GeneratorRegistry 匹配发电机类型 → EnergyPool 注册 → 调度器每 tick 产出能量 → 60 秒批量写 SQLite。

**技术栈：** Kotlin, TabooLib, Paper 1.21.11, HikariCP + SQLite, Dominion API（可选依赖）, CraftEngine API（可选依赖）

---

### 任务 1：数据模型 — EnergyPool + GeneratorType

**文件：**
- 创建：`project/core/src/main/kotlin/com/pixlehavencore/feature/industry/power/EnergyPool.kt`

- [ ] **步骤 1：创建 EnergyPool.kt，定义数据类和接口**

```kotlin
package com.pixlehavencore.feature.industry.power

data class EnergyPool(
    val dominionId: String,
    var energy: Double = 0.0,
    var capacity: Double = 0.0,
    val generators: MutableList<GeneratorRecord> = mutableListOf(),
    var lastTickTime: Long = System.currentTimeMillis()
)

data class GeneratorRecord(
    val type: GeneratorType,
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int
)

interface GeneratorType {
    val id: String
    val displayName: String
    val generatePerSecond: Double
    val capacityContribution: Double
    fun tick(pool: EnergyPool): Double
}

class PassiveGenerator(
    override val id: String,
    override val displayName: String,
    override val generatePerSecond: Double,
    override val capacityContribution: Double
) : GeneratorType {
    override fun tick(pool: EnergyPool): Double = generatePerSecond
}

class FuelGenerator(
    override val id: String,
    override val displayName: String,
    override val generatePerSecond: Double,
    override val capacityContribution: Double
) : GeneratorType {
    override fun tick(pool: EnergyPool): Double {
        return 0.0 // 燃料系统由后续子模块实现
    }
}
```

- [ ] **步骤 2：Build 验证**

```bash
./gradlew build
```

预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/industry/power/EnergyPool.kt
git commit -m "feat(industry): 新增 EnergyPool 和 GeneratorType 数据模型"
```

---

### 任务 2：配置层 — PowerSettings + power.yml

**文件：**
- 创建：`project/core/src/main/kotlin/com/pixlehavencore/feature/industry/power/PowerSettings.kt`
- 创建：`project/core/src/main/resources/feature/industry/power.yml`

- [ ] **步骤 1：创建 power.yml 配置文件**

```yaml
# feature/industry/power.yml
version: 1
enabled: true
maxEnergyPerDominion: 100000.0
generators: {}
```

- [ ] **步骤 2：创建 PowerSettings.kt**

```kotlin
package com.pixlehavencore.feature.industry.power

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object PowerSettings {

    @Config("feature/industry/power.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    var maxEnergyPerDominion: Double = 100000.0
        private set

    data class GeneratorConfig(
        val id: String,
        val type: String,
        val craftengineId: String,
        val displayName: String,
        val generatePerSecond: Double,
        val capacityContribution: Double
    )

    var generators: List<GeneratorConfig> = emptyList()
        private set

    fun init() = reload()

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        maxEnergyPerDominion = config.getDouble("maxEnergyPerDominion") ?: 100000.0

        generators = config.getConfigurationSection("generators")?.getKeys(false)?.mapNotNull { key ->
            val section = config.getConfigurationSection("generators.$key") ?: return@mapNotNull null
            GeneratorConfig(
                id = key,
                type = section.getString("type") ?: return@mapNotNull null,
                craftengineId = section.getString("craftengineId") ?: return@mapNotNull null,
                displayName = section.getString("displayName") ?: key,
                generatePerSecond = section.getDouble("generatePerSecond") ?: 0.0,
                capacityContribution = section.getDouble("capacityContribution") ?: 0.0
            )
        } ?: emptyList()
    }
}
```

- [ ] **步骤 3：Build 验证**

```bash
./gradlew build
```

预期：BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/industry/power/PowerSettings.kt project/core/src/main/resources/feature/industry/power.yml
git commit -m "feat(industry): 新增 PowerSettings 配置和 power.yml"
```

---

### 任务 3：持久化层 — PowerStorage

**文件：**
- 创建：`project/core/src/main/kotlin/com/pixlehavencore/feature/industry/power/PowerStorage.kt`

- [ ] **步骤 1：创建 PowerStorage.kt**

```kotlin
package com.pixlehavencore.feature.industry.power

import com.pixlehavencore.util.DatabaseUtils
import com.zaxxer.hikari.HikariDataSource
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.warning
import java.sql.Connection

object PowerStorage {

    private var dataSource: HikariDataSource? = null

    fun init() {
        dataSource = DatabaseUtils.newHikariDataSource("industry_power", maxPoolSize = 2, minIdle = 1)
        createTables()
    }

    fun close() {
        dataSource?.close()
        dataSource = null
    }

    private fun createTables() {
        execute("""
            CREATE TABLE IF NOT EXISTS industry_power_pool (
                dominion_id TEXT PRIMARY KEY,
                energy REAL NOT NULL DEFAULT 0,
                capacity REAL NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL DEFAULT 0
            )
        """)
        execute("""
            CREATE TABLE IF NOT EXISTS industry_power_generator (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                dominion_id TEXT NOT NULL,
                generator_type TEXT NOT NULL,
                world TEXT NOT NULL,
                x INTEGER NOT NULL,
                y INTEGER NOT NULL,
                z INTEGER NOT NULL,
                UNIQUE(world, x, y, z)
            )
        """)
    }

    fun loadAllPools(): Map<String, EnergyPool> {
        val pools = mutableMapOf<String, EnergyPool>()
        query("SELECT dominion_id, energy, capacity FROM industry_power_pool") { rs ->
            val pool = EnergyPool(
                dominionId = rs.getString("dominion_id"),
                energy = rs.getDouble("energy"),
                capacity = rs.getDouble("capacity")
            )
            pools[pool.dominionId] = pool
        }
        return pools
    }

    fun loadGenerators(dominionId: String, registry: GeneratorRegistry): List<GeneratorRecord> {
        val records = mutableListOf<GeneratorRecord>()
        query("SELECT generator_type, world, x, y, z FROM industry_power_generator WHERE dominion_id = ?", dominionId) { rs ->
            val type = registry.get(rs.getString("generator_type"))
            if (type != null) {
                records.add(GeneratorRecord(
                    type = type,
                    world = rs.getString("world"),
                    x = rs.getInt("x"),
                    y = rs.getInt("y"),
                    z = rs.getInt("z")
                ))
            }
        }
        return records
    }

    fun savePool(pool: EnergyPool) {
        execute("""
            INSERT OR REPLACE INTO industry_power_pool (dominion_id, energy, capacity, updated_at)
            VALUES (?, ?, ?, ?)
        """, pool.dominionId, pool.energy, pool.capacity, System.currentTimeMillis())
    }

    fun saveAllPools(pools: Collection<EnergyPool>) {
        val ds = dataSource ?: return
        ds.connection.use { conn ->
            conn.autoCommit = false
            val stmt = conn.prepareStatement(
                "INSERT OR REPLACE INTO industry_power_pool (dominion_id, energy, capacity, updated_at) VALUES (?, ?, ?, ?)"
            )
            pools.forEach { pool ->
                stmt.setString(1, pool.dominionId)
                stmt.setDouble(2, pool.energy)
                stmt.setDouble(3, pool.capacity)
                stmt.setLong(4, System.currentTimeMillis())
                stmt.addBatch()
            }
            stmt.executeBatch()
            conn.commit()
        }
    }

    fun addGenerator(dominionId: String, generatorTypeId: String, world: String, x: Int, y: Int, z: Int) {
        execute("""
            INSERT INTO industry_power_generator (dominion_id, generator_type, world, x, y, z)
            VALUES (?, ?, ?, ?, ?, ?)
        """, dominionId, generatorTypeId, world, x, y, z)
    }

    fun removeGenerator(world: String, x: Int, y: Int, z: Int) {
        execute("DELETE FROM industry_power_generator WHERE world = ? AND x = ? AND y = ? AND z = ?", world, x, y, z)
    }

    private fun execute(sql: String, vararg params: Any) {
        val ds = dataSource ?: return
        ds.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                params.forEachIndexed { i, p -> stmt.setObject(i + 1, p) }
                stmt.executeUpdate()
            }
        }
    }

    private fun query(sql: String, vararg params: Any, block: (java.sql.ResultSet) -> Unit) {
        val ds = dataSource ?: return
        ds.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                params.forEachIndexed { i, p -> stmt.setObject(i + 1, p) }
                stmt.executeQuery().use { rs ->
                    while (rs.next()) block(rs)
                }
            }
        }
    }
}
```

- [ ] **步骤 2：Build 验证**

```bash
./gradlew build
```

预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/industry/power/PowerStorage.kt
git commit -m "feat(industry): 新增 PowerStorage 持久化层"
```

---

### 任务 4：DominionBridge 桥接层

**文件：**
- 创建：`project/core/src/main/kotlin/com/pixlehavencore/feature/industry/power/DominionBridge.kt`

- [ ] **步骤 1：创建 DominionBridge.kt**

```kotlin
package com.pixlehavencore.feature.industry.power

import org.bukkit.Bukkit
import org.bukkit.Location

object DominionBridge {

    private var available: Boolean = false

    fun init() {
        available = Bukkit.getPluginManager().getPlugin("Dominion") != null
    }

    fun isAvailable(): Boolean = available

    fun getDominionId(location: Location): String? {
        if (!available) return null
        return try {
            val plugin = Bukkit.getPluginManager().getPlugin("Dominion") ?: return null
            val result = plugin.javaClass.getMethod(
                "getDominionIdByLocation", Location::class.java
            ).invoke(plugin, location)
            result as? String
        } catch (e: Exception) {
            null
        }
    }
}
```

> **注意：** `getDominionIdByLocation` 是占位方法名。如果 Dominion 的实际 API 不同，请替换为正确的类名和方法签名。

- [ ] **步骤 2：Build 验证**

```bash
./gradlew build
```

预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/industry/power/DominionBridge.kt
git commit -m "feat(industry): 新增 DominionBridge 领地桥接层"
```

---

### 任务 5：GeneratorRegistry 发电机注册表

**文件：**
- 创建：`project/core/src/main/kotlin/com/pixlehavencore/feature/industry/power/GeneratorRegistry.kt`

- [ ] **步骤 1：创建 GeneratorRegistry.kt**

```kotlin
package com.pixlehavencore.feature.industry.power

import java.util.concurrent.ConcurrentHashMap

object GeneratorRegistry {

    private val byConfigId = ConcurrentHashMap<String, GeneratorType>()
    private val byCraftengineId = ConcurrentHashMap<String, GeneratorType>()

    fun reload() {
        byConfigId.clear()
        byCraftengineId.clear()

        for (config in PowerSettings.generators) {
            val generator = when (config.type.lowercase()) {
                "passive" -> PassiveGenerator(
                    id = config.id,
                    displayName = config.displayName,
                    generatePerSecond = config.generatePerSecond,
                    capacityContribution = config.capacityContribution
                )
                "fuel" -> FuelGenerator(
                    id = config.id,
                    displayName = config.displayName,
                    generatePerSecond = config.generatePerSecond,
                    capacityContribution = config.capacityContribution
                )
                else -> continue
            }
            byConfigId[config.id] = generator
            byCraftengineId[config.craftengineId] = generator
        }
    }

    fun get(configId: String): GeneratorType? = byConfigId[configId]

    fun getByCraftengineId(craftengineId: String): GeneratorType? = byCraftengineId[craftengineId]
}
```

- [ ] **步骤 2：Build 验证**

```bash
./gradlew build
```

预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/industry/power/GeneratorRegistry.kt
git commit -m "feat(industry): 新增 GeneratorRegistry 发电机注册表"
```

---

### 任务 6：核心服务 — PowerService

**文件：**
- 创建：`project/core/src/main/kotlin/com/pixlehavencore/feature/industry/power/PowerService.kt`

- [ ] **步骤 1：创建 PowerService.kt**

```kotlin
package com.pixlehavencore.feature.industry.power

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.warning
import java.util.concurrent.ConcurrentHashMap

object PowerService {

    private val pools = ConcurrentHashMap<String, EnergyPool>()
    private var tickTaskActive = false

    fun init() {
        PowerSettings.init()
        GeneratorRegistry.reload()
        DominionBridge.init()
        PowerStorage.init()

        if (!PowerSettings.enabled) {
            info("[Industry-Power] 模块已禁用")
            return
        }

        startTickTask()
        info("[Industry-Power] 已启动，已加载 ${pools.size} 个领地能量池")
    }

    fun reload() {
        stopTickTask()
        PowerSettings.reload()
        GeneratorRegistry.reload()
        startTickTask()
    }

    fun stop() {
        stopTickTask()
        PowerStorage.close()
        pools.clear()
    }

    private fun startTickTask() {
        tickTaskActive = true
        submit(async = true, period = 20) {
            if (!tickTaskActive) {
                cancel()
                return@submit
            }
            tick()
        }
    }

    private fun stopTickTask() {
        tickTaskActive = false
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        for ((_, pool) in pools) {
            val elapsedSeconds = ((now - pool.lastTickTime) / 1000.0).coerceAtMost(60.0)
            if (elapsedSeconds <= 0.0) continue
            pool.lastTickTime = now

            var totalGenerated = 0.0
            for (gen in pool.generators) {
                totalGenerated += gen.type.tick(pool) * elapsedSeconds
            }
            pool.energy = (pool.energy + totalGenerated).coerceIn(0.0, pool.capacity)
        }
    }

    fun getPool(dominionId: String): EnergyPool? = pools[dominionId]

    fun getAllPools(): Map<String, EnergyPool> = pools.toMap()

    fun getOrCreatePool(dominionId: String): EnergyPool {
        return pools.getOrPut(dominionId) {
            EnergyPool(dominionId = dominionId, capacity = PowerSettings.maxEnergyPerDominion)
        }
    }

    private fun recalculateCapacity(pool: EnergyPool) {
        pool.capacity = PowerSettings.maxEnergyPerDominion + pool.generators.sumOf { it.type.capacityContribution }
        if (pool.energy > pool.capacity) pool.energy = pool.capacity
    }

    fun addGenerator(dominionId: String, generatorType: GeneratorType, location: Location) {
        val pool = getOrCreatePool(dominionId)
        val record = GeneratorRecord(
            type = generatorType,
            world = location.world?.name ?: return,
            x = location.blockX,
            y = location.blockY,
            z = location.blockZ
        )
        pool.generators.add(record)
        recalculateCapacity(pool)
        PowerStorage.savePool(pool)
        PowerStorage.addGenerator(dominionId, generatorType.id, record.world, record.x, record.y, record.z)
    }

    fun removeGenerator(location: Location): Boolean {
        val world = location.world?.name ?: return false
        for ((_, pool) in pools) {
            val removed = pool.generators.removeAll { gen ->
                gen.world == world && gen.x == location.blockX && gen.y == location.blockY && gen.z == location.blockZ
            }
            if (removed) {
                recalculateCapacity(pool)
                PowerStorage.savePool(pool)
                PowerStorage.removeGenerator(world, location.blockX, location.blockY, location.blockZ)
                return true
            }
        }
        return false
    }

    fun getGeneratorTypeByCraftengineId(craftengineId: String): GeneratorType? {
        return GeneratorRegistry.getByCraftengineId(craftengineId)
    }
}
```

- [ ] **步骤 2：Build 验证**

```bash
./gradlew build
```

预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/industry/power/PowerService.kt
git commit -m "feat(industry): 新增 PowerService 核心服务（调度器+能量池管理）"
```

---

### 任务 7：管理命令 — PowerCommand

**文件：**
- 创建：`project/core/src/main/kotlin/com/pixlehavencore/feature/industry/power/PowerCommand.kt`

- [ ] **步骤 1：创建 PowerCommand.kt**

```kotlin
package com.pixlehavencore.feature.industry.power

import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePermission
import com.pixlehavencore.util.ADMIN_PERMISSION
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand

@CommandHeader(name = "industry", permissionDefault = PermissionDefault.TRUE)
object PowerCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
            sender.msg("<gold>=== 工业模块帮助 ===")
            sender.msg("<aqua>/industry power info <gray>- 查看所有领地能量信息")
            sender.msg("<aqua>/industry power reload <gray>- 重载电力模块配置")
        }
    }

    @CommandBody
    val power = subCommand {
        literal("info") {
            execute<ProxyCommandSender> { sender, _, _ ->
                if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                sender.msg("<gold>=== 领地能量信息 ===")
                if (!PowerSettings.enabled) {
                    sender.msg("<red>电力模块未启用")
                    return@execute
                }
                val allPools = PowerService.getAllPools()
                if (allPools.isEmpty()) {
                    sender.msg("<gray>暂无领地能量数据")
                } else {
                    allPools.forEach { (id, pool) ->
                        sender.msg("<yellow>领地: <white>$id")
                        sender.msg("  <gray>能量: <white>${String.format("%.1f", pool.energy)} / ${String.format("%.1f", pool.capacity)}")
                        sender.msg("  <gray>发电机数量: <white>${pool.generators.size}")
                    }
                }
            }
        }

        literal("reload") {
            execute<ProxyCommandSender> { sender, _, _ ->
                if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                PowerService.reload()
                sender.msg("<green>电力模块已重载")
            }
        }
    }
}
```

> **注意：** `info` 子命令的反射访问 `PowerService` 内部 pools 需要在 PowerService 中暴露。当前占位，会在后续完善。

- [ ] **步骤 2：Build 验证**

```bash
./gradlew build
```

预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/industry/power/PowerCommand.kt
git commit -m "feat(industry): 新增 /industry power 管理命令"
```

---

### 任务 8：集成 — PixleHavenCore.kt + mainCommand.kt

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/PixleHavenCore.kt`
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/mainCommand.kt`

- [ ] **步骤 1：在 PixleHavenCore.kt 中注册生命周期**

在导入区域添加：
```kotlin
import com.pixlehavencore.feature.industry.power.PowerService
import com.pixlehavencore.feature.industry.power.PowerSettings
```

在 `onEnable()` 中，`PlayerInfoService.init()` 之后添加：
```kotlin
        PowerService.init()
```

在 `logModulesStatus()` 的 summary map 中对应的位置添加：
```kotlin
            "Industry-Power" to PowerSettings.enabled,
```

在 `onDisable()` 中，适当位置添加：
```kotlin
        PowerService.stop()
```

- [ ] **步骤 2：在 mainCommand.kt 中注册 ReloadStep**

在导入区域添加：
```kotlin
import com.pixlehavencore.feature.industry.power.PowerService
```

在 `reloadAllModules()` 的 steps 列表中，适当位置添加：
```kotlin
        ReloadStep("industry-power", false) { PowerService.reload() },
```

- [ ] **步骤 3：Build 验证**

```bash
./gradlew build
```

预期：BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/PixleHavenCore.kt project/core/src/main/kotlin/com/pixlehavencore/mainCommand.kt
git commit -m "feat(industry): 集成电力模块到插件生命周期和重载系统"
```

---

### 任务 9：全量构建验证

- [ ] **步骤 1：清理并完整构建**

```bash
./gradlew clean build
```

预期：BUILD SUCCESSFUL

- [ ] **步骤 2：检查 git status**

```bash
git status
```

预期：工作区干净，仅新增和修改了计划中的文件

- [ ] **步骤 3：验证提交历史**

```bash
git log --oneline -10
```

预期：看到 8 个新 commit
