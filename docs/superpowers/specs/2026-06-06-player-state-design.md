# 统一玩家状态库 设计文档

**日期**: 2026-06-06
**状态**: 已确认
**范围**: 新建 `feature/playerstate/` 模块，提供统一的玩家元信息存储与查询

---

## 1. 目标

新建 `feature/playerstate/` 模块，用一张统一的 `player_meta` 表收拢分散在各个模块中的"玩家基础状态"——首次登录判定、登录/离线时间、传送/死亡位置——让其他模块通过统一的 API 读写，减少重复查询和表耦合。

## 2. 行为概述

- 通过 `PlayerStateListener` 自动监听 `PlayerJoinEvent`、`PlayerQuitEvent`、`PlayerDeathEvent`、`PlayerTeleportEvent`，写入对应字段
- 提供 `PlayerStateService` 对外 API，供 `BackService`、`FirstJoinService` 等模块迁移调用
- 一张 `player_meta` 表存所有字段，UUID 为 key，每行一个玩家
- 现阶段与旧表共存，后续迭代逐步迁移

## 3. 模块结构

```
feature/playerstate/
├── PlayerStateStorage.kt     # 持久化（MultipleHandler + ConcurrentHashMap 缓存）
├── PlayerStateService.kt     # 对外 API
├── PlayerStateSettings.kt    # @Config 配置
├── PlayerStateListener.kt    # 自动事件处理
└── player-state.yml          # 配置
```

## 4. 表结构

| 字段 | 类型 | 说明 |
|---|---|---|
| `player_name` | String | 最新玩家名快照 |
| `first_join_time` | Long | 首登时间戳 |
| `last_join_time` | Long | 最近登录时间戳 |
| `last_quit_time` | Long | 最近离线时间戳 |
| `join_count` | Int | 累计登录次数 |
| `last_death_location` | String | "世界名:x:y:z:yaw:pitch" |
| `last_teleport_location` | String | "世界名:x:y:z:yaw:pitch" |

表名：`player_meta`，以 UUID 字符串为 key。

## 5. 对外 API

### PlayerStateService

```kotlin
// 登录态
fun getFirstJoinTime(uuid: UUID): Long?
fun getLastJoinTime(uuid: UUID): Long?
fun getJoinCount(uuid: UUID): Int
fun isFirstJoin(uuid: UUID): Boolean
fun getLastQuitTime(uuid: UUID): Long?
fun getPlayerName(uuid: UUID): String?

// 位置
fun getLastDeathLocation(uuid: UUID): Location?
fun setLastDeathLocation(uuid: UUID, loc: Location)
fun getLastTeleportLocation(uuid: UUID): Location?
fun setLastTeleportLocation(uuid: UUID, loc: Location)

// 管理
fun reset(uuid: UUID)
```

### PlayerStateListener（自动处理）

| 事件 | 操作 |
|---|---|
| `PlayerJoinEvent` | 更新 `player_name`、`last_join_time`、`join_count`(+1)；若首次则写 `first_join_time` |
| `PlayerQuitEvent` | 更新 `last_quit_time` |
| `PlayerDeathEvent`(MONITOR) | 写 `last_death_location` |
| `PlayerTeleportEvent`(MONITOR, ignoreCancelled) | 写 `last_teleport_location`（event.from） |

## 6. 配置

```yaml
# player-state.yml
enabled: true
```

## 7. 线程安全

- 缓存：`ConcurrentHashMap<UUID, PlayerStateData>`
- 持久化：`MultipleHandler` + `submitAsync` 异步写库
- 事件线程：Folia 安全，监听器在实体线程执行读缓写缓，异步刷库

## 8. 错误处理

| 场景 | 处理 |
|---|---|
| 数据库不可用 | warning 日志，缓存正常工作，功能不中断 |
| 反序列化失败 | 返回 null/default，记录 warning |

## 9. 边界情况

- 首次登录时 `first_join_time` 不存在 → PlayerStateListener 自动写入当前时间 + join_count = 1
- 同一个 UUID 多次写入 → 缓存即时更新，异步刷库防丢失

## 10. 不实现的功能

- 不直接修改 BackService / FirstJoinService（后续迭代迁移）
- 不在本模块提供 PlaceholderAPI 变量
- 不限制哪些世界记录位置
