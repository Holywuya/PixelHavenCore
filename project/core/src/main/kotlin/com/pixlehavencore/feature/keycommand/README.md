# KeyCommand 模块

按键触发命令模块，基于玩家换手事件实现快捷执行。

## 功能概述

- **按键触发**
  - 监听玩家换手事件（默认 F 键行为）
  - 支持普通 F 与 Shift+F 配置命令

- **触发约束**
  - 仅在玩家未打开容器界面时触发
  - 支持触发冷却，避免高频刷指令

## 指令

- 当前无独立业务命令。
- 模块配置可通过全局重载 `/phc reload` 生效。

## 配置文件

路径：`feature/key-command.yml`

- `enabled`
  - 模块总开关

- `f` / `shiftF`
  - 对应按键触发命令

- `cooldownMillis`
  - 按键触发冷却（毫秒）

## 主要代码结构

- `KeyCommandSettings.kt`
  - 配置读取与 `reload()`

- `KeyCommandService.kt`
  - 触发判定与命令执行

- `KeyCommandListener.kt`
  - 监听换手事件
