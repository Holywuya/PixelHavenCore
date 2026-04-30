# 命令与权限规范

## 结构约定

- 每模块至少提供：主命令 + `reload` 子命令。
- 命令类命名：`XxxCommand.kt`。
- 权限校验统一使用 `requirePermission()`。

## 全局重载纳管

- 模块 `reload()` 必须可独立调用。
- 必须纳入 `/phc reload` 统一流程。
- 涉及 Bukkit API 的 reload 步骤标记为主线程执行。

## 文案约定

- 帮助文案包含用途与权限信息。
- 失败文案可定位问题，不吞异常。

## 安全约定

- 管理命令默认 `phcore.<module>.admin`。
- 玩家命令与管理员命令权限分离。
