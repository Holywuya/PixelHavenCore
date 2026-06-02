# 体温系统暴露压力与性能优化设计

**日期**：2026-06-02  
**范围**：`project/core/src/main/kotlin/com/pixlehavencore/feature/realworld/temperature/`、`project/core/src/main/resources/feature/realworld/temperature.yml`  
**状态**：设计已批准，待实现计划

---

## 1. 背景与目标

当前体温系统已经具备环境温度、季节、昼夜、天气、海拔、水温、潮湿度、方块辐射、护甲绝缘、遮蔽绝缘和主动体温调节。问题集中在两类：

1. **数值节奏**：极端环境可能让玩家过快进入危险，反应窗口不足。
2. **性能/稳定性**：热源扫描、遮蔽判定和出入水/死亡保护等边界需要更稳妥。

本次优化目标：

- 裸奔玩家在极端环境中保持高风险，约 **30~60 秒**内感到明显危险。
- 有基础装备、遮蔽或热源/冷源后，生存窗口延长到 **2~4 分钟**。
- 不推翻现有体温模型，只在极端环境变化速度上增加游戏性控制。
- 性能优化只做缓存、限频、短路和边界保护，不新增异步世界访问。

---

## 2. 核心机制：冷热分离的极端暴露压力

### 2.1 设计原则

新增“极端暴露压力”作为现有模型之后的薄层：

- 现有温度模型决定“环境有多冷/热”。
- 绝缘与体温调节决定“玩家能抵抗多少”。
- 暴露压力决定“危险来得有多快”。

暴露压力不直接改变环境温度，也不直接造成伤害，只影响极端环境下最终体温变化速度。

### 2.2 暴露判断

低温暴露：

```kotlin
effectiveEnvTemp < TemperatureSettings.comfortMin - TemperatureSettings.exposureColdThreshold
```

高温暴露：

```kotlin
effectiveEnvTemp > TemperatureSettings.comfortMax + TemperatureSettings.exposureHeatThreshold
```

默认建议：

```yaml
exposure:
  cold-threshold: 10.0
  heat-threshold: 8.0
```

即舒适下限 15°C 时，环境低于 5°C 开始累计低温压力；舒适上限 36°C 时，环境高于 44°C 开始累计高温压力。

### 2.3 状态字段

在 `PlayerEnvState` 中新增冷热分离压力：

```kotlin
var coldExposurePressure: Double = 0.0
var heatExposurePressure: Double = 0.0
```

规则：

- 当前为低温暴露：增长 `coldExposurePressure`，恢复 `heatExposurePressure`。
- 当前为高温暴露：增长 `heatExposurePressure`，恢复 `coldExposurePressure`。
- 当前非极端：两个压力都恢复。
- 最终只使用当前方向的压力。

这样避免玩家从雪原进入沙漠时继承错误压力。

### 2.4 压力增长

压力增长公式：

```kotlin
pressure += baseGain * severity * vulnerability * (1.0 - protectionScore) * tickSeconds
pressure = pressure.coerceIn(0.0, 1.0)
```

并保留极端环境中的最小增长：

```kotlin
gain = gain.coerceAtLeast(TemperatureSettings.exposureMinGainPerSecond)
```

默认建议：

```yaml
exposure:
  base-gain-per-second: 0.018
  min-gain-per-second: 0.0025
  recovery-per-second: 0.01
  water-gain-multiplier: 1.5
```

含义：

- 裸奔时压力快速增长，约 30~60 秒接近高风险。
- 有准备时压力增长明显变慢，但不会完全免疫。
- 水中压力增长更快，但仍从低压力逐步上升，不瞬间满值。

### 2.5 压力影响体温变化

当前体温变化流程在计算 `rawChange` 后接入压力倍率：

```kotlin
val rawChange = envDelta * (1.0 - Math.exp(-absorptionRate * Math.abs(envDelta)))
val exposureMultiplier = updateAndGetExposureMultiplier(...)
val change = (rawChange * exposureMultiplier).coerceIn(-maxChange, maxChange)
state.temperature += change
```

