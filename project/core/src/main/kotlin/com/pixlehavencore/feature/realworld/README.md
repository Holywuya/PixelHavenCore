# RealWorld 模块

真实世界环境系统，模拟体温、口渴、骨折、季节、天气与食物腐蚀等生存机制。

## 功能概述

- **体温系统**
  - 根据环境、季节、天气、海拔等因素动态变化
  - 温度方块辐射加热/冷却（营火、岩浆、冰块等）
  - 饱食度抗寒机制（高饱食度增加抗寒能力）
  - 体温阶段：严重过热 → 过热 → 舒适 → 轻微寒冷 → 寒冷 → 严重寒冷
  - 霜冻/高温红屏视觉效果

- **口渴系统**
  - 玩家在不同环境下持续消耗水分
  - 饮水回复：自然水源、饮水器
  - 口渴阶段：充足 → 口渴 → 严重口渴 → 脱水

- **骨折系统**
  - 摔落伤害触发骨折
  - 骨折阶段：无 → 轻微 → 中度 → 严重
  - 影响移动速度，可使用绷带/石膏治疗

- **季节系统**
  - 春 → 夏 → 秋 → 冬 自动轮换
  - 季节影响环境温度、天气概率、水分流失速率
  - 平滑过渡，支持自定义持续天数

- **天气系统**
  - 噪声驱动天气（晴天、雨天）
  - 天气影响温度与潮湿度
  - 支持强制天气与自动恢复

- **食物腐蚀**
  - 背包中食物随时间腐烂
  - 可配置保质期

## 指令

主命令：`/realworld`（别名：`/rw`）

| 命令 | 说明 |
|------|------|
| `/rw status` | 查看当前季节、天气与在线玩家平均状态 |
| `/rw player <玩家名>` | 查看缓存中的玩家环境状态 |
| `/rw season <季节>` | 强制切换季节（SPRING/SUMMER/AUTUMN/WINTER） |
| `/rw weather <天气>` | 强制切换天气（RAIN/AUTO） |
| `/rw reset <玩家名>` | 重置玩家生存数据 |
| `/rw reload` | 重载真实世界模块 |
| `/rw corrosion status` | 查看食物腐蚀功能状态 |

管理子命令均需要 `phcore.admin` 权限。

## PlaceholderAPI 变量

标识符：`phcorerw`

| 变量 | 说明 |
|------|------|
| `%phcorerw_season%` | 当前季节名称 |
| `%phcorerw_weather%` | 当前天气名称 |
| `%phcorerw_season_progress%` | 季节进度百分比 |
| `%phcorerw_temperature%` | 玩家体温 |
| `%phcorerw_hydration%` | 玩家口渴值 |

## 配置文件

路径：`feature/realworld/`

| 配置文件 | 说明 |
|----------|------|
| `realworld.yml` | 模块总开关、HUD 显示配置 |
| `temperature.yml` | 体温阈值、温度方块、护甲加成、饱食度抗寒 |
| `thirst.yml` | 口渴消耗/恢复速率、阶段阈值 |
| `fracture.yml` | 骨折触发、治疗物品配置 |
| `season.yml` | 季节持续天数、时间控制、过渡进度 |
| `weather.yml` | 天气生成、极端天气配置 |
| `food-corrosion.yml` | 食物保质期配置 |

## 主要代码结构

### 核心服务

| 文件 | 职责 |
|------|------|
| `RealWorldService.kt` | 模块生命周期管理、Tick 调度 |
| `RealWorldSettings.kt` | 全局配置读取与热重载 |
| `RealWorldStorage.kt` | 数据持久化（玩家状态、全局状态） |
| `RealWorldModels.kt` | 数据模型定义（PlayerEnvState、GlobalEnvState） |
| `RealWorldEvents.kt` | 事件监听（食物、攻击、挖掘） |
| `RealWorldCommand.kt` | 命令入口 |
| `RealWorldPlaceholders.kt` | PlaceholderAPI 扩展 |
| `SurvivalEffectApplier.kt` | 药水效果与速度调整 |
| `SurvivalHud.kt` | ActionBar 与 BossBar 显示 |

### 子系统

| 目录 | 说明 |
|------|------|
| `temperature/` | 体温引擎、温度方块扫描、霜冻/高温效果 |
| `thirst/` | 口渴引擎、饮水器交互 |
| `fracture/` | 骨折引擎、治疗逻辑 |
| `season/` | 季节引擎、季节切换 |
| `weather/` | 天气引擎、噪声生成 |
| `foodcorrosion/` | 食物腐蚀服务 |
| `tick/` | Tick 调度器（全局/玩家） |
