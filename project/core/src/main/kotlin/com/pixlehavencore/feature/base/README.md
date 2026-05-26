# Base 模块

基础行为与限制模块，负责基础命令和部分实体行为拦截。

## 功能概述

- **基础命令**
  - 提供 `/killme` 自杀命令
  - 支持模块开关与提示消息

- **实体与世界保护**
  - 苦力怕爆炸保护（可选仅清方块破坏，或直接取消爆炸）
  - 末地/下界指定实体传送门与跨维传送拦截
  - 区块加载时清理指定实体（下界/末地）

## 指令

主命令：`/killme`

- `/killme`
  - 让玩家自杀
  - 受 `feature/base-command.yml` 的 `enabled` 控制

## 配置文件

路径：`feature/base-command.yml`

- `enabled`
  - 基础模块总开关

- `messages.suicide`
  - 自杀成功提示

- `creeperProtect.*`
  - 苦力怕爆炸保护相关配置

- `portalProtection.*`
  - 传送门实体拦截和跨维清理配置

## 主要代码结构

- `BaseCommandSettings.kt`
  - 读取配置、提供 `reload()`

- `BaseCommand.kt`
  - 命令入口实现

- `BaseListener.kt`
  - 世界与实体事件拦截
