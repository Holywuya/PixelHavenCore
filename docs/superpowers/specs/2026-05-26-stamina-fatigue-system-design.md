# RealWorld 体力疲劳系统设计规格

**日期**: 2026-05-26
**状态**: 待实现
**模块**: `feature/realworld`

---

## 1. 概述

为 RealWorld 生存模块新增体力疲劳系统，作为所有行动的底层资源，串联温度、骨折、口渴、潮湿度、食物、天气、季节七个现有系统，增加生存玩法深度。

### 设计目标

- 慢消耗节奏：奔跑 16 分钟耗尽，静止 5.5 分钟回满
- 深度联动现有系统，增加玩法层次
- 完整配置可调，管理员可自由调整所有数值
- 不侵入现有 HUD，仅低体力时 BossBar + 聊天提醒

---

## 2. 核心数据模型

### 体力值定义

- 类型: `Double`
- 范围: `0.0 ~ 100.0`
- 默认满值: `100.0`

### PlayerEnvState 新增字段

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `stamina` | Double | 100.0 | 当前体力值 |
| `staminaPhase` | StaminaPhase | FULL | 体力阶段 |
| `staminaIdleTimer` | Double | 0.0 | 静止计时器（秒） |
| `staminaRecoveryCooldown` | Double | 0.0 | 恢复冷却 |
| `staminaChatWarnCooldown` | Double | 0.0 | 聊天提醒冷却 |

### StaminaPhase 枚举

```kotlin
enum class StaminaPhase(
    val displayName: String,
    val speedMultiplier: Double,
    val canSprint: Boolean,
) {
    FULL("充沛", 1.0, true),           // > 60%
    TIRED("疲劳", 0.85, true),          // 30% ~ 60%
    EXHAUSTED("筋疲力尽", 0.70, false),  // 10% ~ 30%
    DEPLETED("体力耗尽", 0.50, false),   // < 10%
}
```

### 计算公式

**消耗公式（每 tick）：**
```
实际消耗 = 基础消耗速率 × 行为倍率 × 环境倍率 × 联动倍率
```

**恢复公式（每 tick）：**
```
实际恢复 = 基础恢复速率 × 恢复方式倍率 × 联动恢复倍率
```

---

## 3. 四层消耗机制

### 3.1 持续消耗层

玩家处于特定移动状态时，每 tick 持续扣除体力。

| 状态 | 检测方式 | 消耗倍率 | 纯消耗耗时 |
|------|----------|----------|------------|
| 奔跑 | `player.isSprinting` | ×2.0 | ~16 分钟 |
| 游泳 | `player.isSwimming` 或在水中且潜行 | ×2.5 | ~13 分钟 |
| 攀爬 | `player.isClimbing` | ×2.0 | ~16 分钟 |

### 3.2 动作消耗层

执行特定动作时，瞬间扣除体力（事件触发）。

| 动作 | 触发事件 | 单次消耗 |
|------|----------|----------|
| 攻击 | `EntityDamageByEntityEvent` | 0.15 |
| 挖掘 | `BlockBreakEvent` | 0.075 |
| 使用工具 | `PlayerInteractEvent` | 0.1 |

### 3.3 环境消耗层

环境因素作为倍率叠加到持续消耗上，不独立消耗。

| 环境条件 | 判定逻辑 | 消耗倍率 |
|----------|----------|----------|
| 极端温度 | `temperaturePhase == SEVERE_HEAT / SEVERE_COLD` | ×1.5 |
| 骨折状态 | `fracture > 20` | ×1.3 |
| 潮湿 > 70% | `wetness > 0.7` | ×1.2 |

### 3.4 特殊消耗层

特定场景下的高额消耗，独立于常规行为。

| 场景 | 检测方式 | 消耗倍率 |
|------|----------|----------|
| 水下憋气 | `player.remainingAir < player.maximumAir` 且无水下呼吸附魔 | ×4.0 |
| 高空作业 | `player.location.y > 120` 且未着地 | ×2.0 |

