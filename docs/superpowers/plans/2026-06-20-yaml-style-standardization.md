# YAML 配置文件写法统一实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将项目全部 26 个 YAML 配置文件及对应 Kotlin 配置类的命名、引号、注释、缩进统一为一致规范。

**架构：** TabooLib `@Config` 不做自动键名映射——YAML 键名和 Kotlin 属性的桥接完全靠 `reload()` 方法中硬编码的 `config.getString("key", ...)` 字符串。因此改 YAML 键名时必须同步修改对应 Kotlin 代码中的字符串字面量。

**技术栈：** Kotlin, TabooLib 6.x, SnakeYAML

---

### 任务 1：mm-healthbar — snake_case → kebab-case

**文件：**
- 修改：`project/core/src/main/resources/feature/mm-healthbar.yml`
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/mmhealthbar/MMHealthBarSettings.kt`

- [ ] **步骤 1：修改 YAML 键名**

将 `mm-healthbar.yml` 中的 snake_case 键名改为 kebab-case。需要读取文件确认当前键名后批量替换：

| 改前 (snake_case) | 改后 (kebab-case) |
|---|---|
| `bar_color` | `bar-color` |
| `bar_style` | `bar-style` |
| `title_format` | `title-format` |
| `damage_format` | `damage-format` |
| `remove_delay_ticks` | `remove-delay-ticks` |
| `update_interval_ticks` | `update-interval-ticks` |

- [ ] **步骤 2：修改 Kotlin reload() 中的字符串字面量**

在 `MMHealthBarSettings.kt` 的 `reload()` 方法中，将所有 `config.getXXX("snake_case_key", ...)` 中的字符串字面量改为 kebab-case：

```
// 改前
config.getString("bar_color", "PURPLE")
config.getString("bar_style", "PROGRESS")

// 改后
config.getString("bar-color", "PURPLE")
config.getString("bar-style", "PROGRESS")
```

同理修改所有 `bar_color` → `bar-color`, `bar_style` → `bar-style`, `title_format` → `title-format`, `damage_format` → `damage-format`, `remove_delay_ticks` → `remove-delay-ticks`, `update_interval_ticks` → `update-interval-ticks`。

- [ ] **步骤 3：验证构建**

```bash
./gradlew :project:core:compileKotlin
```

预期：BUILD SUCCESSFUL，零警告。

- [ ] **步骤 4：Commit**

```bash
git add project/core/src/main/resources/feature/mm-healthbar.yml project/core/src/main/kotlin/com/pixlehavencore/feature/mmhealthbar/MMHealthBarSettings.kt
git commit -m "refactor(config): mm-healthbar 键名 snake_case → kebab-case"
```

---

### 任务 2：title/config — snake_case → kebab-case

**文件：**
- 修改：`project/core/src/main/resources/feature/title/config.yml`
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/title/TitleSettings.kt`

- [ ] **步骤 1：修改 YAML 键名**

将 `title/config.yml` 中的 snake_case 键名改为 kebab-case。需要读取文件确认所有键名后批量替换。已知键名包括：`border_item`, `border_accent`, `category_slots`, `title_start_slot`, `page_size`, `prev_page_slot`, `info_slot`, `next_page_slot`, `active_indicator`, `expired_indicator`, `available_indicator`, `locked_indicator`, `rarity_colors`, `default_title_enabled`, `default_title_id`, `default_title_auto_equip`, `expiry_check_ticks`，以及所有 `msg_*` 消息键。

- [ ] **步骤 2：同步标题注释**

将文件头改为标准模板（添加 `# =============================...` 分隔线，统一措辞）。

- [ ] **步骤 3：修改 Kotlin reload() 中的字符串字面量**

在 `TitleSettings.kt` 的 `reload()` 方法中，将所有 `config.getXXX("snake_case_key", ...)` 的字符串字面量同步改名。

- [ ] **步骤 4：内联列表 → 块列表**

将 `category_slots: [7, 17, 26, 35, 44]` 改为块列表格式。

- [ ] **步骤 5：验证构建**

```bash
./gradlew :project:core:compileKotlin
```

预期：BUILD SUCCESSFUL。

- [ ] **步骤 6：Commit**

```bash
git add project/core/src/main/resources/feature/title/config.yml project/core/src/main/kotlin/com/pixlehavencore/feature/title/TitleSettings.kt
git commit -m "refactor(config): title 键名 snake_case → kebab-case，内联列表改块列表"
```

---

### 任务 3：economy 模块 — 键名 + 引号统一

