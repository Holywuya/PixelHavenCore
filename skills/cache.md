# 缓存与一致性规范

## 缓存层次

- L1：进程内内存缓存（`ConcurrentHashMap`）
- L2：Redis（可选）
- 持久层：MySQL/SQLite（最终落盘）

## 设计建议

- 明确缓存 key 结构：`module:user:field`。
- 区分“缺失缓存”与“空值缓存”。
- 高并发读写用原子更新（`compute` / `Atomic*`）。

## 失效策略

- reload 时清空模块缓存。
- quit 时清理玩家缓存。
- 写成功后更新缓存；写失败要保留脏标记并重试。

## Redis 约束

- Redis 仅做缓存/分发，不替代最终持久化。
- 网络操作必须异步。
- 订阅线程与主线程逻辑解耦，主线程仅做 Bukkit API 调用。

## JSON 与缓存载荷

- Redis/缓存载荷的 JSON 读写统一使用 `ArimJsonUtils`（Arim GsonUtils 封装）。
- 泛型解析场景通过 `ArimJsonUtils.gson()` + `TypeToken`，不要在模块内重复创建 Gson 实例。
