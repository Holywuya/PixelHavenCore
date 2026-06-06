# 首次登录随机传送 设计文档

**日期**: 2026-06-06  
**状态**: 已确认  
**范围**: 基础指令模块新增首次登录随机传送功能

---

## 1. 目标

在 `feature/base/` 模块中新增首次登录随机传送功能：玩家第一次进入服务器时，自动传送到以世界出生点为中心、指定半径范围内的随机安全位置。

## 2. 行为概述

- 监听 `PlayerJoinEvent`，检测是否是首次登录
- 首次判定：复用称号系统的 `title_player_data` 表，无数据 = 新玩家
- 若称号模块未启用，回退到 `player.hasPlayedBefore()` 判定
- 随机生成 X/Z 坐标（圆环区域），查找安全表面位置，传送玩家
- 非新玩家跳过，不做任何处理
- 传送前不记录 `/back` 位置（首次传送无"上一个位置"）

## 3. 架构

### 3.1 文件结构

```
feature/base/
├── FirstJoinService.kt     # 新增：首次检测 + 随机传送
├── FirstJoinSettings.kt    # 新增：配置读取
├── BaseListener.kt         # 修改：新增 PlayerJoinEvent 监听
└── base-command.yml        # 修改：新增 first-join 配置节
```

### 3.2 组件职责

| 组件 | 职责 |
|---|---|
| `FirstJoinService` | 判断是否首次，计算随机坐标，查找安全位置，执行传送 |
| `FirstJoinSettings` | 读取配置项 |
| `BaseListener` | PlayerJoinEvent 监听入口，调用 Service |

### 3.3 依赖关系

```
BaseListener → FirstJoinService → FirstJoinSettings
                    ↓
            TitleStorage (查询 title_player_data 表)
```

## 4. 首次判定逻辑

```
PlayerJoinEvent
  → 尝试查 title_player_data 表 (KEY_ACTIVE / KEY_OWNED)
  → 表可访问且都有数据 → 非新玩家，跳过
  → 表可访问但无数据 → 新玩家，执行随机传送
  → 表不可访问（称号模块未启用）→ 回退到 hasPlayedBefore()
```

## 5. 随机传送算法

```
1. 在圆环区域 (minRadius ~ maxRadius) 随机生成角度和距离
   angle = random(0, 2π)
   distance = minRadius + random(0, maxRadius - minRadius)
   x = centerX + distance * cos(angle)
   z = centerZ + distance * sin(angle)

2. 取目标 X/Z 所在列的最高方块 Y，向下搜索 safeLocationRetries 次
   若找不到 → 回到步骤 1 重新随机，最多尝试 5 轮

3. 找到安全位置 → 传送
   5 轮后仍未找到 → 不传送，记录警告日志
```

## 6. 配置

```yaml
# base-command.yml 新增节
first-join:
  # 总开关
  enabled: false
  # 传送中心坐标偏移（相对于世界出生点）
  centerX: 0
  centerZ: 0
  # 最小半径（距中心至少多远）
  minRadius: 50
  # 最大半径（距中心至多多远）
  maxRadius: 500
  # 安全位置查找最大尝试次数
  safeLocationRetries: 10
  # 提示消息（{x}{y}{z} 为占位符）
  msgTeleported: "&a你被随机传送到 {x}, {y}, {z}"
```

## 7. 线程安全

- 首次判定中的数据库查询在命令线程（PlayerJoinEvent 线程）执行
- 随机坐标计算无世界访问，可在任意线程
- 安全位置检测和传送操作通过 `player.submitOnEntity` 在实体线程执行

## 8. 错误处理

| 场景 | 处理 |
|---|---|
| 称号模块未启用 | 回退到 `hasPlayedBefore()` |
| 安全位置多轮未找到 | 不传送，warning 日志 |
| 世界不存在 | 跳过，不传送 |

## 9. 边界情况

- 玩家首次登录但世界无合适位置：不传送
- minRadius > maxRadius：配置校验，reload 时 warning 并禁用
- 多个新玩家同时登录：无共享状态，天然线程安全

## 10. 不实现的功能

- 不提供命令接口（纯自动触发）
- 不支持多世界（仅主世界）
- 不支持 `/back` 记录首次传送前位置
