# Notification 模块

服务器通知与广播模块，提供管理员通知、自动公告与上下线通知。

## 功能概述

- **自动通知**
  - 按固定间隔轮播公告
  - 支持运行时开关与间隔修改

- **管理员通知**
  - 管理员可发送全服、同世界或半径范围通知

- **事件通知**
  - 玩家加入/离开提示
  - 服务器重启预告模板支持

## 指令

主命令：`/notification`（别名：`/notify`、`/servernotify`）

- `/notify send <消息>`
  - 发送管理员通知

- `/notify auto on|off|status`
  - 控制自动通知开关与状态

- `/notify auto interval <时间>`
  - 设置自动通知间隔（如 `30s`、`5m`、`1h`）

- `/notify reload`
  - 重载通知配置

- `/notify test`
  - 发送测试通知

## 配置文件

路径：`feature/notification.yml`

- `enabled`
  - 模块总开关

- `autoNotifications.*`
  - 自动公告间隔与内容

- `adminNotifications.*`
  - 管理员通知权限、范围、格式

- `eventNotifications.*`
  - 加入/离开/重启通知

- `messages.*`
  - 命令反馈文案

## 主要代码结构

- `NotificationSettings.kt`
  - 配置读取与时间格式解析

- `NotificationService.kt`
  - 自动任务与通知分发逻辑

- `NotificationCommand.kt`
  - 指令入口
