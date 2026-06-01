# RealWorld 真实世界系统

真实世界环境系统，模拟体温、口渴、骨折、季节、天气与食物腐蚀等生存机制。

## 系统架构

采用 **Tick 驱动架构**，所有子系统通过统一的 Tick 循环定期更新（默认 2 秒）：

```
RealWorldService (主服务)
    ↓
┌────────────────────────────────────────┐
│  GlobalSubsystemTickers (全局子系统)    │
│  - SeasonTicker (季节推进)              │
└────────────────────────────────────────┘
    ↓
┌────────────────────────────────────────┐
│  PlayerSubsystemTickers (玩家子系统)    │
│  - TemperatureTicker (温度计算)         │
│  - ThirstTicker (水分消耗)              │
│  - FractureTicker (骨折累积)            │
│  - SurvivalEffectTicker (生存效果)      │
│  - FoodCorrosionTicker (食物腐蚀)       │
└────────────────────────────────────────┘
    ↓
SurvivalHud (HUD 渲染 + Per-Player 天气同步)
```

## 功能概述

### 体温系统

动态体温模拟，受多种环境因素影响，采用**绝缘乘数 + 主动回拉**模型。

**计算流程（8 阶段）：**

1. **基础环境温度** = 群系温度 + 季节修正 + 昼夜修正 + 天气修正 + 海拔修正
2. **热源叠加**：区块噪声驱动 + 方块辐射修正
3. **水/空气体感**：水中使用水温，空气叠加湿度蒸发冷却
4. **水/空气平滑过渡**：出水后根据潮湿度混合水温与空气温度
5. **温度差** = 有效环境温度 - 当前体温（水中乘以导热系数）
6. **绝缘**：护甲 + 遮蔽减缓环境温差穿透（冷天保暖、热天隔热）
7. **主动回拉**：体温自动向舒适区中值靠拢，受饱食度和骨折影响
8. **动态限速**：上限 = 基础值 + |温差| × 动态系数

**关键特性：**
- **护甲绝缘**：皮革 8%/件，锁链 4%/件，铁 6%/件，金 5%/件，钻石 10%/件，下界合金 12%/件，上限 70%
- **遮蔽绝缘**：树冠 15%，建筑 25%
- **热源辐射**：营火、岩浆等方块提供局部加热/冷却（衰减叠加模型）
- **主动调节**：回拉强度 0.05，受饱食度 (0.3~1.0) 和骨折惩罚影响
- **视觉效果**：霜冻遮罩（freezeTicks）、高温红屏（WorldBorder）

### 口渴系统

水分消耗受温度和活动影响。

- 基础消耗率 × 温度倍率 × 季节倍率
- 饮水回复：自然水源、饮水器
- 阶段：充足 → 口渴 → 严重口渴 → 脱水

### 骨折系统

骨骼损伤累积，影响行动能力。

- 摔落伤害触发骨折累积
- 阶段：无 → 轻微 → 中度 → 严重
- 影响移动速度和挖掘速度
- 满饱食度时自然恢复，可使用绷带/石膏治疗

### 季节系统

四季循环，影响全局环境参数。

| 季节 | 温度修正 | 降水倍率 |
|------|---------|---------|
| 春 | +3°C | 2.5 |
| 夏 | +10°C | 1.0 |
| 秋 | +0°C | 0.8 |
| 冬 | -20°C | 4.0 |

### 天气系统

基于区块的局部天气系统，不同区域天气独立。

- **噪声驱动**：每个区块独立计算降雨概率
- **Per-Player 同步**：使用 `player.setPlayerWeather()` 同步客户端视觉效果
- **一致性保证**：视觉下雨、HUD 显示、潮湿度计算使用同一数据源
- 天气类型：晴天 (CLEAR)、雨天 (RAIN, -3°C)
- 支持强制天气与自动恢复

### 食物腐蚀

背包中食物随时间腐烂，可配置保质期。

- 食物放入个人仓库或共享仓库后，过期速度减慢（默认 20 倍）
- 可配置减速倍率 `storage-slowdown-factor`

### 生存效果

根据玩家状态应用药水效果：

| 状态 | 效果 |
|------|------|
| 严重过热 (≥42°C) | 凋零 I + 失明 I |
| 过热 (≥36°C) | 缓慢 I + 饥饿 II |
| 轻微寒冷 (5~15°C) | 饥饿 I |
| 寒冷 (-5~5°C) | 缓慢 I + 挖掘疲劳 I |
| 严重寒冷 (≤-5°C) | 缓慢 II + 凋零 I |
| 脱水 | 凋零 I（允许伤害时） |

## 指令

主命令：`/realworld`（别名：`/rw`），管理子命令均需 `phcore.admin` 权限。