默认建议：

```yaml
exposure:
  min-extreme-multiplier: 0.35
  max-extreme-multiplier: 1.0
```

含义：

- 刚进入极端环境时，体温变化速度为当前模型的 35%。
- 暴露越久，逐渐恢复到当前模型的完整强度。
- 装备、遮蔽和热源会让玩家更久停留在低倍率阶段。

---

## 3. 装备门槛与保护分数

### 3.1 保护分数来源

保护分数只用于暴露压力，不直接改环境温度：

```kotlin
protectionScore = armorProtection + shelterProtection + blockProtection + regulationProtection
```

来源：

| 来源 | 说明 |
|---|---|
| 护甲绝缘 | 复用现有各材质绝缘和温度抵抗附魔结果 |
| 遮蔽绝缘 | 复用 CANOPY / BUILDING 绝缘 |
| 热源/冷源 | 低温中热源有效，高温中冷源有效 |
| 体温调节能力 | 饱食度高、骨折低时略微提高恢复能力 |

### 3.2 护甲保护

护甲保护按现有绝缘上限归一化：

```kotlin
armorProtection = (totalInsulation / TemperatureSettings.armorInsulationMax).coerceIn(0.0, 1.0)
```

示例：

| 总绝缘 | 保护分数 |
|---:|---:|
| 0% | 0.00 |
| 铁套 24% | 0.34 |
| 钻石套 40% | 0.57 |
| 下界合金套 48% | 0.69 |
| 满上限 70% | 1.00 |

### 3.3 热源/冷源保护

复用 `state.temperatureBlockModifier`：

```kotlin
if (isColdExposure && state.temperatureBlockModifier > 0) {
    blockProtection = (state.temperatureBlockModifier / fullModifier).coerceIn(0.0, maxProtection)
}

if (isHeatExposure && state.temperatureBlockModifier < 0) {
    blockProtection = (-state.temperatureBlockModifier / fullModifier).coerceIn(0.0, maxProtection)
}
```

默认建议：

```yaml
exposure:
  block-protection-max: 0.4
  block-protection-full-modifier: 20.0
```

热源/冷源最多提供 40% 暴露保护，避免单个方块直接免疫极端环境。

### 3.4 三档体验目标

| 档位 | 条件 | 目标 |
|---|---|---|
| 无准备裸奔 | 无护甲、无遮蔽、无热源/冷源 | 极端环境 30~60 秒明显危险 |
| 基础准备 | 普通护甲、遮蔽或有效热源/冷源 | 生存窗口延长到 2~4 分钟 |
| 充分准备 | 高绝缘、附魔、建筑、热源/冷源齐全 | 多数极端环境可控，但不完全免疫 |

冰水、深水、沙漠正午厚甲等超极端场景仍保留风险。

---

## 4. 性能与稳定性优化

### 4.1 热源扫描缓存

当前热源扫描已有时间间隔。新增玩家位置与环境缓存，玩家原地时延长扫描间隔。

新增字段：

```kotlin
var heatSourceCacheBlockX: Int = Int.MIN_VALUE
var heatSourceCacheBlockY: Int = Int.MIN_VALUE
var heatSourceCacheBlockZ: Int = Int.MIN_VALUE
var heatSourceCacheWorldName: String = ""
var heatSourceCacheBiomeTemperature: Double = 20.0
```

刷新条件：

1. 扫描计时器到期。
2. 玩家移动到新方块。
3. 玩家换世界/维度。
4. 基础生物群系温度变化超过阈值。
5. 配置 reload 后自然等待下一次刷新。

性能配置：

```yaml
performance:
  heat-scan-stationary-multiplier: 3
  heat-scan-biome-temp-threshold: 2.0
```

示例：基础扫描间隔 5 秒，玩家原地时可延长到 15 秒。

