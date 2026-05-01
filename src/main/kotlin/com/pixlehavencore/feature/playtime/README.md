# 在线时长统计模块 (Playtime)

## 功能概述

自动记录玩家在线时长，支持多维度统计（总计/今日/本周/本月）和 PlaceholderAPI 变量输出。

## 模块结构

| 文件 | 职责 |
|------|------|
| `PlaytimeSettings.kt` | 配置管理、热重载、时长格式化 |
| `PlaytimeStorage.kt` | 数据模型、PlayerDatabase容器、缓存、持久化 |
| `PlaytimeService.kt` | 核心业务逻辑、统计重置调度 |
| `PlaytimeListener.kt` | 登录/登出事件监听 |
| `PlaytimeCommand.kt` | 命令处理器 |
| `PlaytimePlaceholders.kt` | PAPI 变量扩展 |

## 配置文件

`feature/playtime.yml`

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `enabled` | true | 模块开关 |
| `autoSaveTicks` | 200 | 自动保存间隔(tick) |
| `papi.enabled` | true | PAPI 变量开关 |
| `papi.defaultFormat` | readable | 默认输出格式 |
| `leaderboard.maxLimit` | 100 | 排行榜最大条数 |
| `leaderboard.defaultLimit` | 10 | 排行榜默认条数 |
| `cleanup.defaultDays` | 90 | 清理默认天数 |
| `cleanup.batchSize` | 50 | 批量删除大小 |
| `resetSchedule.daily` | "00:00" | 每日重置时间 |
| `resetSchedule.weeklyDay` | 1 | 每周重置日(1-7) |
| `resetSchedule.monthlyDay` | 1 | 每月重置日(1-28) |

## 命令

| 命令 | 权限 | 说明 |
|------|------|------|
| `/playtime` | phcore.playtime | 查看自己的在线时长 |
| `/playtime <玩家>` | phcore.playtime.other | 查看他人在线时长 |
| `/playtime top [类型] [数量]` | phcore.playtime.top | 查看排行榜 |
| `/playtime cleanup [天数]` | phcore.playtime.cleanup | 清理旧数据 |
| `/playtime reload` | phcore.playtime.reload | 重载配置 |

类型选项: `total`(默认) / `today` / `week` / `month`

## PAPI 变量

变量前缀: `%phcorept_<变量>%`

| 变量 | 说明 |
|------|------|
| `%phcorept_total%` | 总在线时长（可读格式） |
| `%phcorept_total_seconds%` | 总在线时长（秒） |
| `%phcorept_total_minutes%` | 总在线时长（分钟） |
| `%phcorept_total_hours%` | 总在线时长（小时） |
| `%phcorept_today%` | 今日在线时长（可读格式） |
| `%phcorept_today_seconds%` | 今日在线时长（秒） |
| `%phcorept_today_minutes%` | 今日在线时长（分钟） |
| `%phcorept_today_hours%` | 今日在线时长（小时） |
| `%phcorept_week%` | 本周在线时长（可读格式） |
| `%phcorept_week_seconds%` | 本周在线时长（秒） |
| `%phcorept_week_minutes%` | 本周在线时长（分钟） |
| `%phcorept_week_hours%` | 本周在线时长（小时） |
| `%phcorept_month%` | 本月在线时长（可读格式） |
| `%phcorept_month_seconds%` | 本月在线时长（秒） |
| `%phcorept_month_minutes%` | 本月在线时长（分钟） |
| `%phcorept_month_hours%` | 本月在线时长（小时） |
| `%phcorept_session%` | 当前会话时长（可读格式） |
| `%phcorept_session_seconds%` | 当前会话时长（秒） |
| `%phcorept_enabled%` | 模块启用状态 |

## 线程模型

- **事件监听**: 同步（PlayerJoinEvent/PlayerQuitEvent）
- **数据预加载**: 异步（`submitAsync`）
- **数据写入**: 异步定时刷新（脏数据机制）
- **排行榜查询**: 异步排序 + 主线程回调
- **PAPI 变量解析**: 同步（仅读内存缓存，< 10ms）
- **统计重置**: 异步执行

## 数据存储

- 使用 TabooLib `MultipleHandler`（PlayerDatabase 容器）
- 表名: `playtime_data`
- 内存缓存: `ConcurrentHashMap<UUID, PlaytimeData>`
- 细粒度锁: `ConcurrentHashMap<UUID, Any>` 保护并发访问
