# 数据库与玩家数据规范

## 总原则

- 玩家动态数据必须使用 TabooLib PlayerDatabase 容器。
- 禁止将玩家动态数据写入 YAML/JSON/本地文件。
- 仅非玩家实体业务才评估手写 SQL 表。

## 推荐入口

- 常规容器：`DatabaseUtils.newPlayerDataHandler(table, ...)`
- Redis 缓存容器：`DatabaseUtils.newRedisPlayerDatabaseHandler(table)`

## 读写策略

- 读：优先缓存/容器，避免主线程磁盘/数据库 IO。
- 写：优先异步写入，批量写可走定时 flush。
- 玩家进服：按需 preload。
- 玩家离服：flush + 清缓存，防止泄漏。

## 表与字段建议

- key 命名统一 `module:key` 或 `prefix:key`。
- 动态 key 避免与固定字段冲突。
- 使用 `version` + 迁移逻辑保证兼容。

## 禁忌

- 不要在主线程循环中直接数据库查询。
- 不要新增 `HikariDataSource + 手写 SQL` 存玩家个人数据。
