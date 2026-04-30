# Vanish 模块

隐身模块，支持普通隐身、隐身列表查看与管理员临时可见控制。

## 功能概述

- **普通隐身**
  - 玩家可切换隐身状态
  - 对无查看权限玩家隐藏
  - 应用隐身药水效果

- **管理员可见控制**
  - 可列出当前隐身玩家
  - 可临时显示指定隐身玩家或全部隐身玩家

- **状态管理**
  - 玩家上下线时清理可见映射状态

## 指令

- `/vanish`（别名：`/v`）
  - 切换自己的隐身状态

- `/vanish reload`
  - 重载隐身配置（管理员）

- `/vanish-list`（别名：`/vlist`）
  - 查看当前隐身玩家列表（管理员）

- `/vanish-show <player|--all>`（别名：`/vshow`）
  - 临时显示指定或全部隐身玩家（管理员）

## 配置文件

路径：`feature/vanish.yml`

- `enabled`
  - 模块总开关

- `messages.*`
  - 状态切换与管理提示文案

- `fakeMessages.*`
  - 伪造消息相关配置

## 主要代码结构

- `VanishSettings.kt`
  - 配置读取与 `reload()`

- `VanishService.kt`
  - 隐身状态管理与观察者可见控制

- `VanishListener.kt`
  - 玩家加入/退出相关处理

- `VanishCommand.kt`
  - 命令入口
