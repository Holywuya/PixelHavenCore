# RealWorld Ticker 与 Effect 收敛重构设计

## 背景

RealWorld 模块已经完成第一轮重构：

- `RealWorldService` 中的游戏事件处理已拆分到 `RealWorldEvents`
- `RealWorldSettings` 中的委托属性膨胀已消除

但当前模块仍存在两个明显问题：

1. `RealWorldService` 仍然硬编码顺序调用多个 Engine 的 `tick()` / `compute()` / `applyEffects()` 方法，调度逻辑集中且扩展成本高。
2. 玩家效果施加逻辑仍散落在 `TemperatureEngine`、`ThirstEngine`、`FractureEngine`、`StaminaEngine` 与 `SurvivalEffectApplier` 中，职责边界不清晰。

这次重构聚焦于统一 tick 循环模式，并将所有玩家效果收敛到单一出口，同时保持现有玩法行为与执行顺序不变。

## 目标

1. 为全局与玩家两类 tick 建立清晰、可扩展的统一接口。
2. 让 `RealWorldService` 只保留编排、调度、dirty 判断、HUD 刷新与生命周期管理职责。
3. 让各 Engine 只负责状态计算与状态变更，不再分散直接操作玩家表现层。
4. 让 `SurvivalEffectApplier` 成为玩家效果的唯一输出入口。
5. 在不改变核心玩法顺序的前提下，降低后续新增子系统的接入成本。

## 非目标

本次重构不包含以下内容：

- 不调整 `RealWorldStorage` 的持久化结构
- 不调整 `RealWorldCommand`、Placeholder、配置文件格式
- 不重构数据库访问模型
- 不在本轮引入新的玩法规则或数值平衡改动
- 不强制将所有子系统合并进单一 ticker 接口

## 设计决策

### 1. 双接口模型

本次使用两套 ticker 接口，而不是将所有 tick 逻辑硬塞进同一抽象：

```kotlin
interface GlobalSubsystemTicker {
    fun tick(global: GlobalEnvState, dt: Int, context: GlobalTickContext)
}

interface PlayerSubsystemTicker {
    fun tick(player: Player, state: PlayerEnvState, global: GlobalEnvState, dt: Int)
}
```

原因：

- `SeasonEngine`、`WeatherEngine` 处理的是全局状态，不适合伪装成玩家级 tick。
- `TemperatureEngine`、`ThirstEngine`、`FractureEngine`、`StaminaEngine` 明显属于玩家级状态推进。
- 双接口能保留语义清晰度，避免一个抽象同时承载两种不同职责。

### 2. Service 保留编排职责

`RealWorldService` 不会被彻底削成空壳，而是保留以下职责：

- 构造全局与玩家 tick 的执行上下文
- 维护 `globalTickers` 与 `playerTickers`
- 负责统一调度顺序
- 负责 dirty 判断
- 负责 HUD 刷新
- 负责生命周期、任务、存储交互

`ticker` 只负责描述“本轮 tick 应调用哪些领域逻辑”，不负责持久化、生命周期或 UI 编排。

### 3. 效果统一收敛

`SurvivalEffectApplier` 将成为玩家效果唯一输出点，统一负责：

- 药水效果
- 玩家直接伤害
- 行走速度调整
- 禁止疾跑
- 粒子效果
- 即时提示（如 action bar 级别反馈）

这意味着：

- `TemperatureEngine` 保留温度状态计算，但不再直接承担最终玩家效果输出职责
- `ThirstEngine` 保留 hydration / phase 计算，但不再输出干渴惩罚效果
- `FractureEngine` 保留 fracture 数值推进、severity 判定、治疗和跌落骨折值变化，但不再直接调整 walkSpeed / sprint 或恢复提示
- `StaminaEngine` 保留 stamina 消耗恢复与 phase 判定，但不再直接输出速度、疾跑和药水惩罚

### 4. 允许彻底重组 Engine API

本次允许对现有 `compute()` / `tick()` / `applyEffects()` 进行彻底重组。目标不是给旧 API 做一层薄适配，而是明确拆出：

- 状态计算 / 状态推进
- 效果输出
- ticker 编排

但这种重组必须遵守一个约束：**现有执行顺序与主要玩法表现保持不变**。

## 接口与上下文设计

### GlobalTickContext

`GlobalTickContext` 第一版只包含全局 ticker 之间确实需要共享、又不适合塞进 Service 的编排信息：

```kotlin
class GlobalTickContext(
    val onlinePlayers: List<Player>,
)
```

第一版不提前加入更多字段，避免上下文膨胀。后续若出现新的全局协调需求，再按需扩展。

## Tick 执行顺序

### 全局 tick 顺序

每次全局调度时：

1. 构造 `GlobalTickContext(onlinePlayers)`
2. 依次执行：
   - `SeasonTicker`
   - `WeatherTicker`
3. 更新 `dayPhase`
4. 同步原版天气
5. 标记全局 dirty
6. 生成 `globalSnapshot`

这里必须保持 `Season -> Weather` 的顺序，因为天气权重本来依赖季节。

### 玩家 tick 顺序

每个玩家的 entity 线程内，保持固定顺序：

1. `TemperatureTicker`
2. `ThirstTicker`
3. `FractureTicker`
4. `StaminaTicker`
5. `FoodCorrosionTicker`
6. `SurvivalEffectTicker`

