# SpawnReducer 模块

自然生成削减模块，对符合条件的生物自然生成按比例取消。

## 功能概述

- **按原因过滤**
  - 仅对配置的自然生成原因生效
  - 不影响刷怪蛋、命令生成等非自然来源

- **概率削减**
  - 按 `reduction-percent` 概率取消生成
  - 支持 0~100% 范围

## 指令

主命令：`/spawnreducer`（别名：`/sreduce`）

- `/spawnreducer reload`
  - 重载自然生成削减配置
  - 权限：`phcore.spawnreducer.admin`

## 配置文件

路径：`feature/optimization/spawn-reducer.yml`

- `enabled`
  - 模块总开关

- `reduction-percent`
  - 生成取消概率

- `natural-reasons`
  - 参与削减的生成原因列表

## 主要代码结构

- `SpawnReducerSettings.kt`
  - 配置读取与原因解析

- `SpawnReducerService.kt`
  - 取消判定逻辑

- `SpawnReducerListener.kt`
  - 生物生成事件监听

- `SpawnReducerCommand.kt`
  - 重载命令入口
