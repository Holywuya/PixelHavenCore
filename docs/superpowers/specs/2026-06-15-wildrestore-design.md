# 荒野恢复模块设计

> 日期：2026-06-15
> 状态：待实现

## 概述

为 PixelHavenCore 增加荒野恢复模块。荒野定义为 Dominion 领地之外的所有区块。当荒野区块在超时时间内没有玩家方块变更交互时，通过 FAWE/WorldEdit 将区块恢复到原始生成状态。

路径：`project/core/src/main/kotlin/com/pixlehavencore/feature/wildrestore/`

---

## 一、架构与模块结构

```
feature/wildrestore/
├── WildRestoreSettings.kt    # 配置加载（全局 + 世界覆写）
├── WildRestoreTracker.kt     # 区块变更追踪 + 内存索引
├── WildRestoreScanner.kt     # 过期扫描定时任务
├── WildRestoreRegenerator.kt # 再生执行（FAWE/WorldEdit API）
├── WildRestoreCommand.kt     # 管理员命令
├── WildRestoreListener.kt    # 方块变更事件监听
└── WildRestoreDatabase.kt    # SQLite/MySQL 持久化层
```

**数据流**：

```
方块变更事件 → Dominion荒野检查 → 标记区块 → 持久化DB
定时扫描 → 查询过期区块 → 再次确认荒野 → FAWE/WE再生 → 清理DB
```

---

## 二、配置文件

### 全局配置 (`wildrestore.yml`)

资源路径：`project/core/src/main/resources/feature/wildrestore.yml`

```yaml
defaults:
  timeout: 86400            # 无交互超时（秒），默认 24 小时
  scanInterval: 300         # 过期扫描间隔（秒）
  regenerateInterval: 20    # 再生队列处理间隔（tick）
  maxPerBatch: 64           # 单批最大再生数量
  onlyLoaded: true          # 仅再生已加载区块
  protectRadius: 0          # 领地保护半径（额外区块数）
```

### 世界覆写 (`wildrestore-worlds.yml`)

资源路径：`project/core/src/main/resources/feature/wildrestore-worlds.yml`

```yaml
worlds:
  world:
    enabled: true
    timeout: 86400
  world_nether:
    enabled: false
  world_resource:
    enabled: true
    timeout: 3600
```

每个世界继承默认值，只覆写需要的项。数据库连接复用项目全局配置，不在本模块单独定义。

---

## 三、数据库

每个世界一张表，表名格式 `wildrestore_{world_name}`：

```sql
CREATE TABLE wildrestore_{world_name} (
  x    INT    NOT NULL,
  z    INT    NOT NULL,
  time BIGINT NOT NULL,        -- 最后方块变更 epoch millis
  PRIMARY KEY (x, z)
);
```

支持 SQLite（UPSERT: `ON CONFLICT`）和 MySQL（UPSERT: `ON DUPLICATE KEY UPDATE`）。

---

## 四、追踪逻辑

监听 `BlockPlaceEvent` / `BlockBreakEvent`（MONITOR 优先级）：

1. 计算区块坐标 `(x >> 4, z >> 4)`
2. 通过 Dominion API 检查区块是否在领地外
3. 领地外 → 异步 UPSERT 到 DB，更新当前时间戳
4. 领地内 → 跳过

---

## 五、过期扫描

定时任务（`scanInterval` 间隔执行）：

```sql
SELECT * FROM wildrestore_{world}
WHERE time + {timeout_ms} <= {now}
LIMIT {maxPerBatch}
```

结果收集到内存队列供再生处理器消费。

---

## 六、再生执行

定时任务（`regenerateInterval` tick 间隔执行）：

1. 从队列取一个区块坐标
2. `onlyLoaded=true` 且区块未加载 → 跳过
3. 添加 `PluginChunkTicket` 防止卸载
4. 再次 Dominion 检查（双重保险）
5. 区块内有玩家 → 跳过
6. 再生：FAWE API → WorldEdit API → 跳过（都不可用时日志警告）
7. 从 DB 删除记录
8. 移除 `PluginChunkTicket`
9. 未达 `maxPerBatch` 上限 → 继续下一个

**FAWE/WorldEdit 降级**：启动时检测 FAWE → 有则优先使用 → 无则尝试 WorldEdit → 都没有则禁用再生功能并警告。

---

## 七、管理员命令

统一使用 `phcore.admin` 权限。根命令 `/wildrestore`（别名 `/wr`）：

| 子命令 | 功能 |
|--------|------|
| `info` | 全局状态：追踪总数、队列长度、启用世界 |
| `info <世界>` | 世界状态：追踪数、超时、上次扫描 |
| `regen` | 强制再生当前区块（需在荒野） |
| `regen <世界> <x> <z>` | 强制再生指定区块 |
| `clear <世界>` | 清除指定世界所有追踪数据 |
| `reload` | 重载配置 |

---

## 八、依赖

| 依赖 | 类型 | 用途 |
|------|------|------|
| Dominion | 必需软依赖 | 领地检查 |
| FastAsyncWorldEdit | 首选软依赖 | 高性能异步再生 |
| WorldEdit | 备选软依赖 | FAWE 不可用时的再生 |
| TabooLib | 框架依赖 | 生命周期、配置、命令 |
