# MobDrop 模块

怪物自定义掉落模块，支持按实体类型或 MythicMob ID 配置掉落与金钱奖励。

## 功能概述

- **自定义掉落规则**
  - 按实体类型配置掉落物、概率、数量表达式
  - 支持按配置清空原版掉落

- **MythicMob 支持**
  - 优先按 MythicMob 内部 ID 匹配专属掉落
  - 支持基于 MythicMob 等级的 `{level}` 掉落公式

- **抢夺联动**
  - 根据抢夺等级应用概率乘数

- **金钱掉落**
  - 支持击杀者模式或范围分发模式
  - 支持范围平分或全额发放

## 指令

主命令：`/mobdrop`（别名：`/md`）

- `/mobdrop reload`
  - 重载怪物掉落配置
  - 权限：`phcore.mobdrop.admin`

## 配置文件

路径：`feature/mob-drop.yml`

- `enabled`
  - 模块总开关

- `looting-multiplier-by-level`
  - 抢夺等级概率乘数映射

- `drops.*`
  - 掉落规则与金钱掉落配置
  - `items` 新格式：`<spec> <amountExpr> <chanceExpr>`
  - `money` 新格式：`<chanceExpr> <minExpr> <maxExpr> <mode> [radius] [split]`
  - 表达式支持 `+ - * / ()` 与 `{level}`

## 主要代码结构

- `MobDropSettings.kt`
  - 配置解析与规则结构定义

- `MobDropListener.kt`
  - 实体死亡事件处理

- `MobDropCommand.kt`
  - 重载命令入口
