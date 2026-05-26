# PlayerInv 模块

仓库与共享仓库模块，支持个人仓库存取、共享仓协作与管理面板。

## 功能概述

- **个人仓库**
  - 玩家可打开个人仓库
  - 支持按权限决定仓库行数
  - 支持管理员查看与设置容量

- **共享仓库**
  - 创建、打开、升级共享仓库
  - 成员管理（添加/移除/查看）
  - 支持公开/私有可见性切换

- **管理界面与聊天输入**
  - GUI 管理入口
  - 聊天输入添加/删除成员

- **数据存储**
  - 玩家个人数据：TabooLib PlayerDatabase 容器
  - 共享仓元数据：独立业务表
  - 大量读写操作采用异步任务处理

## 指令

 主命令：`/playerinv`（别名：`/pi`）

- `/playerinv`
  - 打开自己的仓库

- `/playerinv open <玩家>`
  - 管理员打开目标玩家仓库

- `/playerinv size <玩家> <大小>`
  - 管理员设置个人仓库容量

- `/playerinv shared ...`
  - 共享仓库子命令（create/open/add/remove/members/upgrade/quota/admin-open/owner）

- `/playerinv reload`
  - 重载仓库模块配置

## 配置文件

 路径：`feature/playerinv.yml`

- `enabled`
  - 模块总开关

- `personal.*`
  - 个人仓库容量与权限规则

- `shared.*`
  - 共享仓库初始容量、升级成本、管理面板配置

- `database.*`
  - 数据表名配置

- `messages.*`
  - 提示消息配置

## 主要代码结构

- `PlayerInvSettings.kt`
  - 配置读取与消息模板

- `PlayerInvService.kt`
  - 仓库核心逻辑、存储、GUI 会话管理

- `PlayerInvListener.kt`
  - 背包事件与聊天输入监听

- `PlayerInvCommand.kt`
  - 仓库命令入口
