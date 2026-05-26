# Veinminer 模块

矿物连锁挖掘模块，支持按方块链路批量破坏与次数限制。

## 功能概述

- **连锁挖掘**
  - 以起始方块为中心按半径搜索同矿类型方块
  - 支持最大连锁数量与工具校验

- **使用限制**
  - 支持每日次数限制与固定时间重置
  - 支持按权限组定义上限

- **掉落与耐久**
  - 支持合并掉落
  - 支持工具耐久损耗

- **Placeholder 支持**
  - 提供剩余次数、总上限、已使用、重置倒计时

## 指令

主命令：`/veinminer`（别名：`/vm`）

- `/veinminer toggle`
  - 切换连锁挖掘开关（管理员）

- `/veinminer reload`
  - 重载连锁配置（管理员）

- `/veinminer limit`
  - 查看当前玩家连锁限制信息

- `/veinminer add <玩家> <次数>`
  - 增加目标玩家的剩余次数（管理员）

- `/veinminer remove <玩家> <次数>`
  - 减少目标玩家的剩余次数（管理员）

- `/veinminer set <玩家> <次数>`
  - 直接设置目标玩家的剩余次数（管理员）

## PlaceholderAPI 变量

标识符：`veinminer`

- `%veinminer_remaining%`
  - 剩余次数

- `%veinminer_limit%`
  - 总上限

- `%veinminer_used%`
  - 已使用次数

- `%veinminer_reset_seconds%`
  - 下次重置剩余秒数

## 配置文件

路径：`feature/veinminer.yml`

- `enabled`
  - 模块总开关

- `maxChain` / `searchRadius`
  - 连锁规模配置

- `allowedBlocks` / `allowedTools`
  - 生效方块与工具白名单

- `limit.*`
  - 次数限制与重置时间

- `groups.*`
  - 权限组限制配置

## 主要代码结构

- `VeinminerSettings.kt`
  - 配置读取、权限组解析

- `VeinminerLimitService.kt`
  - 次数存储与重置调度

- `VeinminerService.kt`
  - 连锁搜索、破坏逻辑

- `VeinminerListener.kt`
  - 方块破坏事件监听

- `VeinminerCommand.kt`
  - 命令入口

- `VeinminerPlaceholders.kt`
  - PlaceholderAPI 扩展
