# 模块模板与落地清单

## 目标

- 统一 `feature/*` 模块组织方式，降低维护成本。
- 保证模块具备可重载、可观测、可文档化的最小闭环。

## 推荐目录结构

```text
feature/<module>/
  README.md
  <Module>Settings.kt
  <Module>Service.kt
  <Module>Command.kt
  <Module>Listener.kt
  <Module>Placeholders.kt        # 可选，若提供 PAPI
  <Module>Storage.kt             # 可选，若有状态/容器封装
```

## 四件套职责

- `Settings`
  - 只负责配置读取、默认值、`reload()`。
  - 不写业务逻辑，不持有重对象生命周期。
- `Service`
  - 承载核心业务逻辑与状态。
  - 明确线程边界：耗时逻辑异步，Bukkit API 主线程。
- `Command`
  - 负责参数解析、权限校验、调用 Service。
  - 至少提供管理员 `reload` 子命令。
- `Listener`
  - 负责事件监听与轻量路由。
  - 避免在监听器中堆积重逻辑，复杂流程下沉到 Service。

## 配置与资源规范

- 默认配置放 `src/main/resources/feature/<module>.yml`。
- 任何配置结构变更必须递增 `version`。
- 若存在用户可扩展 section，必须同步加入 `ConfigAlignService.DYNAMIC_SECTIONS` 白名单。

## 重载与生命周期

- 模块需提供 `init()` / `reload()`（或同等职责入口）。
- `reload()` 要做到幂等：重复调用不泄漏任务、不重复注册监听。
- 模块 `reload()` 必须纳入 `/phc reload` 全局流程。

## 数据与缓存约束

- 玩家动态数据必须走 PlayerDatabase 容器（见 `database.md`）。
- 缓存分层、失效与脏数据处理遵循 `cache.md`。
- 玩家进出服应显式初始化/释放相关缓存与容器引用。

## 并发与性能约束

- IO/DB/网络/批量扫描必须异步（见 `async.md`）。
- 主线程只做 Bukkit 交互和最终结果提交。
- 高频事件优先短路判断，避免无谓对象创建。

## 工具集统一

- 文件夹读取/资源目录遍历：统一使用 `ArimFolderUtils`（基于 Arim FolderReader）。
- JSON 序列化/反序列化：统一使用 `ArimJsonUtils`（基于 Arim GsonUtils）。
- 新增模块不得直接散落 `walkTopDown` + 手工后缀判断作为常规配置加载方案。
- 详细约定见 `arim-toolkit.md`。

## README 最低要求

- 模块用途与边界。
- 配置文件路径与关键配置说明。
- 指令列表与权限节点。
- 若有 PAPI：变量列表与示例。
- 线程模型说明：哪些逻辑异步、哪些逻辑主线程。

## 新模块落地检查单

- 已创建并接入 `Settings/Service/Command/Listener`。
- 已提供模块 `reload()`，并纳入 `/phc reload`。
- 已补齐资源配置与 `version` 字段。
- 已补齐模块 README。
- 已执行 `./gradlew.bat build` 并通过。
