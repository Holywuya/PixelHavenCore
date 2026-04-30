# PAPI 占位符规范

## 目标

- 统一 PlaceholderExpansion 的实现方式、命名规则和容错行为。
- 保证占位符查询轻量、稳定、可观测。

## 实现入口

- 优先使用 TabooLib 的 `taboolib.platform.compat.PlaceholderExpansion`。
- 由框架自动扫描注册，不额外手写 Bukkit 原生注册流程。

## 命名规范

- `identifier` 统一使用 `phcore<module>`（如 `phcoretax`、`phcorechat`）。
- 参数名使用小写下划线：`pending_tax`、`next_settle_seconds`。
- 禁止在同模块重复定义同名参数。

## 性能与线程边界

- `onPlaceholderRequest` 中禁止直接做 DB/文件/网络 IO。
- 占位符只读取内存态或已缓存数据；重计算放异步任务提前准备。
- 返回值转换保持 O(1) 或接近 O(1)，避免遍历大集合。

## 容错与返回约定

- 未识别参数返回空字符串 `""`，不要抛异常。
- 玩家上下文缺失时返回可预期兜底值（如 `"0"`、`"false"`、`"-"`）。
- 关键异常必须记录上下文日志（参数名、玩家、模块）。

## 文档同步要求

- 新增占位符后，必须同步更新模块 README 的“PAPI 变量”章节。
- 若涉及配置项（格式化、开关、刷新间隔），必须同步更新对应 `feature/*.yml`。
- 配置结构变化需递增 `version` 并提供迁移兼容逻辑。

## 最小实现清单

- 新建 `XxxPlaceholders.kt` 并放在模块目录下。
- 在 `when(args.lowercase())` 中集中维护所有参数映射。
- 为每个参数提供稳定兜底返回值。
- 补充 README 示例：`%phcorexxx_example%`。
