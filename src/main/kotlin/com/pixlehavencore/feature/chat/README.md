# SimpleChat 模块

聊天与私聊模块，按 SimpleChat 语义迁移，提供聊天格式、私聊回复、AT 提及与跨服聊天能力。

## 功能概述

- **聊天格式化**
  - 支持前缀、名称、分隔符与消息内容组合
  - 支持 MiniMessage、旧 hover/click 语法与 PlaceholderAPI 占位符

- **私聊体系**
  - `/msg` 私聊
  - `/reply` 快速回复最近私聊对象
  - 支持私聊双方不同消息格式

- **AT 提及与提示音**
  - 聊天中 `@玩家` 自动匹配并替换格式
  - 提及玩家可播放提示音并带冷却

- **消息增强**
  - 链接识别并转成可点击组件
  - 数字识别并支持点击复制

- **跨服聊天（Redis）**
  - 支持 Redis 发布/订阅跨服聊天
  - 可配置 click/hover 过滤策略

- **异步处理**
  - Redis 初始化、重载、发布异步执行
  - `ignorePlaceholderApi=true` 时，玩家等级文件异步预热缓存

## 指令

- `/chat`（别名：`/simplechat`、`/sc`）
  - `/chat reload` 重载聊天配置
  - `/chat help` 查看帮助
  - `/chat about` 查看模块信息

- `/msg <玩家> <消息>`（别名：`/message`、`/tell`、`/w`）
  - 发送私聊消息

- `/reply <消息>`（别名：`/r`）
  - 回复最近私聊对象

## 配置文件

- `feature/chat/chat.yml`
  - 聊天主配置（格式、提及、检测、Redis、权限）
  - `format`、`nameFormat`、`privateMessage.*`、`sayCommand.*`、`at.format`、`messageSeparator` 支持 MiniMessage

- `feature/chat/chat-messages.yml`
  - 命令提示、错误信息、按钮文本

## 主要代码结构

- `SimpleChatSettings.kt`
  - 主配置读取与 `reload()`

- `SimpleChatMessages.kt`
  - 文案配置读取

- `SimpleChatService.kt`
  - 聊天格式组装、模块生命周期

- `SimpleChatListener.kt`
  - 主聊天事件处理

- `SimpleChatMsgCommand.kt` / `SimpleChatReplyCommand.kt` / `SimpleChatCommand.kt`
  - 聊天命令实现

- `SimpleChatRedisService.kt`
  - Redis 发布/订阅与消息转发
