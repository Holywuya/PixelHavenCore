# 温度系统真实感改造设计规格

**日期**: 2026-05-28  
**状态**: 设计完成，待实现  
**范围**: 温度系统核心逻辑重构

---

## 1. 目标

将温度系统从简单的数值叠加模型改造为更贴近现实的体感温度模型，引入水温系统、细化遮蔽类型、调整温差参数，提升生存玩法的真实感和策略深度。

---

## 2. 核心架构：体感温度中间层

### 2.1 设计理念

引入**体感温度（feelsLike）**作为中间计算层：
- 环境修正 → 体感温度 → 体温变化
- 体感温度综合了所有环境因素
- 体温始终趋向体感温度变化

### 2.2 体感温度公式

```kotlin
feelsLike = biomeBaseTemp
          + seasonModifier
          + timeModifier
          + weatherModifier
          + altitudeModifier
          + shelterModifier
          + armorModifier
          + blockModifier
          + wetnessModifier
```

**特殊情况**：当玩家浸水时，体感温度直接替换为水温：
```kotlin
feelsLike = if (player.isInWater) calculateWaterTemp(player)
            else calculateAirFeelsLike(...)
```

### 2.3 体温变化公式

```kotlin
val delta = (feelsLike - bodyTemp) * changeRate
bodyTemp += delta.coerceIn(-maxChangePerTick, +maxChangePerTick)
```

其中 `changeRate` 在浸水时乘以 `waterConductivity`（默认 4.0）。

---

## 3. 水温系统

### 3.1 水温计算公式

```kotlin
waterTemp = biomeWaterTemp + seasonLag + depthModifier
```

### 3.2 生物群系基础水温

| 生物群系类型 | 基础水温 |
|------------|---------|
| 热带/jungle/bamboo | 24°C |
| 沼泽/mangrove | 22°C |
| 沙漠/恶地 | 20°C |
| 平原/森林/海滩 | 16°C |
| 海洋/河流 | 14°C |
| 针叶林/暗林 | 10°C |
| 雪山/冰原/frozen | 2°C |
| 默认 | 14°C |

### 3.3 季节滞后

水温跟随**上一季**的季节修正，且只取 30%：
```kotlin
seasonLag = previousSeason.temperatureModifier * seasonLagRatio
```

**实现细节**：
- 当 `seasonProgress < seasonLagRatio` 时，使用当前季节的修正值
- 否则使用上一季的修正值
- 示例：当前冬季（-20°C），`seasonLag = 0°C * 0.3 = 0°C`（秋季修正为 0）

### 3.4 深度修正

每深入水下 10 格，水温降低 1°C，最多降 5°C：
```kotlin
val waterDepth = (waterSurfaceY - playerLocation.blockY).coerceAtLeast(0)
val depthModifier = -(waterDepth / 10.0).coerceAtMost(maxDepthCool)
```

**水面 Y 坐标获取**：
- 从玩家位置向上扫描，找到第一个非水方块
- 如果找不到（完全水下），使用世界最高 Y 值

### 3.5 水中导热

浸水时体温变化速率：
```kotlin
val waterChangeRate = maxChangePerTick * waterConductivityMultiplier
```

默认 `waterConductivityMultiplier = 4.0`（水中导热速度是空气的 4 倍）。

---

## 4. 潮湿度：从隔热到蒸发冷却

### 4.1 当前问题

旧逻辑：潮湿度把温度拉向舒适中点 25.5°C，不合理。

### 4.2 新逻辑：蒸发冷却

潮湿度产生**蒸发冷却**效果，只在**非浸水状态**生效：
```kotlin
wetnessModifier = -wetness * wetnessCoolingFactor
```

**效果**：
- 完全干燥（wetness=0）：无影响
- 淋雨（wetness≈0.3）：约 -2.4°C
- 刚从水里出来（wetness≈1.0）：约 -8°C

**安全保障**：
- 夏天高温地区，淋雨帮助散热（符合现实）
- 冬天淋雨加速失温（符合现实）
- 浸水时不受潮湿度影响（体温直接趋向水温）

---

## 5. 昼夜温差改进

### 5.1 公式改进

```kotlin
timeModifier = dayNightScale * biomeDayNightFactor * cos(2π * (worldTime - 6000) / 24000)
```

### 5.2 参数说明

| 参数 | 默认值 | 说明 |
|-----|-------|------|
| `dayNightScale` | 10.0 | 基础昼夜温差幅度（±10°C） |
| `biomeDayNightFactor` | 按群系 | 沙漠 1.5，丛林/沼泽 0.5，默认 1.0 |

### 5.3 生物群系昼夜因子映射

```kotlin
private fun getBiomeDayNightFactor(biomeName: String): Double = when {
    "desert" in biomeName || "badlands" in biomeName -> 1.5
    "jungle" in biomeName || "bamboo" in biomeName -> 0.5
    "swamp" in biomeName || "mangrove" in biomeName -> 0.5
    "ocean" in biomeName || "river" in biomeName -> 0.7
    else -> 1.0
}
```

