# World 世界管理模块

世界管理与跨世界传送功能，支持预加载世界、按需加载缺失世界、自传送与传送他人。

## 功能概述

- **世界预加载**
  - 服务器启动时自动加载配置中列出的世界
  - 通过 `worlds.preload` 配置需要预加载的世界列表

- **按需加载**
  - 当 `worlds.loadMissing` 启用时，传送命令可自动加载未预加载的世界
  - 关闭时仅允许传送到已配置的世界

- **世界别名**
  - `worlds.default` 定义默认世界，同时作为 `allWorldNames()` 的一部分参与别名解析
  - 传送命令只允许传送到 `allWorldNames()` 中列出的世界（除非 `allowUnlistedTeleport` 启用）

- **传送功能**
  - `/world teleport <世界>` — 传送到指定世界出生点
  - `/world teleport <世界> <玩家>` — 将目标玩家传送到指定世界出生点
  - 传送使用 `teleportAsync` 确保 Folia 兼容

- **手动加载**
  - `/world load <世界>` — 管理员手动加载指定世界

## 命令

| 命令 | 说明 | 权限 |
|------|------|------|
| `/world` | 查看帮助与模块状态 | 所有玩家 |
| `/world list` | 列出已配置世界及其加载状态 | 所有玩家 |
| `/world teleport <世界> [玩家]` | 传送自己或他人到指定世界 | `phcore.world.teleport.self` / `phcore.world.teleport.other` |
| `/world load <世界>` | 手动加载指定世界 | `phcore.world.admin` |
| `/world reload` | 重载模块配置 | `phcore.world.admin` |

命令别名：`/mfw`

## 权限节点

| 权限 | 默认 | 说明 |
|------|------|------|
| `phcore.world.admin` | OP | 管理员权限（重载、手动加载） |
| `phcore.world.teleport.self` | 所有玩家 | 自传送权限 |
| `phcore.world.teleport.other` | OP | 传送他人权限 |

## 配置文件

路径：`feature/world.yml`

- `enabled` — 模块总开关
- `worlds.default` — 默认世界名称
- `worlds.preload` — 需要预加载的世界列表
- `worlds.loadMissing` — 是否允许按需加载未配置的世界
- `worlds.allowUnlistedTeleport` — 是否允许传送到未在列表中的世界
- `permissions.*` — 各项权限节点自定义
- `messages.*` — 所有提示消息自定义，支持颜色代码

## 主要代码结构

- `WorldSettings.kt`
  - 配置读取、`reload()`、世界名称解析与校验

- `WorldService.kt`
  - 世界加载、传送核心逻辑，`init()` / `reload()` / `stop()` 生命周期

- `WorldCommand.kt`
  - `/world` 命令及子命令定义，权限校验与消息发送