### 3.5 倍率叠加规则

```
总倍率 = min(持续消耗倍率 × (1 + 环境倍率之和), 5.0)
```

示例：奔跑(×2.0) + 极端温度(×1.5) + 骨折(×1.3) = ×2.0 × (1 + 0.5 + 0.3) = ×3.6

---

## 4. 恢复机制

### 4.1 静止休息

| 参数 | 值 | 说明 |
|------|-----|------|
| 触发条件 | 玩家速度 < 0.01 且不在船上/矿车 | 真正静止 |
| 延迟 | 3 秒 | 防止走走停停反复触发 |
| 恢复倍率 | ×1.0（0.3/秒） | 基础恢复速率 |
| 禁止条件 | 极端温度、水下、受到攻击 | 恶劣环境无法休息 |
| 中断条件 | 移动、攻击、被攻击、切换状态 | 任何动作中断静止状态 |

### 4.2 进食联动

| 参数 | 值 | 说明 |
|------|-----|------|
| 触发事件 | `PlayerItemConsumeEvent` | 玩家吃东西时 |
| 恢复量 | 食物恢复饥饿值 × 2.0 | 例如熟牛排恢复6饥饿 → 恢复12体力 |
| 腐烂惩罚 | 腐烂食物恢复量减半 | 与食物腐蚀系统联动 |
| 冷却 | 1 秒 | 防止连续进食刷体力 |

### 4.3 饮水联动

| 参数 | 值 | 说明 |
|------|-----|------|
| 触发事件 | 口渴系统中饮水事件 | 复用现有饮水逻辑 |
| 恢复量 | 饮水量 × 0.5 | 例如水瓶恢复30口渴 → 恢复15体力 |
| 脏水惩罚 | 恢复量不变，但有概率触发恶心 | 与水质系统联动 |

### 4.4 睡觉联动

| 参数 | 值 | 说明 |
|------|-----|------|
| 触发条件 | 玩家在床上度过夜晚 | 复用原版睡觉机制 |
| 恢复量 | 瞬间回满 | 睡觉是最强恢复方式 |
| 睡眠质量惩罚 | 室外睡觉只恢复 50% | 鼓励建造庇护所 |
| 温度惩罚 | 极端温度下无法入睡 | 与温度系统联动 |

### 4.5 药水/特殊食物

| 参数 | 值 | 说明 |
|------|-----|------|
| 配置方式 | YAML 配置文件定义物品列表 | 可自定义 |
| 恢复量 | 按物品单独配置 | 每个物品独立数值 |
| 示例 | 金苹果 +50，附魔金苹果 +100 | 可扩展 |

---

## 5. 惩罚机制

### 5.1 四阶段惩罚表

| 阶段 | 体力区间 | 显示名 | 移动速度 | 疾跑 | 药水效果 | 聊天提醒 |
|------|----------|--------|----------|------|----------|----------|
| FULL | > 60% | 充沛 | ×1.0 | 允许 | 无 | 无 |
| TIRED | 30% ~ 60% | 疲劳 | ×0.85 | 允许 | 无 | 首次进入时提醒 |
| EXHAUSTED | 10% ~ 30% | 筋疲力尽 | ×0.70 | 禁止 | 挖掘疲劳 I + 虚弱 I | 首次进入时提醒 |
| DEPLETED | < 10% | 体力耗尽 | ×0.50 | 禁止 | 挖掘疲劳 II + 虚弱 II | 每 30 秒提醒 |

### 5.2 惩罚实现方式

| 惩罚类型 | 实现方式 |
|----------|----------|
| 移动速度 | `Attribute.GENERIC_MOVEMENT_SPEED` 修改 |
| 禁止疾跑 | 每 tick 检测 `player.isSprinting`，体力不足时强制 `false` |
| 挖掘疲劳 | `player.addPotionEffect(PotionEffectType.MINING_FATIGUE, ...)` 短持续时间自动续期 |
| 虚弱 | `player.addPotionEffect(PotionEffectType.WEAKNESS, ...)` 短持续时间续期 |