### 5.4 效果示例

- **沙漠中午（夏）**：35 + 10 + 15 = 60°C（极端，需遮蔽）
- **沙漠午夜（夏）**：35 + 10 - 15 = 30°C（仍热）
- **平原中午（夏）**：15 + 10 + 10 = 35°C
- **平原午夜（夏）**：15 + 10 - 10 = 15°C
- **雪原午夜（冬）**：-10 - 20 - 10 = -40°C（致命，需进屋）

---

## 6. 季节温差改进

### 6.1 修正值调整

| 季节 | 旧修正 | 新修正 | 理由 |
|-----|-------|-------|------|
| 春 | +5°C | +3°C | 春秋温差不大，春天回暖慢 |
| 夏 | +15°C | +10°C | 配合昼夜温差已够热 |
| 秋 | 0°C | 0°C | 基准季节 |
| 冬 | -15°C | -20°C | 冬天更冷，生存压力更大 |

### 6.2 实现位置

修改 `Season` 枚举的 `temperatureModifier` 字段：
```kotlin
SPRING("春", 3.0, ...),
SUMMER("夏", 10.0, ...),
AUTUMN("秋", 0.0, ...),
WINTER("冬", -20.0, ...),
```

### 6.3 平滑过渡

保持现有的 `transitionProgress` 插值逻辑不变。

---

## 7. 遮蔽细化

### 7.1 遮蔽类型枚举

```kotlin
enum class ShelterType {
    NONE,     // 露天：无修正
    CANOPY,   // 树荫/半遮蔽：+2°C
    BUILDING, // 实体建筑：+8°C
}
```

### 7.2 判定逻辑

```kotlin
fun classifyShelter(player: Player): ShelterType {
    val hasOverhead = hasAnyOverheadCover(player.eyeLocation)
    if (!hasOverhead) return ShelterType.NONE
    
    val hasCompleteRoof = hasWeatherTopCoverage(player.eyeLocation)
    return if (hasCompleteRoof) ShelterType.BUILDING else ShelterType.CANOPY
}
```

**判定规则**：
- `NONE`：头顶无方块
- `CANOPY`：头顶有方块，但水平半径内有露天开口（树荫、半砖、楼梯等）
- `BUILDING`：头顶有不透明方块，且水平 `shelterHorizontalRadius` 范围内全部有屋顶

### 7.3 修正值

```kotlin
shelterModifier = when (shelterType) {
    ShelterType.NONE -> 0.0
    ShelterType.CANOPY -> shelterCanopyBonus      // 默认 2.0°C
    ShelterType.BUILDING -> shelterBuildingBonus   // 默认 8.0°C
}
```

### 7.4 HUD 展示

| 遮蔽类型 | HUD 图标 | 说明 |
|---------|---------|------|
| NONE | ☁ | 露天 |
| CANOPY | 🌳 | 树荫/半遮蔽 |
| BUILDING | 🏠 | 室内 |

---

## 8. 配置文件变更

### 8.1 temperature.yml 新增项

```yaml
# 体感温度系统
feels-like:
  wetness-cooling: 8.0              # 满湿时最大蒸发冷却(°C)

# 昼夜温差
time:
  day-night-scale: 10.0             # 基础昼夜温差幅度

# 水温系统
water:
  enabled: true
  conductivity-multiplier: 4.0      # 水中导热倍率
  depth-cool-per-10-blocks: 1.0     # 每 10 格深度降温
  max-depth-cool: 5.0               # 深度降温上限
  season-lag-ratio: 0.3             # 季节滞后比例

# 遮蔽细化
shelter:
  canopy-bonus: 2.0                 # 树荫/半遮蔽修正(°C)
  building-bonus: 8.0               # 建筑修正(°C)
  horizontal-radius: 1              # 建筑判定水平半径
  glass-counts-as-building: false
  leaves-count-as-canopy: true
```

### 8.2 season.yml 变更

```yaml
# Season 枚举修正值
# SPRING: temperatureModifier = 3.0
# SUMMER: temperatureModifier = 10.0
# AUTUMN: temperatureModifier = 0.0
# WINTER: temperatureModifier = -20.0
```

---

## 9. 代码变更清单

### 9.1 TemperatureEngine.kt

**修改**：
- `compute()` 方法重构为体感温度模型
- `updateShelterState()` 返回 `ShelterType`
- 新增 `calculateWaterTemp()` 方法
- 新增 `calculateAirFeelsLike()` 方法
- 新增 `getBiomeDayNightFactor()` 方法
- 修改 `getTimeTemperatureModifier()` 使用 `dayNightScale`