不做异步方块扫描，避免违反 Folia/Canvas 区域线程安全。

### 4.2 遮蔽缓存配置化

`ShelterDetector` 已有缓存字段，保留现有判定逻辑，只将缓存秒数配置化：

```yaml
performance:
  shelter-cache-seconds: 3
```

刷新条件：

1. 玩家移动到新方块。
2. 缓存过期。
3. 天气状态变化导致雨水暴露判断需要更新。
4. 配置 reload 后按下一轮 tick 自然刷新。

### 4.3 死亡保护

死亡保护期内不增长暴露压力，推荐只恢复压力：

```kotlin
recoverExposurePressure(state, tickIntervalSeconds)
```

这样玩家复活后不会立刻继承满压力继续恶化。

### 4.4 非极端短路

非极端环境中：

- 不计算完整保护分数。
- 恢复冷热压力。
- 体温仍按现有模型正常变化。
- 不套用极端暴露倍率。

### 4.5 水中稳定性

水中保留现有导热倍率，不直接降低 `water.thermal-conductivity`。通过暴露压力控制危险节奏：

```kotlin
if (player.isInWater) {
    gain *= TemperatureSettings.exposureWaterGainMultiplier
}
```

水中仍比空气危险，但不会入水后瞬间满压力。

---

## 5. 配置项汇总

新增到 `temperature.yml`：

```yaml
# =============================================================================
# 极端暴露压力配置
# =============================================================================
# 控制极端环境下体温恶化节奏：
# - 裸奔：30~60 秒进入明显危险
# - 有基础装备/遮蔽/热源：延长到 2~4 分钟

exposure:
  cold-threshold: 10.0
  heat-threshold: 8.0
  base-gain-per-second: 0.018
  min-gain-per-second: 0.0025
  recovery-per-second: 0.01
  min-extreme-multiplier: 0.35
  max-extreme-multiplier: 1.0
  water-gain-multiplier: 1.5
  block-protection-max: 0.4
  block-protection-full-modifier: 20.0

# =============================================================================
# 性能优化配置
# =============================================================================

performance:
  heat-scan-stationary-multiplier: 3
  heat-scan-biome-temp-threshold: 2.0
  shelter-cache-seconds: 3
```

`TemperatureSettings.kt` 新增字段并在 `reload()` 中 clamp：

```kotlin
var exposureColdThreshold: Double = 10.0
var exposureHeatThreshold: Double = 8.0
var exposureBaseGainPerSecond: Double = 0.018
var exposureMinGainPerSecond: Double = 0.0025
var exposureRecoveryPerSecond: Double = 0.01
var exposureMinExtremeMultiplier: Double = 0.35
var exposureMaxExtremeMultiplier: Double = 1.0
var exposureWaterGainMultiplier: Double = 1.5
var exposureBlockProtectionMax: Double = 0.4
var exposureBlockProtectionFullModifier: Double = 20.0
var heatScanStationaryMultiplier: Int = 3
var heatScanBiomeTempThreshold: Double = 2.0
var shelterCacheSeconds: Int = 3
```

约束：

- 速度类配置不小于 0。
- `exposureWaterGainMultiplier` 不小于 1。
- `exposureMaxExtremeMultiplier` 不小于 `exposureMinExtremeMultiplier`。
- `exposureBlockProtectionFullModifier` 大于 0。
- `heatScanStationaryMultiplier` 不小于 1。
- `shelterCacheSeconds` 不小于 1。

---

## 6. 代码边界

### 6.1 修改文件

| 文件 | 变更 |
|---|---|
| `RealWorldModels.kt` | 新增冷热压力与热源扫描缓存字段 |
| `TemperatureSettings.kt` | 新增暴露压力与性能配置读取 |
| `temperature.yml` | 新增配置与中文注释 |
| `TemperatureEngine.kt` | 接入压力曲线、热源扫描缓存、死亡保护恢复 |
| `ShelterDetector.kt` | 将遮蔽缓存秒数改为配置项 |