> 药水效果采用 3 秒短持续 + 自动续期模式，与现有天气系统的视觉效果实现方式一致。

### 5.3 药水效果配置

```yaml
stamina-penalties:
  exhausted:
    mining-fatigue-amplifier: 0    # I 级
    weakness-amplifier: 0          # I 级
    effect-duration-seconds: 3
  depleted:
    mining-fatigue-amplifier: 1    # II 级
    weakness-amplifier: 1          # II 级
    effect-duration-seconds: 3
```

### 5.4 阶段切换逻辑

```
进入新阶段 → 记录 previousPhase → 发送提醒 → 应用惩罚
每 tick → 检测阶段变化 → 变化时发送提醒
DEPLETED 内 → chatWarnCooldown 递减 → 归零时发送提醒 → 重置
```

---

## 6. 系统联动

### 6.1 联动总览

| 联动系统 | 联动类型 | 触发条件 | 效果 |
|----------|----------|----------|------|
| 体温 | 消耗倍率 | 过热/过冷 | 体力消耗 ×1.5 |
| 骨折 | 消耗倍率 + 上限 | 骨折值 > 20 | 消耗 ×1.3，体力上限 -30% |
| 口渴 | 恢复倍率 | 脱水（口渴值 < 30） | 体力恢复速率 -50% |
| 潮湿度 | 消耗倍率 | 潮湿 > 70% | 体力消耗 ×1.2 |
| 食物 | 恢复倍率 | 饱食度满 | 体力恢复 +25% |
| 天气 | 消耗倍率 | 暴风雪/沙尘暴 | 体力消耗 ×1.3 |
| 季节 | 基础消耗倍率 | 冬季/夏季 | 冬季 ×1.1，夏季 ×1.05 |

### 6.2 联动细节

**体温联动：**
```kotlin
val temperaturePenalty = when (playerState.temperaturePhase) {
    TemperaturePhase.SEVERE_HEAT, TemperaturePhase.SEVERE_COLD -> 1.5
    TemperaturePhase.HEAT, TemperaturePhase.COLD_MILD -> 1.2
    else -> 1.0
}
```

**骨折联动：**
```kotlin
val fracturePenalty = if (playerState.fracture > 20) 1.3 else 1.0
val staminaMax = if (playerState.fracture > 20) 70.0 else 100.0
```

**口渴联动：**
```kotlin
val thirstRecoveryMultiplier = if (playerState.hydration < 30) 0.5 else 1.0
```

**潮湿度联动：**
```kotlin
val wetnessPenalty = if (playerState.wetness > 0.7) 1.2 else 1.0
```

**食物联动：**
```kotlin
val foodRecoveryBonus = if (player.foodLevel >= 20) 1.25 else 1.0
```

**天气联动：**
```kotlin
val weatherPenalty = when (globalState.weather) {
    WeatherType.BLIZZARD, WeatherType.SANDSTORM -> 1.3
    else -> 1.0
}
```

**季节联动：**
```kotlin
val seasonPenalty = when (globalState.season) {
    Season.WINTER -> 1.1
    Season.SUMMER -> 1.05
    else -> 1.0
}
```

### 6.3 倍率叠加示例

```
场景：冬季 + 暴风雪 + 骨折 + 奔跑
总消耗 = 0.05 × 2.0(奔跑) × 1.1(冬) × 1.3(暴) × 1.3(骨) = 0.186/秒
耗尽时间 ≈ 540 秒 ≈ 9 分钟（正常奔跑 16 分钟）
```

---

## 7. 聊天提醒系统

### 7.1 提醒规则

