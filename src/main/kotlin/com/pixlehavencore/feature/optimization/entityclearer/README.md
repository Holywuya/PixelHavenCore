# EntityClearer 模块

定时实体清理模块，按周期扫描区块并清理低价值实体。

## 功能概述

- **周期清理**
  - 按配置间隔触发清理周期
  - 清理任务分批执行，避免单 tick 压力过大

- **倒计时广播**
  - 清理前按配置秒数发送 ActionBar 提示

- **清理对象控制**
  - 可分别开关掉落物与怪物清理
  - 怪物清理默认跳过有自定义名称实体

## 指令

主命令：`/entityclearer`（别名：`/eclear`）

- `/entityclearer reload`
  - 重载实体清理配置
  - 权限：`phcore.entityclearer.admin`

## 配置文件

路径：`feature/optimization/entity-clearer.yml`

- `enabled`
  - 模块总开关

- `scan-interval-seconds`
  - 清理周期（秒）

- `countdown-seconds` / `countdown-message`
  - 倒计时提示配置

- `items.enabled` / `mobs.enabled`
  - 清理对象开关

## 主要代码结构

- `EntityClearerSettings.kt`
  - 配置读取与 `reload()`

- `EntityClearerService.kt`
  - 调度、分批扫描与清理执行

- `EntityClearerCommand.kt`
  - 重载命令入口

- `EntityClearerListener.kt`
  - 预留监听器
