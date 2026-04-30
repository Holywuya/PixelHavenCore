# 异步与主线程边界

## 强制规则

- IO/DB/网络/批量扫描必须异步。
- Bukkit 主线程 API（玩家消息、背包、世界实体）仅主线程执行。

## 推荐模式

- 后台执行：`submit(async = true) { ... }` 或 `submitAsync { ... }`
- 回主线程：`submit { ... }`
- 数据流：异步计算 -> 主线程提交结果

## Folia 调度变体

- 基础调度：`submit()` 负责通用任务调度，支持同步/异步、延迟、重复执行。
- 异步快捷：`submitAsync()` 等效于 `submit(async = true)`。
- `Location.submit()` / `Location.runTask()`：在指定位置使用 `RegionScheduler` 调度或立即执行。
- `Entity.submit()` / `Entity.runTask()`：在实体所在位置使用 `EntityScheduler` 调度或立即执行。
- `Block.submit()` / `Block.runTask()`：委托到方块所在位置的 `Location` 调度。
- `Chunk.submit()` / `Chunk.runTask()`：在区块中心位置调度或立即执行。
- `World.submit(x, z)` / `World.runTask(x, z)`：在指定世界坐标调度或立即执行。

### 参数说明

- `now: Boolean = false`：是否立即执行。
- `async: Boolean = false`：是否异步执行。
- `delay: Long = 0`：延迟 tick 数。
- `period: Long = 0`：重复 tick 数。
- `useScheduler: Boolean = true`：非 Folia 环境下是否使用调度器。

### 约束

- 涉及 Bukkit / 实体 / 区块 / 玩家状态的操作，优先绑定到对应对象的调度入口。
- 纯计算、IO、DB、网络请求继续使用异步调度，避免阻塞区域线程。
- 立即执行只用于确定需要同步落在当前对象上下文的短逻辑。

## 线程安全

- 共享状态用 `ConcurrentHashMap`、`Atomic*` 或 `synchronized`。
- `synchronized` 仅保护临界区，不代表异步。

## 常见坑

- 在异步线程直接调用 Bukkit 实体/背包 API。
- 在主线程等待长耗时 Future。
- reload 时忘记取消任务导致重复调度。

## 关服约束（onDisable）

- onDisable 阶段尽量不再新建异步任务。
- 需要落盘/收尾时优先同步收口（先停任务，再同步 flush，再释放资源）。
- 长期后台任务需提供 `stop()/shutdown()`，由主插件在 `onDisable` 明确调用。

## 模块 init/reload 约定

- 同一模块优先由 `Service.init()` 统一串起 `Settings.init()` + 运行时任务启动。
- `Service.reload()` 优先复用 `init()`（先停旧任务，再重启），避免 `Settings` 与 `Service` 双入口分叉。
