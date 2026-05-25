# Security 模块

安全查看模块，提供管理员查看在线玩家背包与末影箱的只读界面。

## 功能概述

- **背包查看**
  - 管理员可打开在线玩家背包镜像

- **末影箱查看**
  - 管理员可打开在线玩家末影箱镜像

- **只读保护**
  - 拦截点击与拖拽，防止修改目标容器内容

## 指令

主命令：`/security`

- `/security inv <玩家>`
  - 查看玩家背包

- `/security ec <玩家>`
  - 查看玩家末影箱

- `/security reload`
  - 重载安全模块配置

## 配置文件

路径：`feature/security.yml`

- `enabled`
  - 模块总开关

- `titles.*`
  - 查看界面标题模板

## 主要代码结构

- `SecuritySettings.kt`
  - 配置读取与 `reload()`

- `SecurityService.kt`
  - 打开只读界面与事件拦截

- `SecurityCommand.kt`
  - 命令入口