| 阶段切换 | 提醒方式 | 冷却时间 |
|----------|----------|----------|
| FULL → TIRED | 单次提醒 | 无 |
| TIRED → EXHAUSTED | 单次提醒 | 无 |
| EXHAUSTED → DEPLETED | 单次提醒 | 无 |
| DEPLETED 内持续 | 重复提醒 | 30 秒 |
| 阶段恢复 | 单次提醒 | 无 |

### 7.2 提醒消息

```yaml
stamina-messages:
  enter-tired: "&e⚡ 你感到有些疲劳了... (体力: {stamina}%)"
  enter-exhausted: "&c⚡ 你筋疲力尽了！无法疾跑！ (体力: {stamina}%)"
  enter-depleted: "&4⚡ 体力耗尽！你几乎无法行动！ (体力: {stamina}%)"
  depleted-reminder: "&4⚡ 你需要休息或进食来恢复体力！ (体力: {stamina}%)"
  recovered-from-tired: "&a⚡ 你感觉好多了。 (体力: {stamina}%)"
  recovered-from-exhausted: "&a⚡ 你恢复了一些精力。 (体力: {stamina}%)"
  recovered-from-depleted: "&a⚡ 你终于缓过来了。 (体力: {stamina}%)"
  depleted-reminder-cooldown-seconds: 30
```

### 7.3 防刷屏机制

| 机制 | 实现方式 |
|------|----------|
| 阶段去重 | 记录上一阶段，仅阶段变化时提醒 |
| DEPLETED 冷却 | `chatWarnCooldown` 计时器，30 秒重置 |
| 消息合并 | 同一 tick 内多条提醒合并为一条 |
| 颜色区分 | 黄色(疲劳)、橙红(筋疲力尽)、红色(耗尽) |

---

## 8. HUD 与 BossBar

### 8.1 设计原则

- **体力值不写入 ActionBar**，不提供任何开关
- **低体力时使用 BossBar 警告**（复用现有模式）
- **聊天框提醒**（第七节已定义）

### 8.2 BossBar 警告

| 条件 | BossBar 内容 | 颜色 | 样式 |
|------|--------------|------|------|
| 体力 < 30% (EXHAUSTED) | "体力不足！寻找食物或休息！" | YELLOW | SOLID |
| 体力 < 10% (DEPLETED) | "体力耗尽！你几乎无法行动！" | RED | SOLID |
| 体力 >= 30% | 隐藏 BossBar | - | - |

### 8.3 BossBar 优先级

当多个极端状态同时存在时，BossBar 显示优先级最高的警告：

```
体力 DEPLETED > 体力 EXHAUSTED > 温度极端 > 口渴极端
```

---

## 9. 命令系统

### 9.1 命令结构

复用现有 `RealWorldCommand`，新增 `stamina` 子命令：

```
/rw stamina <子命令>
```

### 9.2 子命令列表

| 命令 | 权限 | 说明 |
|------|------|------|
| `/rw stamina info [玩家]` | `phcore.admin` | 查看目标玩家体力信息 |
| `/rw stamina set <玩家> <数值>` | `phcore.admin` | 设置玩家体力值 |
| `/rw stamina add <玩家> <数值>` | `phcore.admin` | 增加玩家体力值 |
| `/rw stamina remove <玩家> <数值>` | `phcore.admin` | 减少玩家体力值 |
| `/rw stamina reset <玩家>` | `phcore.admin` | 重置玩家体力为满值 |
| `/rw stamina toggle` | `phcore.admin` | 开关体力系统 |

### 9.3 命令输出示例

**`/rw stamina info`：**
```
⚡ 玩家体力信息: Steve
  体力值: 72.5 / 100.0
  阶段: 疲劳
  消耗倍率: ×1.5 (奔跑 + 极端温度)
  恢复倍率: ×1.0 (静止休息中)
```

