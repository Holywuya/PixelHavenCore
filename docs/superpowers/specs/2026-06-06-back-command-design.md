# /back 命令设计文档

**日期**: 2026-06-06  
**状态**: 已确认  
**范围**: 基础指令模块新增 `/back` 命令

---

## 1. 目标

在 `feature/base/` 模块中新增 `/back` 命令，允许玩家传送回上一次死亡位置或上一次传送来源位置。

## 2. 行为概述

- 死亡和传送**共写一个槽位**，后者覆盖前者
- 监听 `PlayerTeleportEvent`：取 `event.from` 作为记录位置
- 监听 `PlayerDeathEvent`：取 `event.player.location` 作为记录位置
- 使用 `/back` 命令传送到记录的位置
- 传送前经过**冷却检查** → **预热倒计时** → **安全位置检测** → 传送

## 3. 命令定义

| 命令 | 权限 | 说明 |
|---|---|---|
| `/back` | 默认所有玩家可用 (`PermissionDefault.TRUE`) | 传送到上一个记录位置 |

无子命令、无参数。

## 4. 架构

### 4.1 文件结构

```
feature/base/
├── BackCommand.kt          # 新增：@CommandHeader 命令定义
├── BackService.kt          # 新增：核心逻辑（记录、传送、冷却/预热/安全检测）
├── BackStorage.kt          # 新增：持久化（MultipleHandler + ConcurrentHashMap 缓存）
├── BaseListener.kt         # 修改：新增 DeathEvent + TeleportEvent 监听
└── base-command.yml        # 修改：新增 back 配置节
```

### 4.2 组件职责

| 组件 | 职责 |
|---|---|
| `BackCommand` | 命令入口，校验冷却、持有记录，调用 Service |
| `BackService` | 预热调度、安全位置计算、传送执行、冷却管理 |
| `BackStorage` | Location 序列化/反序列化，缓存 + 异步持久化 |
| `BaseListener` | 事件监听，调用 BackService.record() |

### 4.3 依赖关系

```
BackCommand → BackService → BackStorage
                  ↑
BaseListener ─────┘
```

## 5. 数据模型

### 5.1 BackData

```kotlin
data class BackData(
    val location: Location,     // 记录的位置
    val reason: String,         // "death" 或 "teleport"
    val timestamp: Long         // 记录时间（System.currentTimeMillis()）
)
```

### 5.2 持久化

- **表名**: `back_location`
- **Handler**: `MultipleHandler`（通过 `DatabaseUtils.newPlayerDataHandler` 创建）
- **键格式**: `location` → `"世界名:x:y:z:yaw:pitch"`
- **缓存**: `ConcurrentHashMap<UUID, BackData>`，即时读写，异步写库
- **生命周期**: 玩家数据不删除（保留永久），缓存随 PlayerSessionMap 离线清理

## 6. 传送流程

```
/back 命令
  │
  ├─ 1. 无记录 → msgNoLocation，结束
  ├─ 2. 冷却中 → msgCooldown（显示剩余秒数），结束
  │
  └─ 3. 通过 → 启动预热任务（玩家实体线程）
       │
       ├─ 每秒 tick：
       │   ├─ 玩家不在线 → 取消
       │   ├─ cancelOnMove 且玩家移动 → msgWarmupCancelled
       │   └─ cancelOnDamage 且玩家受伤 → msgWarmupCancelled
       │
       ├─ 倒计时中 → actionbar 显示 msgWarmupStarting（{time}=剩余秒）
       │
       └─ 读秒完成
           ├─ unsafeTeleport=false → 寻找方块上方安全位置
           ├─ unsafeTeleport=true → 直接使用原始坐标
           ├─ teleport 成功 → 记录冷却 → msgTeleported
           └─ teleport 失败 → 提示错误
```

## 7. 配置

```yaml
# base-command.yml 新增节
back:
  enabled: true
  cooldownSeconds: 30
  warmupSeconds: 3
  cancelOnMove: true
  cancelOnDamage: true
  unsafeTeleport: false
  msgNoLocation: "&c没有可返回的位置。"
  msgCooldown: "&c请等待 {time} 秒后再使用。"
  msgWarmupStarting: "&a将在 {time} 秒后传送... 请勿移动"
  msgWarmupCancelled: "&c传送已取消！"
  msgTeleported: "&a已传送到上一个位置。"
```

## 8. 线程安全

遵循 `folia-thread-safety` 规范：

- **冷却检查/记录**: 在命令线程（任意线程）读取 `ConcurrentHashMap`，线程安全
- **预热调度**: 使用 `player.submitOnEntity` 在实体线程执行
- **移动检测**: 在预热 tick 中比较当前位置与起始位置（实体线程内）
- **受伤检测**: 通过 `EntityDamageEvent` 监听，在实体线程标记取消
- **数据库写入**: 通过 `submitAsync` 异步执行，不阻塞主线程

## 9. 错误处理

| 场景 | 处理 |
|---|---|
| 记录的世界已不存在 | 清除记录，提示"目标世界不可用" |
| 记录的世界已卸载 | 提示"目标世界不可用" |
| 安全位置找不到（搜索超限） | 提示"未找到安全传送位置" |
| 数据库读写失败 | 警告日志，不影响游戏内功能（缓存可用） |
| 冷却期间重连 | 冷却基于时间戳记录，支持跨会话 |

## 10. 边界情况

- 新玩家首次使用 `/back`：无记录，提示 msgNoLocation
- 跨世界 `/back`：正常传送（不限制世界）
- 传送后立即 `/back`（冷却过期后）：回到传送前位置
- 数据库不可用时：缓存仍工作，功能不受影响（重启后丢失）

## 11. 不实现的功能

- 不实现 `/back death` 子命令（双槽模式）
- 不限制跨世界使用
- 不集成 Vault 经济收费
- 不提供 PlaceholderAPI 变量
