# YAML 配置文件写法统一规范

## 概述

将项目全部 26 个 YAML 配置文件的命名、引号、注释、缩进统一为一致的规范。同时更新对应的 Kotlin 配置类代码以匹配新的键名。

## 规范细则

### 1. 键名命名 — kebab-case

所有键名统一使用 `kebab-case`（小写字母 + 连字符）。

| 改前风格 | 示例 | 改后 |
|----------|------|------|
| camelCase | `defaultCurrency` | `default-currency` |
| snake_case | `bar_color` | `bar-color` |
| 不统一的时间单位后缀 | `autoSaveTicks` / `check-interval-ticks` | 统一为风格一致的 kebab |

### 2. 字符串值引号 — 一律不加（例外见下）

字符串值不加双引号。**仅以下情况保留引号：**

- 含 `&` 颜色代码（如 `"&6[税收]"`）
- 含 `{placeholder}` 占位符（如 `"{amount}"`）
- 含冒号 `:` 或特殊 YAML 字符
- 含前导/后置空格
- 值为空字符串 `""`

| 改前 | 改后 |
|------|------|
| `type: "sqlite"` | `type: sqlite` |
| `material: "BARRIER"` | `material: BARRIER` |
| `tax-base: "square"` | `tax-base: square` |
| `message: "&6[税收]..."` | 不变（含颜色代码） |

### 3. 数字值引号 — 一律不加

所有数字值不加引号。YAML 原生支持 int/float 类型。

| 改前 | 改后 |
|------|------|
| `port: "3306"` | `port: 3306` |
| `expected-balance: "10000"` | `expected-balance: 10000` |
| `buffer-multiplier: "2.0"` | `buffer-multiplier: 2.0` |
| `dormant-recovery-rate: "0.01"` | `dormant-recovery-rate: 0.01` |
| `max-negative-reserve: "-1"` | `max-negative-reserve: -1` |

**例外：** 纯数字作为映射键时（如 `"45": 1`），引号保留——YAML 语法要求。

### 4. 注释规范

#### 4.1 注释位置 — 上方注释

注释统一在键值对**上方独占一行**，不用行内注释（`#` 跟在值后）。

```yaml
# 改前（行内注释 — veinminer.yml）
cooldown: 20 # 触发冷却（tick）

# 改后（上方注释）
# 触发冷却（tick）
cooldown: 20
```

#### 4.2 总开关统一措辞

```
# 功能开关（true = 启用，false = 禁用）
enabled: true
```

#### 4.3 子模块开关统一措辞

```
# 是否启用 XXX（true = 启用，false = 禁用）
enabled: true
```

#### 4.4 文件头模板

```yaml
version: 1

# =============================================================================
# 模块名称
# =============================================================================
# 功能说明：
#   - 功能点 1
#   - 功能点 2

# 功能开关（true = 启用，false = 禁用）
enabled: true
```

分隔线 77 个 `=`（与现有主流文件一致）。

### 5. 缩进规范 — 2 空格

- 所有嵌套层级使用 **2 个空格**缩进
- 列表项缩进同样 2 空格：`  - item`

| 文件 | 变更 |
|------|------|
| `flight.yml` | 列表缩进 3 → 2 空格 |

### 6. 列表格式 — 块列表

列表统一用**块列表**（每项独立一行），不用内联 `[a, b, c]`。

| 文件 | 变更 |
|------|------|
| `notification.yml:64` | `[60, 30, 15, 5, 1]` → 块列表 |
| `title/config.yml:19` | `[7, 17, 26, 35, 44]` → 块列表 |

```yaml
# 改前
category-slots: [7, 17, 26, 35, 44]

# 改后
category-slots:
  - 7
  - 17
  - 26
  - 35
  - 44
```

### 7. 列表项字符串引号

与规则 2 一致：纯单词不加引号，含特殊字符才加。

| 文件 | 变更 |
|------|------|
| `base/protection.yml` | `- "FROG"` → `- FROG` |
| `flight.yml` | `- sky-overworld` 不变（已无引号） |

## 代码同步变更

键名改为 kebab-case 后，对应的 Kotlin 配置类需同步更新：

- **TabooLib `@Config` 注解**：属性名与 YAML 键名需匹配
- **`@ConfigComment` 注释**：同步调整措辞
- 涉及 `config/` 包下的配置类，以及各功能模块中直接读取配置的代码

**受影响的文件类别：**
- `mm-healthbar.yml` → snake_case 改为 kebab-case，需更新 `MmHealthbarConfig.kt`
- `title/config.yml` → 同上，需更新 `TitleConfig.kt`
- `economy.yml`、`central-bank.yml`、`tax.yml` → camelCase 改 kebab-case，需更新经济模块配置类
- `grindstone-repair.yml`、`view-distance-controller.yml`、`death-drop.yml` 等 → 同上

## 完整文件清单（26 个）

### 需改键名 + 引号 + 注释的文件
| 文件 | 键名变更量 | 字符串去引号 | 注释调整 |
|------|-----------|-------------|----------|
| `settings.yml` | 少量 | 端口去引号 | 措辞统一 |
| `feature/veinminer.yml` | 无 | 无 | 行内→上方 |
| `feature/vanish.yml` | 无 | 无 | 措辞统一 |
| `feature/grindstone-repair.yml` | camelCase→kebab | 有 | 措辞统一 |
| `feature/flight.yml` | 无 | 有 | 缩进+措辞 |
| `feature/face-trade.yml` | 无 | 无 | 措辞统一 |
| `feature/mm-healthbar.yml` | snake_case→kebab | 少数 | 措辞统一 |
| `feature/playerinv.yml` | camelCase→kebab | 有 | 分隔线+措辞 |
| `feature/playerinfo.yml` | 无 | 无 | 措辞统一 |
| `feature/key-command.yml` | 无 | 有 | 措辞统一 |
| `feature/player-state.yml` | 无 | 无 | 措辞统一 |
| `feature/playtime.yml` | camelCase→kebab | 无 | 措辞统一 |
| `feature/notification.yml` | 无 | 列表→块列表 | 措辞统一 |
| `feature/death-drop.yml` | camelCase→kebab | 无 | 措辞统一 |
| `feature/economy/economy.yml` | camelCase→kebab | 少量 | 措辞统一 |
| `feature/economy/central-bank.yml` | 少量 | 数字去引号 | 措辞统一 |
| `feature/economy/tax.yml` | 少量 | 有 | 措辞统一 |
| `feature/crafting-bench/config.yml` | 无 | 无 | 措辞统一 |
| `feature/crafting-bench/recipes/bandage.yml` | 无 | 无 | — |
| `feature/title/config.yml` | snake_case→kebab | 有 | 分隔线+列表+措辞 |
| `feature/title/titles/example.yml` | 无 | 无 | — |
| `feature/base/protection.yml` | 无 | 有 | 补注释 |
| `feature/base/killme.yml` | 无 | 无 | 补注释 |
| `feature/base/back.yml` | 无 | 无 | 补注释 |
| `feature/optimization/entity-clearer.yml` | 少量 | 无 | 措辞统一 |
| `feature/optimization/view-distance-controller.yml` | camelCase→kebab | 无 | 措辞统一 |

## 不在范围

- 不改变配置文件的功能逻辑和默认值
- 不修改 `feature/title/titles/example.yml` 和 `feature/crafting-bench/recipes/bandage.yml`（示例文件保持现状）
- 不重构配置类结构，仅同步键名
- 不修改代码注释（只改 YAML 注释和 Kotlin 配置类属性名/注释）
