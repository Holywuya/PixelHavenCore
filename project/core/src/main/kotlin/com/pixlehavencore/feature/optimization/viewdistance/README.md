# ViewDistance 模块

动态视距控制模块，提供默认视距、AFK 视距、动态 MSPT 调节与 Ping 调节。

## 功能概述

- **基础视距控制**
  - 玩家加入时应用默认视距
  - 可选同步 simulation distance

- **AFK 机制**
  - 根据无移动时长判定 AFK
  - AFK 状态降低视距并提示

- **动态调节**
  - 可按服务器 MSPT 阈值降低视距
  - 可按 Ping 阈值降低视距

- **无玩家偏好持久化**
  - 当前实现为纯配置驱动，不写玩家偏好数据库

## 指令

主命令：`/viewdistance`（别名：`/vd`）

- `/vd get`
  - 查看当前生效视距

- `/vd set <距离>`
  - 设置当前玩家视距

- `/vd reset`
  - 重置为默认视距

- `/vd reload`
  - 重载视距配置
  - 权限：`phcore.viewdistance.admin`

## 配置文件

路径：`feature/optimization/view-distance-controller.yml`

- `enabled`
  - 模块总开关

- `defaultDistance` / `minDistance` / `maxDistance`
  - 基础视距边界

- `afk.*`
  - AFK 逻辑配置

- `dynamic.*`
  - MSPT 动态调节配置

- `ping.*`
  - Ping 动态调节配置

## 主要代码结构

- `ViewDistanceSettings.kt`
  - 配置读取与阈值映射解析

- `ViewDistanceService.kt`
  - 调度与视距应用核心逻辑

- `ViewDistanceListener.kt`
  - 加入/退出/移动/传送事件

- `ViewDistanceCommand.kt`
  - 命令入口
