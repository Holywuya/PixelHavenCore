# PlayerInfo 编辑模式与仓库按钮设计

日期：2026-06-26

## 概述

在现有 playerinfo 模块基础上新增两个能力：
1. 管理员可在 GUI 内直接编辑目标玩家的背包和末影箱（拿取/放入/移动），改动实时生效，支持在线和离线玩家
2. 仪表盘增加「个人仓库」按钮，复用 PlayerInv 模块查看目标玩家仓库

## 仪表盘布局变更

原布局（3 行 x 27 格）：

```
行1: [装饰玻璃板 ×9]
行2: [头像] [首次] [上次] [时长] [金币] [背包] [末影] [空] [空]
行3: [装饰玻璃板 ×9]
```

变更为在 slot 17 增加仓库按钮：

```
行1: [装饰玻璃板 ×9]
行2: [头像] [首次] [上次] [时长] [金币] [背包] [末影] [仓库] [空]
行3: [装饰玻璃板 ×9]
```

| 槽位 | 按钮 | 物品 | 说明 |
|------|------|------|------|
| 17 | 个人仓库 | Chest Minecart | 点击调用 PlayerInvService 打开目标玩家仓库 |

## 编辑模式

### 事件处理变更

当前 `onClick` 对所有会话类型一律拦截。变更后按 `SessionType` 区分：

| 会话类型 | 点击行为 |
|----------|----------|
| DASHBOARD | 全部拦截，仅 action 按钮生效（不变） |
| INVENTORY | 允许正常拿取/放入/移动；拦截 action 按钮和装饰玻璃板格子 |
| ENDER_CHEST | 同上 |

`onDrag` 同理：DASHBOARD 全部拦截，INVENTORY/ENDER_CHEST 仅拦截涉及保护格子的拖拽。

### 保护格子定义

- 携带 `phcore:playerinfo_action` PDC 标签的物品（返回按钮）
- 类型为 `GRAY_STAINED_GLASS_PANE` 的装饰物品

### 关闭时回写

`onClose` 对 INVENTORY / ENDER_CHEST 会话执行回写：

```
提取 GUI 中对应格位的物品内容 → 跳过保护格子
  → target.isOnline?
      YES → target.player.inventory.contents / enderChest.contents = items  （主线程直写）
      NO  → OfflineInventoryUtils.save(target, items) （异步 NBT 写入）
  → 清理 Session
```

## 离线玩家回写

`OfflineInventoryUtils` 新增 `save()` 方法：
- 读取玩家 `.dat` 文件，将物品序列化为 NBT 写入 `Inventory` / `EnderItems` 节点
- 异常处理：文件不存在/权限不足/数据损坏 → 日志警告 + 操作者提示
- 使用 `submit(async = true)` 避免阻塞主线程

## 仓库按钮对接 PlayerInv

- 点击仓库按钮 → `player.closeInventory()` → `PlayerInvService.openOtherAsync(viewer, target)`
- PlayerInv 别名 `/pi` 与 playerinfo 别名 `/pi` 冲突仅影响命令注册，GUI 内部调用不经过命令分发，无影响
- 调用前检查 `PlayerInvSettings.enabled`，模块未启用时提示操作者

## 边界情况

| 场景 | 处理方式 |
|------|----------|
| 在线目标玩家正打开自己背包 | 管理员写入直接覆盖，目标玩家下次交互可见变更 |
| 目标玩家在回写前上线 | 优先走在线路径直写，放弃离线 NBT 写入 |
| 回写失败 | 提示操作者「物品同步失败」，Session 保留不清理允许重试 |
| PlayerInv 模块未启用 | 检查 `PlayerInvSettings.enabled`，禁用时提示操作者 |

## 文件变更

| 操作 | 文件 |
|------|------|
| 修改 | `feature/playerinfo/PlayerInfoService.kt` — 事件处理、回写逻辑、仪表盘布局 |
| 修改 | `util/OfflineInventoryUtils.kt` — 新增 `save()` 方法 |
| 不改 | `playerinfo.yml` — 无需新增配置项 |
| 不改 | `PlayerInfoCommand.kt` — 命令逻辑无变化 |
| 不改 | `PlayerInfoSettings.kt` — 配置结构无变化 |

## 线程安全（Folia 合规）

- 在线玩家回写通过 `target.submitOnEntity {}` 在目标线程执行
- 离线 NBT 写入通过 `submit(async = true)` 在全局异步线程执行
- 回写期间不持有跨线程锁，仅用 Session Map 防止重复写入
- 编辑中的 GUI 操作保持在查看者线程

## 权限

- 沿用现有 `phcore.admin` 权限检查
- 编辑功能无需额外权限节点
- 不在 YAML 中定义权限字符串
