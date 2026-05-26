# Title 模块

称号系统模块，支持玩家拥有、装备、管理称号，并提供 GUI 选择界面和 PAPI 占位符。

## 功能概述

- **称号装备与卸下**
  - 玩家可装备已拥有的称号
  - 支持永久称号和限时称号
  - 过期称号自动失效并清理

- **GUI 界面**
  - 可视化称号选择界面
  - 支持按分类筛选（全部/分类）
  - 分页浏览，显示称号详情
  - 显示剩余时间、稀有度等信息

- **称号管理**
  - 管理员可发放/移除玩家称号
  - 支持设置有效期（永久/指定时长）
  - 自动过期检查与清理

- **扩展支持**
  - MiniMessage 格式（称号显示名）
  - CraftEngine 物品支持（称号图标）
  - PlaceholderAPI 占位符

## 指令

主命令：`/title`（别名：`/titles`、`/ch`）

| 指令 | 说明 | 权限 |
|------|------|------|
| `/title` | 显示帮助提示 | 所有玩家 |
| `/title open` | 打开称号选择界面 | 所有玩家 |
| `/title equip <id>` | 装备指定称号 | 所有玩家 |
| `/title unequip` | 卸下当前称号 | 所有玩家 |
| `/title list` | 查看拥有的称号 | 所有玩家 |
| `/title give <玩家> <id> [时长]` | 发放称号 (格式: 10d/10h/10m/10s/permanent) | `phcore.title.admin` |
| `/title take <玩家> <id>` | 移除称号 | `phcore.title.admin` |
| `/title reload` | 重载配置 | `phcore.title.admin` |

## 配置文件

### 主配置

路径：`feature/title/config.yml`

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `enabled` | 模块开关 | `true` |
| `papi.enabled` | 是否启用 PAPI 占位符 | `true` |
| `gui.title` | GUI 标题 (MiniMessage) | `<gradient:gold:yellow>称号系统</gradient>` |
| `gui.rows` | GUI 行数 (1-6) | `6` |
| `gui.border_item` | 边框材质 | `GRAY_STAINED_GLASS_PANE` |
| `expiry.check_interval_ticks` | 过期检查间隔 (tick) | `6000` (5分钟) |
| `permissions.admin` | 管理员权限 | `phcore.title.admin` |

### 称号定义

路径：`feature/title/titles/*.yml`

示例 `feature/title/titles/example.yml`：

```yaml
# 称号 ID（文件名，不含 .yml）
id: "vip"

# 显示名称（支持 MiniMessage 格式）
displayName: "<gold>VIP 玩家"

# 描述（每行一个字符串）
description:
  - "&7尊贵的 VIP 称号"
  - "&7拥有特殊权限"

# 图标（Bukkit 材质 或 CraftEngine 物品）
icon: "GOLDEN_HELMET"
# 或 CraftEngine 物品：
# icon: "ce:your_plugin:vip_helmet"

# 分类（用于 GUI 筛选）
category: "专属"

# 稀有度（仅展示）
rarity: "史诗"

# 权限（装备时检查，空则不检查）
permission: "group.vip"
```

## 主要代码结构

| 文件 | 说明 |
|------|------|
| `TitleSettings.kt` | 配置读取与消息模板 |
| `TitleService.kt` | 核心逻辑、过期检查 |
| `TitleStorage.kt` | 数据存储（MultipleHandler） |
| `TitleMenu.kt` | GUI 界面 |
| `TitleCommand.kt` | 命令入口 |
| `TitleListener.kt` | 事件监听（玩家加入/退出） |
| `TitleDefinitionLoader.kt` | 称号定义加载器 |
| `TitleModels.kt` | 数据模型（TitleDefinition、PlayerTitleEntry 等） |
| `TitlePlaceholders.kt` | PAPI 占位符 |

## 数据存储

### 存储方式

使用 TabooLib `MultipleHandler`（PlayerDatabase 容器），支持 MySQL 和 SQLite。

### 数据表

| 表名 | 说明 |
|------|------|
| `title_player_data` | 玩家称号数据 |

### 存储字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `active_title` | String | 当前装备的称号 ID |
| `owned_titles` | String (JSON) | 拥有的称号列表（含获取时间、过期时间） |

### 数据模型

```kotlin
// 称号定义（从 titles/*.yml 加载）
data class TitleDefinition(
    val id: String,
    val displayName: String,
    val description: List<String>,
    val icon: String,
    val category: String,
    val rarity: String,
    val permission: String,
    val craftEngineDisplay: String?,
    val sourcePath: String,
)

// 玩家称号记录
data class PlayerTitleEntry(
    val titleId: String,
    val obtainedAt: Long,
    val expiresAt: Long,
) {
    val isPermanent: Boolean get() = expiresAt == 0L
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean
}

// 玩家称号状态
data class PlayerTitleState(
    val playerUuid: UUID,
    val playerName: String,
    val activeTitleId: String?,
    val ownedTitles: List<PlayerTitleEntry>,
    val updatedAt: Long,
)
```

## PAPI 占位符

当 `papi.enabled: true` 且服务器安装了 PlaceholderAPI 时可用，标识符为 `phcoretitle`。

| 占位符 | 说明 |
|----------|------|
| `%phcoretitle_active%` | 当前装备的称号显示名（无则显示配置的消息） |
| `%phcoretitle_active_raw%` | 当前装备的称号 ID |
| `%phcoretitle_count%` | 已拥有的有效称号数量 |
| `%phcoretitle_has_<id>%` | 是否拥有指定称号（true/false） |
| `%phcoretitle_category_<分类>` | 指定分类下的有效称号数量 |
| `%phcoretitle_rarity_<稀有度>` | 指定稀有度下的有效称号数量 |

## 注意事项

1. **Folia 线程安全**
   - 数据库操作在异步线程执行
   - GUI 操作在玩家实体调度器执行
   - 玩家查找使用 `onlinePlayers()` 快照，避免 `Bukkit.getPlayer()`

2. **称号过期**
   - 每 5 分钟（可配置）检查一次过期称号
   - 过期称号自动移出 `activeTitleId`
   - 玩家加入时自动清理过期称号

3. **称号定义**
   - 称号 ID 为文件名（不含 `.yml`）
   - 支持 MiniMessage 格式（需要相应依赖）
   - CraftEngine 图标需要服务器安装 CraftEngine

4. **权限检查**
   - 装备称号时检查称号定义的 `permission` 字段
   - 管理员命令需要 `phcore.title.admin` 权限
   - 普通玩家命令默认所有人均可使用（`permissionDefault = TRUE`）
