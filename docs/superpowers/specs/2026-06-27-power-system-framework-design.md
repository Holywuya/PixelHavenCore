# 工业模块 — 电力系统框架设计

日期：2026-06-27

## 概述

新增 `feature/industry` 工业模块，首个子模块为**电力系统框架**。基于 Dominion 领地插件，玩家在领地内放置 CraftEngine 发电机方块即可为领地增加能量产出。框架定义了发电机注册、能量池管理、持久化存储的核心架构，为后续机器消费模块提供基础。

## 架构

### 模块结构

```
feature/industry/
├── power/
│   ├── PowerSettings.kt      # @Config 绑定 YAML 配置
│   ├── PowerService.kt       # 核心服务（生命周期、调度器、事件监听）
│   ├── PowerCommand.kt       # 管理命令
│   ├── PowerStorage.kt       # SQLite 持久化
│   ├── EnergyPool.kt         # 领地能量池模型 + GeneratorType 接口
│   ├── GeneratorRegistry.kt  # 发电机注册表（YAML → 运行时映射）
│   └── DominionBridge.kt     # Dominion API 桥接层（可选依赖）
└── resources/feature/industry/power.yml
```

### 组件职责

| 组件 | 职责 |
|------|------|
| `EnergyPool` | 领地能量池数据类：领地 ID、当前能量、容量上限、关联发电机列表 |
| `GeneratorType` | 发电机类型接口，含 `PassiveGenerator`（被动）、`FuelGenerator`（燃料）两种实现 |
| `GeneratorRegistry` | YAML 配置中 CraftEngine 方块 ID 到 GeneratorType 实例的映射 |
| `DominionBridge` | 封装 Dominion API，通过坐标查询领地 ID；Dominion 未安装时降级运行 |
| `PowerStorage` | SQLite 建表 + 领地能量池与发电机记录的读写操作 |
| `PowerService` | 生命周期入口、定时调度器、方块放置/破坏事件处理 |
| `PowerSettings` | `@Config` 绑定 power.yml 配置 |

### 运行流程

```
服务器启动
  → PowerService.init()
  → PowerStorage 建表 + loadAllPools() 恢复能量池到内存
  → 加载每个领地的发电机记录重建 EnergyPool
  → 启动 1 秒周期调度器

运行时（放置发电机）
  → 方块放置事件 → 查 CraftEngine 方块 ID
  → 查 GeneratorRegistry 是否为注册发电机
  → 查 DominionBridge 获取领地 ID
  → 注册到领地 EnergyPool → 更新容量上限
  → PowerStorage 持久化发电机记录

运行时（破坏发电机）
  → 方块破坏事件 → 查是否为已注册发电机
  → 从 EnergyPool 移除 → 缩减容量
  → PowerStorage 删除发电机记录
  → 如能量超出新容量 → 截断

运行时（调度器每 tick）
  → 遍历所有领地 EnergyPool
  → 遍历每个领地的发电机列表
  → 调用 generator.tick(pool) 产出能量
  → 累加能量，容量上限截断
  → 每 60 秒批量写入 SQLite
```

## 数据模型

### EnergyPool

```kotlin
data class EnergyPool(
    val dominionId: String,
    var energy: Double,
    var capacity: Double,
    var lastTickTime: Long
)
```

- 每个领地全局只有一个 EnergyPool
- capacity 由该领地所有发电机的 capacityContribution 之和动态计算
- energy 范围：0 ≤ energy ≤ capacity

### GeneratorType 接口

```kotlin
interface GeneratorType {
    val id: String
    val displayName: String
    val generatePerSecond: Double
    val capacityContribution: Double
    fun tick(pool: EnergyPool): Double
}
```

### PassiveGenerator

- tick() 直接返回 generatePerSecond，无外部条件依赖

### FuelGenerator

- 关联燃料槽，读取 CraftEngine 方块 NBT 中的燃料数据
- tick() 消耗燃料，有燃料时产出 generatePerSecond，无燃料时产出 0

## YAML 配置

```yaml
# resources/feature/industry/power.yml

enabled: true
maxEnergyPerDominion: 100000.0    # 单个领地能量上限（无发电机时的基础值）

generators:
  solar_panel_t1:
    type: passive
    craftengineId: "phcore:solar_panel_t1"
    displayName: "&e太阳能板 I"
    generatePerSecond: 10.0
    capacityContribution: 1000.0
  diesel_generator:
    type: fuel
    craftengineId: "phcore:diesel_generator"
    displayName: "&7柴油发电机"
    generatePerSecond: 50.0
    capacityContribution: 5000.0
```

- craftengineId 唯一标识 CraftEngine 方块，用于放置时匹配
- type 决定实例化 PassiveGenerator 还是 FuelGenerator

## 数据持久化

### SQLite 表结构

```sql
CREATE TABLE IF NOT EXISTS industry_power_pool (
    dominion_id    TEXT PRIMARY KEY,
    energy         REAL NOT NULL DEFAULT 0,
    capacity       REAL NOT NULL DEFAULT 0,
    updated_at     INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS industry_power_generator (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    dominion_id    TEXT NOT NULL,
    generator_type TEXT NOT NULL,
    world          TEXT NOT NULL,
    x              INTEGER NOT NULL,
    y              INTEGER NOT NULL,
    z              INTEGER NOT NULL,
    UNIQUE(world, x, y, z)
);
```

- 使用项目已有 DatabaseUtils + HikariCP
- 所有 SQL 操作异步执行（submit async）
- 写入优化：调度器每 60 秒批量写池，发电机增删即时写

## 命令

| 命令 | 功能 | 权限 |
|------|------|------|
| `/industry power info [领地]` | 查看领地能量信息 | phcore.admin |
| `/industry power reload` | 重载配置 | phcore.admin |

- 命令头：`@CommandHeader(name = "industry", permissionDefault = PermissionDefault.TRUE)`
- 管理员权限统一使用 `phcore.admin`

## 线程安全（Folia 合规）

- 方块事件在所在区域线程处理
- 调度器使用全局调度器（submit async），操作内存数据后回写
- SQLite 写入始终在全局异步线程
- EnergyPool 内存操作用 ConcurrentHashMap 保护

## 边界情况

| 场景 | 处理 |
|------|------|
| Dominion 未安装 | DominionBridge.isAvailable() = false，降级运行（发电机不关联领地） |
| 在非领地位置放置发电机 | 拒绝放置，提示"必须在领地内放置" |
| 发电机坐标上已存在另一个发电机 | UNIQUE 约束拒绝，提示"此处已有发电机" |
| 领地能量池从未存在 | 首次访问时创建 EnergyPool(energy=0, capacity=generator.capacityContribution) |
| 服务器重启 | 通过 PowerStorage 恢复所有能量池和发电机关联 |

## 文件变更

| 操作 | 文件 |
|------|------|
| 新增 | `feature/industry/power/EnergyPool.kt` |
| 新增 | `feature/industry/power/GeneratorRegistry.kt` |
| 新增 | `feature/industry/power/DominionBridge.kt` |
| 新增 | `feature/industry/power/PowerStorage.kt` |
| 新增 | `feature/industry/power/PowerSettings.kt` |
| 新增 | `feature/industry/power/PowerService.kt` |
| 新增 | `feature/industry/power/PowerCommand.kt` |
| 新增 | `resources/feature/industry/power.yml` |
| 修改 | `PixleHavenCore.kt` — 注册生命周期 |
| 修改 | `mainCommand.kt` — 注册 ReloadStep |