**文件：**
- 修改：`project/core/src/main/resources/feature/economy/economy.yml`
- 修改：`project/core/src/main/resources/feature/economy/tax.yml`
- 修改：`project/core/src/main/resources/feature/economy/central-bank.yml`
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/economy/EconomySettings.kt`
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/economy/TaxSettings.kt`
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/economy/CentralBankSettings.kt`

- [ ] **步骤 1：economy.yml — 键名与引号**

将 camelCase 键名改为 kebab-case，同时去掉不需要的字符串引号。读取文件确认所有键名。

主要变更：
- `defaultCurrency` → `default-currency`
- `autoSaveTicks` → `auto-save-ticks`（或检查 `storage.autoSaveTicks` 嵌套路径）

- [ ] **步骤 2：tax.yml — 键名与引号**

读取 tax.yml 当前内容（注意：任务开始前已修复缩进问题），检查组名是否需要调整：
- `use-marginal-rate` → 已 kebab-case，不变
- `default-tax-rate` → 已 kebab-case，不变
- `check-interval-ticks` → 已 kebab-case，不变
- `pool-persist-interval-ticks` → 已 kebab-case，不变
- 注释措辞统一（`# 功能开关（true = 启用，false = 禁用）`）

- [ ] **步骤 3：central-bank.yml — 数字去引号**

将所有数字值去掉引号：
- `expected-balance: "10000"` → `expected-balance: 10000`
- `buffer-multiplier: "2.0"` → `buffer-multiplier: 2.0`
- `dormant-recovery-rate: "0.01"` → `dormant-recovery-rate: 0.01`
- `max-negative-reserve: "-1"` → `max-negative-reserve: -1`
- 注释措辞统一

- [ ] **步骤 4：同步 EconomySettings.kt reload()**

修改 `config.getXXX("旧键名", ...)` 字符串字面量。

- [ ] **步骤 5：同步 TaxSettings.kt reload()**

检查组名是否有变化。大部分键名已是 kebab-case，主要是引号相关和注释措辞调整，Kotlin 代码可能不需要改动——确认后决定。

- [ ] **步骤 6：同步 CentralBankSettings.kt reload()**

检查数字类型配置的读取方式（`getInt` vs `getString`）。去掉引号后如果 YAML 中值为纯数字，Kotlin 中改用 `getInt/getDouble` 读取更准确。这是可选的类型改进。

- [ ] **步骤 7：验证构建**

```bash
./gradlew :project:core:compileKotlin
```

预期：BUILD SUCCESSFUL。

- [ ] **步骤 8：Commit**

```bash
git add project/core/src/main/resources/feature/economy/ project/core/src/main/kotlin/com/pixlehavencore/feature/economy/
git commit -m "refactor(config): economy 模块键名统一 + 数字去引号"
```

---

### 任务 4：grindstone-repair — camelCase → kebab-case

**文件：**
- 修改：`project/core/src/main/resources/feature/grindstone-repair.yml`
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/grindstone/GrindstoneRepairSettings.kt`

- [ ] **步骤 1：修改 YAML 键名**

读取文件确认所有 camelCase 键名，改为 kebab-case。已知包括：`requireSneak` → `require-sneak` 等。

- [ ] **步骤 2：同步 Kotlin 代码**

在 `GrindstoneRepairSettings.kt` 的 `reload()` 中同步修改 `config.getXXX("camelCaseKey", ...)` 字符串字面量。

- [ ] **步骤 3：验证构建** → 预期 BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add project/core/src/main/resources/feature/grindstone-repair.yml project/core/src/main/kotlin/com/pixlehavencore/feature/grindstone/GrindstoneRepairSettings.kt
git commit -m "refactor(config): grindstone-repair 键名 camelCase → kebab-case"
```

---

### 任务 5：death-drop + playtime — camelCase → kebab-case

**文件：**
- 修改：`project/core/src/main/resources/feature/death-drop.yml`
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/deathdrop/DeathDropSettings.kt`
- 修改：`project/core/src/main/resources/feature/playtime.yml`
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/playtime/PlaytimeSettings.kt`

- [ ] **步骤 1：death-drop.yml 键名 + Kotlin 同步**

`dailyKeepCount` → `daily-keep-count`, `exemptPermission` → `exempt-permission`, `graveExpireSeconds` → `grave-expire-seconds` 等。同步修改 `DeathDropSettings.kt` 中的 `config.getXXX(...)` 字符串字面量。

- [ ] **步骤 2：playtime.yml 键名 + Kotlin 同步**

`autoSaveTicks` → `auto-save-ticks`, `papiEnabled` → `papi-enabled`, `defaultFormat` → `default-format`, `leaderboardMaxLimit` → `leaderboard-max-limit` 等。同步修改 `PlaytimeSettings.kt`。