**`/rw stamina info`（带联动信息）：**
```
⚡ 玩家体力信息: Steve
  体力值: 45.0 / 70.0 (骨折导致上限降低)
  阶段: 疲劳
  消耗倍率: ×2.6 (奔跑 × 冬季 × 暴风雪 × 骨折)
  恢复倍率: ×0.5 (脱水导致恢复减缓)
```

### 9.4 Tab 补全

```
/rw stamina → info, set, add, remove, reset, toggle
/rw stamina info → [在线玩家列表]
/rw stamina set → [在线玩家列表] → [数值建议: 0-100]
/rw stamina add → [在线玩家列表] → [数值建议: 0-100]
/rw stamina remove → [在线玩家列表] → [数值建议: 0-100]
/rw stamina reset → [在线玩家列表]
```

---

## 10. 数据存储

### 10.1 存储方式

复用现有 `RealWorldStorage` 的存储架构，体力数据随玩家状态一起持久化。

### 10.2 存储字段

在玩家状态数据中新增以下字段：

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `stamina` | Double | 100.0 | 当前体力值 |
| `staminaPhase` | String | "FULL" | 当前阶段枚举名 |

> 其他运行时状态（`staminaIdleTimer`、`staminaChatWarnCooldown` 等）为内存态，重启后重置，不需要持久化。

### 10.3 存储时机

| 时机 | 操作 |
|------|------|
| 自动保存 | 随 `RealWorldStorage.flushDirty()` 一起写入（5 分钟间隔） |
| 玩家退出 | `PlayerQuitEvent` 时立即保存 |
| 服务器关闭 | `@Disable` 生命周期中保存 |
| 命令修改 | `set/add/remove/reset` 后立即标记脏数据 |

### 10.4 数据兼容

- 旧存档无 `stamina` 字段时，使用默认值 `100.0`（满体力）
- 无需数据迁移，向后兼容

---

## 11. 自定义事件

### 11.1 事件定义

| 事件类 | 触发时机 | 用途 |
|--------|----------|------|
| `StaminaConsumeEvent` | 体力被消耗时 | 其他插件可修改消耗量或取消消耗 |
| `StaminaRecoverEvent` | 体力恢复时 | 其他插件可修改恢复量或取消恢复 |
| `StaminaPhaseChangeEvent` | 体力阶段切换时 | 其他插件可响应阶段变化 |
| `StaminaDepletedEvent` | 体力降至 0 时 | 触发特殊逻辑 |

### 11.2 事件字段

**StaminaConsumeEvent：**
```kotlin
class StaminaConsumeEvent(
    val player: Player,
    val source: StaminaConsumeSource,
    var amount: Double,
) : Cancellable
```

**StaminaRecoverEvent：**
```kotlin
class StaminaRecoverEvent(
    val player: Player,
    val source: StaminaRecoverSource,
    var amount: Double,
) : Cancellable
```

**StaminaPhaseChangeEvent：**
```kotlin
class StaminaPhaseChangeEvent(
    val player: Player,
    val from: StaminaPhase,
    val to: StaminaPhase,
)
```

### 11.3 消耗来源枚举

```kotlin
enum class StaminaConsumeSource {
    SPRINT, SWIM, CLIMB, ATTACK, MINE, USE_TOOL,
    UNDERWATER, HIGH_ALTITUDE, ENVIRONMENT,
}
```

### 11.4 恢复来源枚举

```kotlin
enum class StaminaRecoverSource {
    IDLE, FOOD, DRINK, SLEEP, SPECIAL_ITEM, COMMAND,
}
```

---

## 12. 实现架构

### 12.1 文件结构

```
project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/
├── ... (现有文件)
├── StaminaEngine.kt          # 体力引擎（核心逻辑）
├── StaminaModels.kt          # 体力数据模型（枚举、事件）
└── StaminaSettings.kt        # 体力配置读取
```

### 12.2 类职责划分

