# Skills 索引

本目录用于沉淀跨模块复用的实现规范。涉及通用能力时，优先查阅对应文档。

## 文档导航

- `database.md`：PlayerDatabase 容器、表设计边界、读写策略
- `cache.md`：本地缓存、Redis 缓存、一致性与失效策略
- `async.md`：异步任务、Folia 调度变体、主线程回切、onDisable 收口与 init/reload 统一实践
- `commands.md`：命令结构、权限规范、重载纳管约定
- `papi.md`：PlaceholderExpansion 规范、命名与容错
- `module-template.md`：模块 README 与四件套组织模板
- `taboolib-modules.md`：TabooLib 模块安装映射与 Redis 模块速记
- `arim-toolkit.md`：Arim 工具集统一接入规范（FolderReader/GsonUtils）

## 使用规则

- 新增功能前先查本目录并按规范实现。
- 若现有实现与文档不一致，优先以线上稳定与安全为准，再补文档。