- [ ] **步骤 3：验证构建** → 预期 BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add project/core/src/main/resources/feature/death-drop.yml project/core/src/main/kotlin/com/pixlehavencore/feature/deathdrop/DeathDropSettings.kt project/core/src/main/resources/feature/playtime.yml project/core/src/main/kotlin/com/pixlehavencore/feature/playtime/PlaytimeSettings.kt
git commit -m "refactor(config): death-drop + playtime 键名 camelCase → kebab-case"
```

---

### 任务 6：playerinv — camelCase → kebab-case

**文件：**
- 修改：`project/core/src/main/resources/feature/playerinv.yml`
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/playerinv/PlayerInvSettings.kt`

> 注意：playerinv 是最大的配置文件，约 200+ 行，70+ 个配置项。

- [ ] **步骤 1：修改 YAML 键名**

读取文件确认所有键名，逐一改为 kebab-case。涉及包括：`defaultRows` → `default-rows`, `maxRows` → `max-rows`, `sharedInitialRows` → `shared-initial-rows` 等大量键名。

- [ ] **步骤 2：修改分隔线长度**

将 `# ============================================================`（60 个 `=`）改为 `# =============================================================================`（77 个 `=`）。

- [ ] **步骤 3：去除不必要的字符串引号**

如 `"BARRIER"` → `BARRIER`, `"pi"` → `pi` 等。注意：命令字符串如 `"playerinv shared open public"`（含空格）保留引号。

- [ ] **步骤 4：同步 Kotlin 代码**

`PlayerInvSettings.kt` 中的 `reload()` 方法，同步修改所有 `config.getXXX("camelCaseKey", ...)` 字符串字面量。

- [ ] **步骤 5：验证构建** → 预期 BUILD SUCCESSFUL

- [ ] **步骤 6：Commit**

```bash
git add project/core/src/main/resources/feature/playerinv.yml project/core/src/main/kotlin/com/pixlehavencore/feature/playerinv/PlayerInvSettings.kt
git commit -m "refactor(config): playerinv 键名 camelCase → kebab-case，统一引号和分隔线"
```

---

### 任务 7：优化模块 — view-distance-controller + entity-clearer

**文件：**
- 修改：`project/core/src/main/resources/feature/optimization/view-distance-controller.yml`
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/optimization/viewdistance/ViewDistanceSettings.kt`
- 修改：`project/core/src/main/resources/feature/optimization/entity-clearer.yml`
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/optimization/entityclearer/EntityClearerSettings.kt`

- [ ] **步骤 1：view-distance-controller.yml 键名 + Kotlin 同步**

`syncSimulationDistance` → `sync-simulation-distance`, `defaultDistance` → `default-distance`, `displayOnJoin` → `display-on-join` 等。同步修改 Kotlin。

- [ ] **步骤 2：entity-clearer.yml 注释统一**

此文件键名已是 kebab-case，只需统一注释措辞。

- [ ] **步骤 3：验证构建** → 预期 BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add project/core/src/main/resources/feature/optimization/ project/core/src/main/kotlin/com/pixlehavencore/feature/optimization/
git commit -m "refactor(config): 优化模块键名 camelCase → kebab-case，注释统一"
```

---

### 任务 8：settings.yml + PixleHavenSettings — 去引号

**文件：**
- 修改：`project/core/src/main/resources/settings.yml`
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/PixleHavenSettings.kt`

- [ ] **步骤 1：settings.yml 去引号**

- `type: "sqlite"` → `type: sqlite`
- `file: "pixelhavencore.db"` → `file: pixelhavencore.db`
- `host: "localhost"` → `host: localhost`
- `port: "3306"` → `port: 3306`
- `database: "pixelhavencore"` → `database: pixelhavencore`
- `user: "root"` → `user: root`
- `password: "password"` → `password: password`

- [ ] **步骤 2：同步 PixleHavenSettings.kt**

检查是否使用了 `config.getString("database.mysql.port", ...)` 等方法。`port` 改为数字后可考虑改用 `getInt`（可选类型改进）。

- [ ] **步骤 3：验证构建** → 预期 BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add project/core/src/main/resources/settings.yml project/core/src/main/kotlin/com/pixlehavencore/PixleHavenSettings.kt
git commit -m "refactor(config): settings.yml 字符串和数字去引号"
```

---

### 任务 9：flight.yml + notification.yml — 缩进和列表格式

**文件：**
- 修改：`project/core/src/main/resources/feature/flight.yml`
- 修改：`project/core/src/main/resources/feature/notification.yml`

- [ ] **步骤 1：flight.yml 列表缩进修复**

将 `enabled-worlds` 下列表项缩进从 3 空格改为 2 空格：
```yaml
# 改前
   - sky-overworld
   - sky-nether

# 改后
  - sky-overworld
  - sky-nether
