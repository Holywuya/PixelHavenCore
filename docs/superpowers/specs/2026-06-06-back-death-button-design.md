# /back 死亡回归 + 聊天按钮 设计文档

**日期**: 2026-06-06
**状态**: 已确认
**范围**: 简化 /back 命令（仅记录死亡、去掉冷却、死亡时发聊天按钮、传后删记录）

---

## 1. 目标

重构 `/back` 命令：只记录死亡位置，去掉冷却时间，死亡时发送可点击的聊天按钮，传送后删除记录而非永久存储。

## 2. 行为变更

| 维度 | 旧 | 新 |
|---|---|---|
| 记录来源 | PlayerDeathEvent + PlayerTeleportEvent | **仅 PlayerDeathEvent** |
| 数据生命周期 | 永久存储 | **/back 传送后立即删除** |
| 冷却 | 30 秒 | **无冷却** |
| 预热 + 安全位置 | 保持不变 | **保持不变** |
| 死亡时消息 | 无 | **聊天框显示可点击按钮** |

## 3. 聊天按钮

死亡后发送：

```
&c你已死亡！ &a[点击此处返回死亡位置]
```

使用 Adventure Component 的 `ClickEvent.runCommand("/back")` + `HoverEvent.showText("点击回到死亡点")`，通过 `TextBridge.sendMessage(Player, Component)` 直接发送（不用 `toLegacy`，避免丢失交互事件）。

## 4. 文件变更

| 文件 | 操作 |
|---|---|
| `BackService.kt` | 移除 `PlayerTeleportEvent` 记录逻辑的方法调用影响；移除冷却检查；传送后删除 BackStorage 数据 |
| `BackSettings.kt` | 移除 `cooldownSeconds`、`msgCooldown`；新增 `msgDeathButton`、`msgDeathHover` |
| `BaseListener.kt` | 移除 `onPlayerTeleport` 中 BackService.record 调用；死亡监听改为调用 BackService 的发消息+记录 |
| `base-command.yml` | 移除 `cooldownSeconds`、`msgCooldown`；新增 `msgDeathButton`、`msgDeathHover` |
| `BackStorage.kt` | 无需改动（remove 方法已存在） |

## 5. BackService 核心逻辑

```kotlin
// 死亡监听回调：记录位置 + 发按钮
fun handleDeath(player: Player) {
    if (!BackSettings.enabled) return
    record(player.uniqueId, player.location, "death")
    sendDeathButton(player)
}

// 传送回去：无冷却检查
fun teleportBack(player: Player): Boolean {
    // 检查启用、预热进行中
    val data = getBackData(uuid)
    // 预热/传送
    // 传送成功后：BackStorage.remove(uuid)
}
```

## 6. 配置变更

```yaml
# base-command.yml back 节
back:
  enabled: true
  warmupSeconds: 3
  cancelOnMove: true
  cancelOnDamage: true
  unsafeTeleport: false
  # 新增
  msgDeathButton: "&c你已死亡！ &a[点击此处返回死亡位置]"
  msgDeathHover: "&a点击回到死亡点"
  # 保留
  msgNoLocation: "&c没有可返回的位置。"
  msgWarmupStarting: "&a将在 {time} 秒后传送... 请勿移动"
  msgWarmupCancelled: "&c传送已取消！"
  msgTeleported: "&a已传送到上一个位置。"
  msgAlreadyWarmingUp: "&c传送预热中，请稍候。"
  # 移除: cooldownSeconds, msgCooldown
```

## 7. 不实现

- 不记录传送位置（仅死亡）
- 无冷却
- 传送后数据不保留
