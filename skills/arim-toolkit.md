# Arim 工具集统一规范

用于统一项目内“文件夹读写”和“JSON 序列化/反序列化”实现，避免模块各自实现导致行为不一致。

## 目标

- 文件夹配置扫描统一走 Arim `FolderReader`。
- JSON 处理统一走 Arim `GsonUtils`。
- 对外统一通过 `util` 层封装，业务模块不直接散落底层 API 调用。

## 当前约定

- 文件夹工具封装：`src/main/kotlin/com/pixlehavencore/util/ArimFolderUtils.kt`
- JSON 工具封装：`src/main/kotlin/com/pixlehavencore/util/ArimJsonUtils.kt`
- 资源扫描封装：`src/main/kotlin/com/pixlehavencore/util/ArimResourceScanner.kt`
- 发光工具封装：`src/main/kotlin/com/pixlehavencore/util/ArimGlowUtils.kt`

## FolderReader 规范

- 优先使用：
  - `ArimFolderUtils.walkYaml(folder, filter) { ... }`
  - `ArimFolderUtils.releaseAndWalkYaml(resourcePath, filter) { ... }`
- 默认读取类型统一为 YAML；如需 JSON/TOML 等，先在 util 扩展统一方法，再落业务模块。
- `walk` 闭包中的 `Configuration` 修改，仍需显式 `saveToFile()` 才会持久化。
- 过滤规则只做文件选择，不承担业务逻辑。
- 配置资源扫描（开发目录态）统一通过 `ArimResourceScanner`，避免模块直接 `walkTopDown()` 扫描。

## GsonUtils 规范

- 优先使用：
  - `ArimJsonUtils.toJson(value)`
  - `ArimJsonUtils.fromJson(json, clazz)`
  - `ArimJsonUtils.parseTree(json)`
  - `ArimJsonUtils.gson()`（仅在泛型 TypeToken 等场景）
- 模块层避免直接依赖 `com.google.gson.JsonParser` 与 `GsonUtils.getGson()`，统一通过 util 封装进入。

## Glow 规范

- 实体发光统一通过：
  - `ArimGlowUtils.setEntityGlowing(entity, receiver, color)`
  - `ArimGlowUtils.setEntityGlowingForAll(entity, color)`
- 业务模块不要直接散落 `Arim.glow.setGlowing(...)` 调用。
- 使用前需确认服务端已安装 PacketEvents（Glow 前置依赖）。

## 禁止事项

- 禁止在不同模块复制粘贴文件夹遍历实现（`walkTopDown + extension 判断`）作为长期方案。
- 禁止在新代码中直接引入多套 JSON 工具并行使用（例如模块 A 用 Gson 原生，模块 B 用 Arim GsonUtils）。

## 迁移建议（增量）

1. 先替换新增代码路径，避免继续扩大分叉。
2. 再按模块逐步迁移历史实现（每次只改一个模块，保证风险可控）。
3. 每次迁移后执行 `./gradlew.bat build`。

## 参考

- FolderReader 文档：
  - `https://taboolib.maplex.top/docs/expanding-technology/arim/folder-reader/`
- GsonUtils 文档：
  - `https://taboolib.maplex.top/docs/expanding-technology/arim/gson-utils/`
- Glow 文档：
  - `https://taboolib.maplex.top/docs/expanding-technology/arim/glow/`