```

- [ ] **步骤 2：notification.yml 内联列表 → 块列表**

```yaml
# 改前
warning-minutes: [60, 30, 15, 5, 1]

# 改后
warning-minutes:
  - 60
  - 30
  - 15
  - 5
  - 1
```

- [ ] **步骤 3：验证 YAML 语法**

```bash
./gradlew :project:core:compileKotlin
```

- [ ] **步骤 4：Commit**

```bash
git add project/core/src/main/resources/feature/flight.yml project/core/src/main/resources/feature/notification.yml
git commit -m "refactor(config): flight 列表缩进修复，notification 内联列表改块列表"
```

---

### 任务 10：veinminer.yml — 行内注释改为上方注释

**文件：**
- 修改：`project/core/src/main/resources/feature/veinminer.yml`

- [ ] **步骤 1：逐行改写注释**

将所有行内注释（`值 # 注释`）改为上方注释（`# 注释` 独占一行，值在下一行）：

```yaml
# 改前
enabled: true # 功能开关（总开关在 settings.yml -> features.veinminer）
cooldown: 20 # 触发冷却（tick）

# 改后
# 功能开关（总开关在 settings.yml -> features.veinminer）
enabled: true

# 触发冷却（tick）
cooldown: 20
```

- [ ] **步骤 2：统一 enabled 措辞**

按规范改为 `# 功能开关（true = 启用，false = 禁用）`

- [ ] **步骤 3：验证 YAML 语法**

```bash
./gradlew :project:core:compileKotlin
```

- [ ] **步骤 4：Commit**

```bash
git add project/core/src/main/resources/feature/veinminer.yml
git commit -m "refactor(config): veinminer 行内注释改为上方注释，统一措辞"
```

---

### 任务 11：base 模块 — 补注释 + 去引号

**文件：**
- 修改：`project/core/src/main/resources/feature/base/protection.yml`
- 修改：`project/core/src/main/resources/feature/base/killme.yml`
- 修改：`project/core/src/main/resources/feature/base/back.yml`

- [ ] **步骤 1：protection.yml 补注释 + 去引号**

添加文件头（分隔线 + 功能说明），将 `- "FROG"` 改为 `- FROG`，补充 `enabled` 措辞。

- [ ] **步骤 2：killme.yml 补注释**

添加文件头，统一 `enabled` 措辞。

- [ ] **步骤 3：back.yml 补注释**

添加文件头（如果缺失），统一 `enabled` 措辞。

- [ ] **步骤 4：验证构建** → 预期 BUILD SUCCESSFUL

- [ ] **步骤 5：Commit**

```bash
git add project/core/src/main/resources/feature/base/
git commit -m "refactor(config): base 模块补全注释，统一引号和措辞"
```

---

### 任务 12：其余文件 — 注释措辞和文件头统一

**文件：**
- 修改：`project/core/src/main/resources/feature/vanish.yml`
- 修改：`project/core/src/main/resources/feature/flight.yml`（仅注释部分）
- 修改：`project/core/src/main/resources/feature/face-trade.yml`
- 修改：`project/core/src/main/resources/feature/playerinfo.yml`
- 修改：`project/core/src/main/resources/feature/key-command.yml`
- 修改：`project/core/src/main/resources/feature/player-state.yml`
- 修改：`project/core/src/main/resources/feature/death-drop.yml`（注释部分）
- 修改：`project/core/src/main/resources/feature/crafting-bench/config.yml`

> 这些文件键名无需改动，只需统一注释措辞和文件头格式。

- [ ] **步骤 1：批量统一 enabled 措辞**

将所有文件的 `# 功能开关` / `# 功能总开关` / `# 基础开关` 等变体统一为：
```
# 功能开关（true = 启用，false = 禁用）
```

- [ ] **步骤 2：检查文件头分隔线**

确保所有文件头有 77 个 `=` 的分隔线和模块名称。跳过示例文件（`titles/example.yml`, `recipes/bandage.yml`）。

- [ ] **步骤 3：验证构建** → 预期 BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add project/core/src/main/resources/feature/
git commit -m "refactor(config): 统一所有配置文件注释措辞和文件头格式"
```

---

### 任务 13：最终验证

- [ ] **步骤 1：完整构建**

```bash
./gradlew build
```

预期：BUILD SUCCESSFUL，零编译警告。

- [ ] **步骤 2：检查 YAML 语法**

手动确认每个 `.yml` 文件能够被 SnakeYAML 正常解析。可以用 `./gradlew build` 的输出作为验证——如果有 YAML 解析错误，构建会直接失败。

- [ ] **步骤 3：git status 清理确认**

```bash
git status
```

确保所有变更都已提交，工作区干净。

- [ ] **步骤 4：最终 Commit（如有遗漏）**

如有遗漏的文件变更，补充提交。