**新增方法**：
```kotlin
fun calculateWaterTemp(player: Player): Double
fun calculateAirFeelsLike(player: Player, state: PlayerEnvState, global: GlobalEnvState): Double
fun getBiomeDayNightFactor(biomeName: String): Double
fun getBiomeWaterTemp(biomeName: String): Double
```

### 9.2 TemperatureSettings.kt

**新增字段**：
```kotlin
var wetnessCoolingFactor: Double = 8.0
var dayNightScale: Double = 10.0
var waterEnabled: Boolean = true
var waterConductivityMultiplier: Double = 4.0
var waterDepthCoolPer10Blocks: Double = 1.0
var waterMaxDepthCool: Double = 5.0
var waterSeasonLagRatio: Double = 0.3
var shelterCanopyBonus: Double = 2.0
var shelterBuildingBonus: Double = 8.0
var shelterLeavesCountAsCanopy: Boolean = true
```

### 9.3 RealWorldModels.kt

**修改 PlayerEnvState**：
```kotlin
// 替换
var isSheltered: Boolean = false
// 为
var shelterType: ShelterType = ShelterType.NONE

// 新增枚举
enum class ShelterType { NONE, CANOPY, BUILDING }
```

### 9.4 Season.kt

**修改 temperatureModifier**：
```kotlin
SPRING("春", 3.0, ...),
SUMMER("夏", 10.0, ...),
AUTUMN("秋", 0.0, ...),
WINTER("冬", -20.0, ...),
```

### 9.5 SurvivalHud.kt

**修改 buildStatusActionBar()**：
```kotlin
val shelterText = when (state.shelterType) {
    ShelterType.NONE -> RealWorldSettings.hudUnshelteredIndicator
    ShelterType.CANOPY -> "🌳"
    ShelterType.BUILDING -> RealWorldSettings.hudShelteredIndicator
}
```

### 9.6 TemperatureEngine.isWeatherSheltered()

**调整判定**：
- 只有 `ShelterType.BUILDING` 才算天气遮蔽
- `CANOPY` 不算（树荫挡不住雨）

---

## 10. 测试场景

### 10.1 水温测试

- [ ] 夏天跳入热带水域（24°C），体温应趋向 24°C（舒适）
- [ ] 冬天跳入冻湖（2°C），体温应快速下降（约 4 倍速）
- [ ] 深潜（水下 50 格），水温应降低 5°C
- [ ] 浸水时不受潮湿度蒸发冷却影响

### 10.2 昼夜温差测试

- [ ] 沙漠中午体温应显著升高（可能达 40°C+）
- [ ] 沙漠午夜体温应明显降低（但仍高于雪原）
- [ ] 丛林昼夜温差应较小（约 ±5°C）

### 10.3 季节温差测试

- [ ] 夏天平原中午应接近舒适上限（35°C）
- [ ] 冬天雪原午夜应致命（-40°C），必须进屋

### 10.4 遮蔽测试

- [ ] 树荫下（CANOPY）体温修正 +2°C
- [ ] 石屋内（BUILDING）体温修正 +8°C
- [ ] 露天（NONE）无修正
- [ ] 只有 BUILDING 才能防雨

### 10.5 潮湿度测试

- [ ] 淋雨时体感温度降低约 2-3°C
- [ ] 刚从水里出来（wetness=1.0）体感温度降低 8°C
- [ ] 浸水时潮湿度不影响体温（直接趋向水温）

---

## 11. 向后兼容性

### 11.1 配置迁移

- 旧 `shelter.bonus` 配置项废弃，替换为 `canopy-bonus` 和 `building-bonus`
- `PlayerEnvState` 为内存对象，`isSheltered` 不持久化，直接替换为 `shelterType` 即可

### 11.2 HUD 格式

- `{sheltered}` 占位符保持，但输出改为三态图标
- 旧配置格式兼容，无需修改

---

## 12. 实现优先级

1. **P0（核心）**：体感温度中间层、水温系统、遮蔽细化
2. **P1（参数）**：昼夜温差调整、季节温差调整
3. **P2（优化）**：HUD 展示更新、配置迁移

---

## 13. 验收标准

- [ ] 夏天沙漠中午体感温度 > 50°C
- [ ] 冬天雪原午夜体感温度 < -30°C
- [ ] 热带水域夏天水温 22-26°C
- [ ] 冻湖冬天水温 0-4°C
- [ ] 浸水时体温变化速率是空气的 4 倍
- [ ] 树荫下体温修正 2°C，建筑内 8°C
- [ ] 淋雨时体感温度降低 2-3°C

---

## 14. 参考资料

- 现实世界水温范围：热带海域 26-30°C，温带海域 10-20°C，极地海域 -2-5°C
- 水的导热率：约空气的 25 倍（游戏中简化为 4 倍）
- 人体舒适温度范围：20-26°C（当前游戏 15-36°C 过宽，本次不调整）
- 沙漠昼夜温差：可达 30-40°C（游戏中 ±15°C 已简化）

---

**设计完成，待用户审查后进入实现阶段。**