| 类 | 职责 | 依赖 |
|----|------|------|
| `StaminaEngine` | 体力计算、消耗/恢复逻辑、阶段判定、惩罚应用 | `StaminaSettings`, `RealWorldStorage`, 现有引擎 |
| `StaminaModels` | `StaminaPhase`、`StaminaConsumeSource`、`StaminaRecoverSource` 枚举，自定义事件类 | 无 |
| `StaminaSettings` | 从 YAML 读取体力配置，提供类型安全的访问器 | TabooLib 配置系统 |

### 12.3 StaminaEngine 核心方法

```kotlin
object StaminaEngine {
    fun init()
    fun reload()

    // 每 tick 调用
    fun tick(player: Player, playerState: PlayerEnvState, globalState: GlobalEnvState, deltaSeconds: Double)

    // 事件监听
    fun onSprint(player: Player, playerState: PlayerEnvState)
    fun onSwim(player: Player, playerState: PlayerEnvState)
    fun onClimb(player: Player, playerState: PlayerEnvState)
    fun onAttack(player: Player, playerState: PlayerEnvState)
    fun onMine(player: Player, playerState: PlayerEnvState)
    fun onUseTool(player: Player, playerState: PlayerEnvState)

    // 静止检测
    fun checkIdle(player: Player, playerState: PlayerEnvState, deltaSeconds: Double)

    // 恢复触发
    fun onEat(player: Player, playerState: PlayerEnvState, hungerRestored: Int)
    fun onDrink(player: Player, playerState: PlayerEnvState, hydrationRestored: Double)
    fun onSleep(player: Player, playerState: PlayerEnvState, isOutdoor: Boolean)

    // 命令接口
    fun getStaminaInfo(player: Player): StaminaInfo
    fun setStamina(player: Player, amount: Double)
    fun addStamina(player: Player, amount: Double)
    fun removeStamina(player: Player, amount: Double)
    fun resetStamina(player: Player)
}
```

### 12.4 事件监听注册

| 事件 | 处理逻辑 |
|------|----------|
| `PlayerMoveEvent` | 检测奔跑/游泳/攀爬状态 |
| `EntityDamageByEntityEvent` | 玩家攻击时扣除体力 |
| `BlockBreakEvent` | 挖掘时扣除体力 |
| `PlayerInteractEvent` | 使用工具时扣除体力 |
| `PlayerItemConsumeEvent` | 进食时恢复体力 |
| `EntityAirChangeEvent` | 水下憋气检测 |
| `PlayerBedEnterEvent` | 睡觉时恢复体力 |
| `PlayerQuitEvent` | 保存体力数据 |

### 12.5 与现有引擎的集成点

| 集成点 | 位置 | 说明 |
|--------|------|------|
| 主 tick 循环 | `RealWorldService` 的 `startTickLoop()` | 在现有 tick 末尾调用 `StaminaEngine.tick()` |
| 配置加载 | `RealWorldSettings.init()` / `reload()` | 调用 `StaminaSettings.init()` / `reload()` |
| 数据存储 | `RealWorldStorage` 的 `PlayerEnvState` | 新增 `stamina` 字段 |
| HUD 显示 | `SurvivalHud` 的 BossBar 管理 | 新增体力 BossBar 逻辑 |
| 命令注册 | `RealWorldCommand` | 新增 `stamina` 子命令 |

### 12.6 执行流程

```
服务器启动
  └── RealWorldService.init()
        ├── RealWorldSettings.init() → StaminaSettings.init()
        ├── RealWorldStorage.init() → 加载玩家体力数据
        └── startTickLoop()
              └── 每 tick:
                    ├── 现有系统 tick (温度/口渴/天气等)
                    └── StaminaEngine.tick()
                          ├── 计算消耗倍率 (四层 + 联动)
                          ├── 计算恢复倍率 (恢复方式 + 联动)
                          ├── 更新体力值
                          ├── 判定阶段变化
                          ├── 应用惩罚 (速度/药水效果)
                          ├── 发送聊天提醒
                          └── 更新 BossBar
```

---

## 13. 完整配置结构