| 命令 | 说明 |
|------|------|
| `/rw status` | 查看当前季节、天气与在线玩家平均状态 |
| `/rw player <玩家名>` | 查看玩家环境状态 |
| `/rw season <季节>` | 强制切换季节（SPRING/SUMMER/AUTUMN/WINTER） |
| `/rw weather <天气>` | 强制切换天气（RAIN/CLEAR/AUTO） |
| `/rw reset <玩家名>` | 重置玩家生存数据 |
| `/rw reload` | 重载模块 |
| `/rw corrosion status` | 查看食物腐蚀状态 |

## PAPI 变量

标识符：`phcorerw`

### 全局变量

| 变量 | 说明 | 示例 |
|------|------|------|
| `%phcorerw_season%` | 当前季节 | 春 |
| `%phcorerw_season_progress%` | 季节进度 | 45.2% |
| `%phcorerw_day_phase%` | 时间段 | 白天/黄昏/夜晚 |
| `%phcorerw_weather%` | 玩家位置天气 | 晴/雨 |

### 玩家变量

| 变量 | 说明 | 示例 |
|------|------|------|
| `%phcorerw_temperature%` | 体温（整数） | 25 |
| `%phcorerw_temperature_exact%` | 体温（一位小数） | 25.3 |
| `%phcorerw_temperature_phase%` | 体温阶段 | 舒适/过热/寒冷... |
| `%phcorerw_biome_temperature%` | 群系基础温度 | 15.0 |
| `%phcorerw_near_heat_source%` | 最近热源 | campfire/none |
| `%phcorerw_hydration%` | 水分（整数） | 80 |
| `%phcorerw_hydration_phase%` | 水分阶段 | 充足/口渴/脱水 |
| `%phcorerw_wetness%` | 潮湿度 | 35% |
| `%phcorerw_shelter%` | 遮蔽类型 | 无遮蔽/树荫/建筑 |
| `%phcorerw_is_sheltered%` | 是否被遮蔽 | true/false |
| `%phcorerw_fracture%` | 骨折值 | 30 |
| `%phcorerw_fracture_severity%` | 骨折严重程度 | 无骨折/轻微/中度/严重 |

### 布尔判断

| 变量 | 说明 |
|------|------|
| `%phcorerw_is_raining%` | 玩家位置是否下雨 |
| `%phcorerw_is_comfortable%` | 体温是否舒适 |
| `%phcorerw_is_thirsty%` | 是否口渴 |
| `%phcorerw_is_injured%` | 是否骨折 |

## 配置文件

路径：`feature/realworld/`

| 配置文件 | 说明 |
|----------|------|
| `realworld.yml` | 模块总开关、Tick 间隔、HUD 配置、生存效果 |
| `temperature.yml` | 体温阈值、绝缘、热源辐射、调节、水/空气过渡 |
| `thirst.yml` | 口渴消耗/恢复速率、阶段阈值 |
| `fracture.yml` | 骨折触发、治疗物品、阶段影响 |
| `season.yml` | 季节持续天数、时间控制、温度修正 |
| `weather.yml` | 天气生成、噪声参数、极端天气 |
| `food-corrosion.yml` | 食物保质期 |

## 代码结构

### 核心服务

| 文件 | 职责 |
|------|------|
| `RealWorldService.kt` | 生命周期管理、Tick 调度、时间控制 |
| `RealWorldSettings.kt` | 全局配置读取与热重载 |
| `RealWorldStorage.kt` | 数据持久化（玩家状态、全局状态） |
| `RealWorldModels.kt` | 数据模型（PlayerEnvState、GlobalEnvState、枚举） |
| `RealWorldEvents.kt` | 事件监听（食物、攻击、挖掘） |
| `RealWorldCommand.kt` | 管理员命令 |
| `RealWorldPlaceholders.kt` | PlaceholderAPI 变量扩展 |
| `SurvivalEffectApplier.kt` | 药水效果与速度调整 |
| `SurvivalHud.kt` | ActionBar + BossBar 渲染、Per-Player 天气同步 |

### 子系统

| 目录 | 说明 |
|------|------|
| `temperature/` | 体温引擎、护甲绝缘、热源扫描、遮蔽检测、霜冻/高温效果 |
| `thirst/` | 口渴引擎、饮水交互 |
| `fracture/` | 骨折引擎、治疗逻辑 |
| `season/` | 季节引擎、季节切换、温度修正 |
| `weather/` | 区块天气引擎、噪声生成、天气缓存、天气查询 |
| `foodcorrosion/` | 食物腐蚀服务、Packet 监听 |
| `tick/` | Tick 调度器接口（全局/玩家） |

## 性能优化

- **遮蔽缓存**：5 秒内相同位置不重复检测
- **热源缓存**：5 秒内相同位置不重复扫描（球体裁剪，~524 方块）
- **天气缓存**：区块级天气缓存，10 秒 TTL
- **HUD 刷新控制**：可配置刷新间隔
- **异步存储**：数据库操作在异步线程执行

## 依赖

- TabooLib 6.3.0
- Paper 1.21.11 API
- PlaceholderAPI（可选）
- PacketEvents（可选，食物腐蚀 Packet 监听）
