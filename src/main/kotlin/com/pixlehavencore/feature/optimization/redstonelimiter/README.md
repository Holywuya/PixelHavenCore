# 红石限制模块（RedstoneLimiter）

检测并阻断高频红石信号，防止因高频红石时钟/方块更新导致的服务器卡顿。

## 文件结构

| 文件 | 职责 |
|------|------|
| `RedstoneLimiterSettings.kt` | 配置读取与热重载 |
| `RedstoneLimiterService.kt` | 核心业务：频率追踪、阈值判定、阻断执行、通知广播、过期清理 |
| `RedstoneLimiterListener.kt` | Bukkit 事件监听：BlockRedstoneEvent / BlockPhysicsEvent |
| `RedstoneLimiterCommand.kt` | 管理员命令：reload / stats |
| `TrackKey.kt` | 位置键 data class（worldName + x/y/z） |
| `SlidingWindow.kt` | 滑动窗口频率追踪器 |
| `StatsSnapshot.kt` | 运行统计数据快照 |

## 配置项速查

| 配置路径 | 类型 | 默认值 | 说明 |
|----------|------|--------|------|
| `enabled` | Boolean | true | 模块总开关 |
| `enabled-worlds` | List\<String\> | [world, world_nether] | 生效世界列表 |
| `threshold-activations-per-second` | Int | 20 | 每秒激活次数阈值 |
| `window-seconds` | Int | 5 | 滑动窗口时长 |
| `max-tracked-points` | Int | 1500 | 最大追踪点数（内存上限） |
| `cleanup-interval-seconds` | Int | 60 | 过期清理周期 |
| `notify.enabled` | Boolean | true | OP 通知开关 |
| `notify.cooldown-seconds` | Int | 10 | 同位置通知冷却 |
| `notify.message` | String | (见配置) | 通知消息模板 |
| `additional-block-types` | List\<String\> | [] | 额外监听方块类型 |

## 命令与权限

| 命令 | 权限 | 说明 |
|------|------|------|
| `/redstonelimiter` | - | 显示帮助 |
| `/redstonelimiter reload` | `phcore.redstonelimiter.admin` | 重载配置 |
| `/redstonelimiter stats` | `phcore.redstonelimiter.admin` | 查看运行统计 |

## 红石方块类型判定

默认监听以下 Material 类型（硬编码集合）：
- 红石粉、中继器、比较器、红石火把（地上/墙上）
- 绊线钩、绊线、阳光传感器、拉杆、侦测器
- 活塞、粘性活塞
- 所有按钮族（Stone/Oak/Spruce/Birch/Jungle/Acacia/DarkOak/Mangrove/Cherry/Bamboo/PolishedBlackstone/Crimson/Warped）
- 所有压力板族（Stone/Oak/Spruce/Birch/Jungle/Acacia/DarkOak/Mangrove/Cherry/Bamboo/PolishedBlackstone/Crimson/Warped/LightWeighted/HeavyWeighted）
- 幽匿感测体、标定幽匿感测体

通过 `additional-block-types` 配置项可扩展此集合。

## 性能与内存约束

- **单次检测开销**：均摊 O(1)，远低于 0.05ms 红线
- **内存占用**：最大追踪点 1500 × 约 3.2KB/窗口 ≈ 4.8MB < 5MB 红线
- **清理策略**：定期清理 + 紧急回收（超 1500 追踪点时淘汰最久未活跃的 10%）

## Folia 线程模型适配

- **Listener 同步执行**：`@SubscribeEvent` 不使用 async，确保 `setCancelled()` 在事件调度线程
- **ConcurrentHashMap**：tracker/notifyCooldowns 支持跨区域线程并发访问
- **异步通知**：OP 广播通过 `submit(async = true)` + `submitOnEntity` 在实体线程发送
- **清理任务**：`submit(period = ..., async = true)` 在全局调度器执行