### 6.2 建议新增函数

在 `TemperatureEngine.kt` 中拆小函数，避免 `compute()` 继续膨胀：

```kotlin
private fun updateAndGetExposureMultiplier(...): Double
private fun recoverExposurePressure(...)
private fun calculateExposureSeverity(...): Double
private fun calculateExposureProtectionScore(...): Double
private fun shouldRefreshHeatSourceScan(...): Boolean
private fun updateHeatSourceScanCache(...)
```

### 6.3 不修改范围

本次不修改：

- `SurvivalEffectApplier.kt`
- `FrostOverlay.kt`
- `HeatOverlay.kt`
- `SurvivalHud.kt`
- 数据库存储结构
- 命令系统
- 天气/季节系统
- 温度抵抗附魔注册逻辑

如需显示暴露压力，后续另开 HUD 优化任务。

---

## 7. Folia/Canvas 线程安全

所有体温计算继续在现有 ticker 调用线程中执行。

明确禁止：

- 不异步读取 `player.location`。
- 不异步访问 `world.getBlockAt`。
- 不跨区域访问实体或方块。
- 不新增跨玩家共享的方块扫描可变缓存。

缓存只保存在 `PlayerEnvState` 内，属于玩家状态，不引入全局共享状态。

---

## 8. 验收设计

### 8.1 构建验证

实现完成后必须运行：

```bash
./gradlew build
```

### 8.2 数值场景

| 场景 | 预期 |
|---|---|
| 冬季雪原午夜裸奔 | 低温压力逐步上升，30~60 秒明显危险，不瞬间满压力 |
| 冬季雪原铁套 | 压力增长明显慢于裸奔，窗口达到分钟级 |
| 冬季雪原建筑 + 热源 | 压力大幅降低，环境回到非极端后压力恢复 |
| 夏季沙漠正午裸奔 | 高温压力逐步上升，30~60 秒明显危险 |
| 夏季沙漠高绝缘装备 | 压力增长变慢，但不完全免疫 |
| 冰水浸泡 | 水中仍危险，压力增长快于空气但保持平滑 |

### 8.3 性能场景

| 场景 | 预期 |
|---|---|
| 玩家原地不动 | 热源扫描间隔延长，扫描次数减少 |
| 玩家移动到新方块 | 热源扫描及时刷新 |
| 玩家换世界 | 热源扫描缓存强制失效 |
| 遮蔽状态原地不动 | 不频繁重复判定 |
| 进入/离开建筑 | 在缓存期内及时更新 |

### 8.4 稳定性场景

| 边界 | 预期 |
|---|---|
| 死亡保护期 | 暴露压力不增长，并逐步恢复 |
| 冷热环境切换 | 冷热压力互不污染 |
| 出入水 | 水中增长更快，出水后按当前环境继续增长或恢复 |
| 配置 reload | 新配置 clamp 正常，旧状态自然收敛 |
| 满绝缘 + 极端环境 | 不完全免疫，仍受最小增长约束 |

---

## 9. 实现优先级

1. **P0：暴露压力状态与配置**  
   新增冷热压力字段、配置读取和 clamp。

2. **P1：暴露压力接入体温变化**  
   在 `TemperatureEngine.compute()` 的最终变化前接入倍率，小函数拆分。

3. **P2：热源扫描与遮蔽缓存优化**  
   原地延长热源扫描间隔，遮蔽缓存秒数配置化。

4. **P3：人工场景验证**  
   按验收场景测试裸奔、装备、遮蔽、热源、水中、死亡保护和 reload。

---

## 10. 范围确认

本规格聚焦一个实现计划可覆盖的改动：**体温极端环境节奏与保守性能优化**。它不包含 HUD 新显示、不包含附魔系统重做、不包含数据库结构变化，也不新增自动化测试框架。后续如需要玩家可见的暴露压力提示，应单独设计。