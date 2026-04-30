# TabooLib 模块安装速记

用于避免再次混淆 TabooLib DSL 安装名、别名常量与真实模块名。

## 当前项目基线

- TabooLib Gradle Plugin：`2.0.36`
- TabooLib Core 版本：`6.3.0-88720d8`

## 推荐写法（Kotlin DSL）

- 优先使用 `io.izzel.taboolib.gradle.*` 导入后的常量写法，例如：`install(AlkaidRedis)`。
- 常量写法与字符串写法等价，但常量更不易拼错。

## Redis 相关模块映射（本项目版本）

- `install(AlkaidRedis)` = `install("database-alkaid-redis")`
- `install(DatabasePlayerRedis)` 会包含：
  - `database-player-redis`
  - `database-player`
  - `database`
  - `database-alkaid-redis`
  - `basic-configuration`

## 易错点

- 在当前版本映射中，未发现 `database-alkaid-redis-tool` 对应的 DSL 常量或模块映射。
- 因此本项目不要写 `install("database-alkaid-redis-tool")`，避免构建期/运行期不一致。

## 需要核对时的最小流程

1. 先看 `build.gradle.kts` 当前 `taboolib { version { ... } }`。
2. 用 `javap` 检查 `taboolib-gradle-plugin` 的 `ModuleNameKt` 映射。
3. 以当前插件版本映射为准，不凭记忆套用其他版本文档。

## 本项目结论（2026-04）

- 当前 Redis 标准实现基于 `database-alkaid-redis`。
- 代码侧对应 API 命名空间：`taboolib.expansion.AlkaidRedis`、`taboolib.expansion.SingleRedisConnection`。

## Arim 工具集备注

- 当前项目 JSON/文件夹读取统一工具由 `Arim` 提供（`top.maplex.arim:Arim:1.3.12`）。
- 文件夹读取：`top.maplex.arim.tools.folderreader.*`
- Gson 工具：`top.maplex.arim.tools.gson.GsonUtils`
