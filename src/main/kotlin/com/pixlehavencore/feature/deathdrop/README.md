# DeathDrop 模块

危险世界死亡保护模块，按每日次数保护玩家死亡不掉落，并支持管理员调整额外次数。

## 功能概述

- **死亡保护**
  - 指定世界内死亡时，若仍有保护次数则保留背包与经验
  - 保护触发时清空掉落并提示剩余次数

- **每日次数机制**
  - 基础每日保护次数 + 管理员发放的额外次数
  - 次数使用与额外次数按玩家维度独立记录

- **存储实现**
  - 使用 TabooLib PlayerDatabase 容器存储玩家每日使用数据
  - 异步初始化与异步写入

## 指令

主命令：`/deathdrop`（别名：`/ddrop`）

- `/deathdrop reload`
  - 重载配置与使用次数存储
  - 权限：`phcore.deathdrop.admin`

- `/deathdrop add <玩家> <次数>`
  - 增加玩家今日额外保护次数
  - 权限：`phcore.deathdrop.admin`

- `/deathdrop set <玩家> <次数>`
  - 直接设置玩家今日剩余保护次数
  - 权限：`phcore.deathdrop.admin`

## 配置文件

路径：`feature/death-drop.yml`

- `enabled`
  - 模块总开关

- `worlds`
  - 生效世界白名单

- `dailyKeepCount`
  - 每日基础保护次数

- `exemptPermission`
  - 免疫保护判定权限

- `keepMessage` / `outOfProtectionMessage`
  - 保护触发与次数耗尽提示

## 主要代码结构

- `DeathDropSettings.kt`
  - 配置读取与 `reload()`

- `DeathDropUsageStorage.kt`
  - 玩家次数存储与缓存

- `DeathDropListener.kt`
  - 玩家死亡事件处理

- `DeathDropCommand.kt`
  - 管理命令入口