```yaml
# =============================================================================
# 体力系统配置
# =============================================================================
stamina:
  enabled: true

  # 基础数值
  max-stamina: 100.0
  base-consumption-rate: 0.05
  base-recovery-rate: 0.3
  max-multiplier: 5.0

  # 持续消耗倍率
  continuous:
    sprint-multiplier: 2.0
    swim-multiplier: 2.5
    climb-multiplier: 2.0

  # 动作消耗（瞬间扣除）
  actions:
    attack-cost: 0.15
    mine-cost: 0.075
    use-tool-cost: 0.1

  # 特殊场景消耗倍率
  special:
    underwater-multiplier: 4.0
    high-altitude-multiplier: 2.0
    high-altitude-y: 120

  # 恢复方式
  recovery:
    idle:
      enabled: true
      delay-seconds: 3.0
      multiplier: 1.0
      blocked-in-extreme-temperature: true
      blocked-underwater: true
      blocked-when-attacked: true
    food:
      enabled: true
      hunger-to-stamina-ratio: 2.0
      rotten-food-penalty: 0.5
      cooldown-seconds: 1.0
    drink:
      enabled: true
      hydration-to-stamina-ratio: 0.5
    sleep:
      enabled: true
      outdoor-recovery-percent: 50.0
      blocked-in-extreme-temperature: true
    special-items:
      enabled: true
      items:
        GOLDEN_APPLE: 50.0
        ENCHANTED_GOLDEN_APPLE: 100.0

  # 惩罚阶段
  penalties:
    tired:
      threshold: 60.0
      speed-multiplier: 0.85
    exhausted:
      threshold: 30.0
      speed-multiplier: 0.70
      mining-fatigue-amplifier: 0
      weakness-amplifier: 0
    depleted:
      threshold: 10.0
      speed-multiplier: 0.50
      mining-fatigue-amplifier: 1
      weakness-amplifier: 1
    effect-duration-seconds: 3

  # 聊天提醒
  messages:
    enter-tired: "&e⚡ 你感到有些疲劳了... (体力: {stamina}%)"
    enter-exhausted: "&c⚡ 你筋疲力尽了！无法疾跑！ (体力: {stamina}%)"
    enter-depleted: "&4⚡ 体力耗尽！你几乎无法行动！ (体力: {stamina}%)"
    depleted-reminder: "&4⚡ 你需要休息或进食来恢复体力！ (体力: {stamina}%)"
    recovered-from-tired: "&a⚡ 你感觉好多了。 (体力: {stamina}%)"
    recovered-from-exhausted: "&a⚡ 你恢复了一些精力。 (体力: {stamina}%)"
    recovered-from-depleted: "&a⚡ 你终于缓过来了。 (体力: {stamina}%)"
    depleted-reminder-cooldown-seconds: 30

  # HUD
  hud:
    bossbar-enabled: true
    bossbar-title-exhausted: "&e⚡ 体力不足！寻找食物或休息！"
    bossbar-title-depleted: "&4⚡ 体力耗尽！你几乎无法行动！"
    bossbar-color-exhausted: "YELLOW"
    bossbar-color-depleted: "RED"
    bossbar-style: "SOLID"

  # 系统联动
  integration:
    temperature:
      enabled: true
      extreme-multiplier: 1.5
      mild-multiplier: 1.2
    fracture:
      enabled: true
      threshold: 20.0
      consumption-multiplier: 1.3
      max-stamina-reduction: 30.0
    thirst:
      enabled: true
      dehydration-threshold: 30.0
      recovery-multiplier: 0.5
    wetness:
      enabled: true
      threshold: 0.7
      consumption-multiplier: 1.2
    food:
      enabled: true
      full-saturation-bonus: 1.25
    weather:
      enabled: true
      extreme-multiplier: 1.3
    season:
      enabled: true
      winter-multiplier: 1.1
      summer-multiplier: 1.05
```