顺序原因：

- 温度 / 口渴先更新基础生存状态
- 骨折 / 体力在此基础上推进阶段与数值
- 食物腐蚀独立处理库存相关状态
- 所有效果最后统一施加，确保看到的是本轮最新状态

### dirty 判断与 HUD 刷新

dirty 判断和 HUD 刷新继续留在 `RealWorldService`：

- ticker 不负责决定是否存盘
- ticker 不直接刷新 HUD
- `SurvivalHud.renderCurrentThread()` 在所有 player ticker 完成后调用

这使得玩家 tick 心智模型变成：

> 先算状态 → 再统一施加效果 → 最后由 Service 决定是否存盘、是否刷新 HUD

## 类与文件结构

### 新增类型

建议新增：

- `feature/realworld/tick/GlobalSubsystemTicker.kt`
- `feature/realworld/tick/PlayerSubsystemTicker.kt`
- `feature/realworld/tick/GlobalTickContext.kt`

### 新增 ticker 实现

建议新增：

- `feature/realworld/tick/global/SeasonTicker.kt`
- `feature/realworld/tick/global/WeatherTicker.kt`
- `feature/realworld/tick/player/TemperatureTicker.kt`
- `feature/realworld/tick/player/ThirstTicker.kt`
- `feature/realworld/tick/player/FractureTicker.kt`
- `feature/realworld/tick/player/StaminaTicker.kt`
- `feature/realworld/tick/player/FoodCorrosionTicker.kt`
- `feature/realworld/tick/player/SurvivalEffectTicker.kt`

### 改造的现有类

- `RealWorldService`
  - 改为维护 ticker 列表并统一调度
- `SurvivalEffectApplier`
  - 扩展为唯一效果出口
- `TemperatureEngine`
- `ThirstEngine`
- `FractureEngine`
- `StaminaEngine`
  - 收缩为“状态计算 / 状态变更”职责
- `WeatherEngine`
- `SeasonEngine`
  - 被 global ticker 调用，继续负责全局状态计算

## 迁移策略

本次按分阶段迁移，避免一次性大改动：

### 阶段 1：引入接口与上下文

- 新增 `GlobalSubsystemTicker`、`PlayerSubsystemTicker`、`GlobalTickContext`
- 不改现有业务规则

### 阶段 2：改造 `RealWorldService` 调度骨架

- 将硬编码的顺序调用替换为 ticker 列表遍历
- 保持调度顺序、dirty 判断、HUD 刷新逻辑不变

### 阶段 3：抽出 global tickers

- 引入 `SeasonTicker`、`WeatherTicker`
- 保持 `Season -> Weather` 顺序不变

### 阶段 4：抽出 player tickers

- 引入 `TemperatureTicker`、`ThirstTicker`、`FractureTicker`、`StaminaTicker`、`FoodCorrosionTicker`
- 先做等价迁移，不改变内部计算规则

### 阶段 5：统一效果出口

- 引入 `SurvivalEffectTicker`
- 将温度 / 口渴 / 天气 / 骨折 / 体力相关效果逐步迁入 `SurvivalEffectApplier`
- 删除各 Engine 中重复或散落的直接玩家效果操作

## 风险与控制

### 风险 1：tick 顺序变化导致行为漂移

例如：

- stamina 读取的是旧的还是新的 fracture / temperature 状态
- effect 在状态更新前还是更新后应用

控制方式：

- 迁移时严格保持当前顺序不变
- 先替换“调度写法”，再调整职责归属

### 风险 2：效果收敛后优先级冲突

尤其是：

- `FractureEngine` 与 `StaminaEngine` 都会影响 walkSpeed / sprint
- 多个系统都可能叠加药水效果

控制方式：

- 在 `SurvivalEffectApplier` 中定义唯一优先级规则
- 不再允许多个 Engine 直接争用表现层

### 风险 3：阶段提示重复或丢失

例如：

- 每 tick 重复发消息
- 原本只在阶段变化时触发的提示变成持续触发

控制方式：

- 阶段变化判定仍保留在最接近状态变更的地方
- `SurvivalEffectApplier` 只负责执行效果，不重新推断阶段跃迁

## 回归验证

### 第一层：编译构建

- `./gradlew build`

### 第二层：行为回归

重点验证：

- 季节推进是否正常
- 天气推进是否正常
- 温度 / 口渴 / 骨折 / 体力数值是否仍变化
- HUD 是否仍刷新
- 极端天气、严重体温、严重缺水、体力不足、骨折时效果是否仍生效

### 第三层：边界场景

重点验证：

- 玩家上下线
- reload 后 task 是否重复
- dirty 缓存是否仍能正确保存
- entity thread 内执行是否没有越界访问

## 完成定义

当以下条件全部满足时，本轮重构视为完成：

1. `RealWorldService` 不再硬编码逐个调用各 Engine 的 tick
2. 存在清晰的 `GlobalSubsystemTicker` / `PlayerSubsystemTicker`
3. 所有玩家效果只从 `SurvivalEffectApplier` 输出
4. 各 Engine 只负责状态计算 / 状态变更
5. 现有执行顺序与主要玩法表现不变
6. `./gradlew build` 通过
7. 至少完成一次手动行为回归检查
