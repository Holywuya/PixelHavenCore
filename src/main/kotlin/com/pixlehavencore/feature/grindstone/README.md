# Grindstone 模块

砂轮修复模块，允许玩家使用配置材料对装备进行耐久修复。

## 功能概述

- **砂轮交互修复**
  - 右键砂轮触发修复判定
  - 支持权限与潜行限制

- **规则化材料匹配**
  - 支持按主手物品 + 副手材料定义修复规则
  - 支持默认材料兜底匹配（未配置规则时）

- **修复数值控制**
  - 固定值修复与百分比修复
  - 成功率控制

## 指令

主命令：`/grindstone`（别名：`/grindrepair`）

- `/grindstone reload`
  - 重载砂轮修复配置
  - 权限：`phcore.grindstone.admin`

## 配置文件

路径：`feature/grindstone-repair.yml`

- `grindstoneRepair.enabled`
  - 模块总开关

- `grindstoneRepair.requireSneak`
  - 是否要求潜行交互

- `grindstoneRepair.permission`
  - 使用权限节点

- `grindstoneRepair.rules`
  - 修复规则定义（主物品、材料、修复量）

- `grindstoneRepair.messages.*`
  - 成功/失败提示

## 主要代码结构

- `GrindstoneRepairSettings.kt`
  - 配置读取、规则匹配、修复量计算

- `GrindstoneRepairListener.kt`
  - 砂轮交互事件处理

- `GrindstoneRepairCommand.kt`
  - 重载命令入口
