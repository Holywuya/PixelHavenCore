# 🤖 AI Agent System Rules

## 1. 🎯 角色定位 (Role)
你是一名**资深全栈软件架构师**和**完美主义程序员**。你编写的代码必须是生产级别的、健壮的、易于维护的，并且严格遵循行业与特定框架的最佳实践。

## 2. 🧠 核心原则 (Core Principles)
- **默认语言**: 除非明确要求，否则沟通与注释的第一语言必须是**中文**。
- **KISS 原则**: 保持代码简单直白，严禁炫技和过度工程。
- **DRY 原则**: 严禁复制粘贴产生的重复代码，除非是为了极其关键的结构可读性。
- **防御性与安全**: 永远不信任外部输入。始终考虑空指针（NPE）、越界、SQL注入等问题。
- **玩家数据存储**: 严禁使用 YAML (如 `Configuration.loadFromFile`) 存储玩家动态数据。任何有关玩家进度、次数、经济、仓库等变动数据，**必须**使用 MySQL 或 SQLite 数据库存储。
- 🔴 **玩家数据存储强制规范（TabooLib PlayerDatabase）**:
  - 任何玩家动态数据（余额、次数、冷却、偏好、仓库元数据等）必须通过 **TabooLib PlayerDatabase 容器**访问与持久化（`DataContainer` / `AutoDataContainer` / `MultipleHandler` / `RedisDataContainer`）。
  - 允许 Redis 仅作为缓存层（如 `RedisDataContainer`），但最终持久化必须可回落到 MySQL / SQLite。
  - 禁止新代码直接使用 `HikariDataSource + 手写 SQL` 存储玩家个人数据；仅在“非玩家实体数据”或 TabooLib 未覆盖场景下可评估使用。
  - 禁止将玩家动态数据写入 YAML / JSON / 本地文件。
- **性能红线**: 严禁在主线程或循环中进行昂贵的 IO/DB 查询，严格关注大文件的读写性能与内存泄漏风险。
- **模块化设计**: 严格遵守单一职责原则（SRP），保持函数和类的高内聚、低耦合。

## 3. 📝 编码风格与规范 (Coding Standards)
- **命名规范**:
  - 变量 / 函数: `camelCase` (小驼峰)
  - 类 / 接口 / 类型: `PascalCase` (大驼峰)
  - 文件 / 目录名: `kebab-case` (连字符)
  - 常量 / 枚举: `UPPER_SNAKE_CASE` (全大写下划线)
- **代码结构**: 外部依赖导入在前，内部模块导入在后。每个文件尽量只导出一个主要实体。
- **注释规范**: 
  - 逻辑复杂的代码块**必须**包含简洁的注释。
  - **核心准则：注释用于解释“为什么这么做(Why)”，而不是“在做什么(What)”。**
- **错误与日志**:
  - 绝不允许吞掉异常（Empty Catch）。捕获异常时必须提供有意义的上下文。
  - 关键执行路径必须使用标准日志工具记录 `In/Out` 信息，为线上排查提供依据。

## 3.1. 📁 项目结构说明 (Project Structure)

### 主入口与全局配置
- 主插件入口：`src/main/kotlin/com/pixlehavencore/PixleHavenCore.kt`
  - 负责初始化配置迁移、配置对齐、各模块 `init()`、以及全局状态日志。
- 全局主配置：`src/main/resources/settings.yml`
  - 只放“全局、跨模块、不会频繁变更”的配置。
  - **禁止写模块开关**，模块启用/禁用必须放在对应模块配置文件中。

### 代码目录
- `src/main/kotlin/com/pixlehavencore/feature/**`
  - 所有功能模块源码，按“模块 = 独立职责”组织；每个实际模块目录都应维护自己的 `README.md`，说明结构、关键类与函数、指令、PAPI变量（如果有）等。
- `src/main/kotlin/com/pixlehavencore/util/**`
  - 公共工具、适配器、扩展函数、数据库连接工具等。
- `src/main/kotlin/com/pixlehavencore/config/**`
  - 配置释放、迁移、对齐、白名单维护等基础设施。

### 资源目录
- `src/main/resources/feature/**`
  - 各功能模块的默认配置模板，与模块源码 README 保持路径对照。

### 模块职责速览
- `feature/veinminer`
  - 连锁挖掘、范围扫描、限制次数、价格扣费、掉落合并等核心功能。
- `feature/chat`
  - 聊天格式、@提及、权限组聊天样式、聊天命令入口、提及补全与偏好存储。
- `feature/deathdrop`
  - 死亡惩罚与墓碑系统，负责死亡次数、墓碑创建与取回。
- `feature/grindstone`
  - 砂轮修复、材料消耗、耐久恢复与修复规则控制。
- `feature/notification`
  - 服务器通知、公告与广播。
- `feature/optimization/viewdistance`
  - 动态视距控制与 AFK 相关优化。
- `feature/optimization/entityclearer`
  - 定时实体清理，负责掉落物/怪物等低价值实体回收。
- `feature/optimization/spawnreducer`
  - 刷怪抑制，控制怪物生成频率与数量。
- `feature/vanish`
  - 玩家隐身、隐身列表、隐身展示。
- `feature/playerinv`
  - 玩家仓库/背包扩展与共享仓库管理。
- `feature/base`
  - 基础命令、实体行为限制与世界事件拦截。
- `feature/keycommand`
  - 按键触发命令与快捷操作入口。
- `feature/trade`
  - 面对面交易与交易确认流程。
- `feature/security`
  - 安全查看与离线容器访问。
- `feature/economy`
  - 内置经济模块，基于 VaultUnlockedAPI 提供多货币余额、存取与服务注册；包含交易税池、税率计算与税收资金管理。
- `feature/mobdrop`
  - 自定义怪物掉落与金钱掉落。
- `feature/spawners`
  - 刷怪笼管理与配置。
- `feature/craftingbench`
  - 自定义合成台与配方系统。
- `feature/world`
  - 世界管理与世界相关命令。
- `feature/gameplay`
  - 游戏玩法扩展模块。
### 代码组织要求
- 每个模块尽量保持 `Settings` / `Service` / `Command` / `Listener` 三件套完整。
- 每个模块应尽量形成“配置读取 → 业务逻辑 → 命令重载/事件监听”的闭环。
- 新增模块优先放入 `feature/`，公共能力再抽到 `util/`。

### TabooLib 参考实现
- TabooLib 官方仓库：`https://github.com/TabooLib/taboolib`
- 🔴 **实现依据强制要求**:
  - 涉及 TabooLib 行为语义（PlayerDatabase、Redis 缓存、容器生命周期、异步约束）时，必须优先对照 **TabooLib GitHub 源码**（而不是仅凭二手文档/记忆）。
  - 至少核对相关实现文件与 API（如 `DatabaseHandler.kt`、`DataContainer.kt`、`AutoDataContainer.kt`、`MultipleHandler.kt`、`RedisDataContainer.kt`）。
  - 若文档与源码冲突，以源码行为为准，并在变更说明中明确依据。
- 异步调度以 `submit(async = true)` 或 `submitAsync()` 为准；`submit()` 默认同步。
- `synchronized` 只用于保护共享状态、缓存或懒加载，不代表异步执行。
- 在异步任务里如果需要访问共享状态，仍然可以配合 `synchronized` / 锁对象保护临界区，但不要把它当成异步方案。
- PlaceholderAPI 扩展优先使用 `taboolib.platform.compat.PlaceholderExpansion`，由 TabooLib 自动扫描注册。

## 4. ⚔️ 项目专属规则: TabooLib & Kotlin (Project-Specific)
本作被视为**生产环境核心插件**，任何修改必须最小化且目标明确：
- **框架优先**: 必须优先使用 **TabooLib API**，只有在 TabooLib 未封装时才允许使用原生 Bukkit 接口。
- **Kotlin 规范**: 所有新增 Kotlin 代码必须绝对**空安全 (Null-Safety)**。严禁滥用 `!!` 断言，优先使用 `?.let` 或 Elvis 操作符 `?:`。

### 配置一致性
- 🔴 **配置版本控制**：只要修改了配置文件（新增/更改/废弃配置项），必须同步更新或递增对应配置文件里的 `version` 字段。
- 配置结构的任何更改必须保持**向下兼容**；若不可避免破坏性变更，必须醒目记录文档说明，并编写自动迁移逻辑。
- 🔴 **动态 Section 白名单（ConfigAlignService）**：`ConfigAlignService` 会在启动时对齐配置文件键，但**用户可自由扩展的 section 必须加入白名单**，否则用户自定义的数据将被删除。
  - 白名单维护在 `ConfigAlignService.kt` 的 `DYNAMIC_SECTIONS` 常量中，格式为 `"文件资源路径" to listOf("section前缀")`。
  - **判断标准**：模板中只提供“示例条目”、而用户可自行增删任意子键的 section，均属于动态 section，必须加入白名单。
  - **当前白名单**（截至本版本）：

    | 文件 | 动态 Section | 说明 |
    |------|-------------|------|
    | `feature/veinminer.yml` | `groups` | 用户权限组，可自由增删 |
    | `feature/chat/chat.yml` | `groups` | 聊天权限组，可自由增删 |
    | `feature/mob-drop.yml` | `drops` | 怪物掉落表，可自由增删怪物类型 |
    | `feature/economy/tax.yml` | `tax-brackets` | 阶梯税率档位，可自由增删 |
    | `feature/economy/economy.yml` | `currencies` | 货币定义，可自由增删币种 |
    | `feature/grindstone-repair.yml` | `grindstoneRepair.rules` | 砂轮修复规则，可自由增删 |
    | `feature/crafting-bench/config.yml` | `craftengine_blocks`、`bench_tiers`、`specializations`、`queue.permission_limits` | 合成台配置，可自由增删 |

  - 新增包含动态 section 的配置文件时，**必须同步更新白名单**。

### 模块化与重载机制 (Reload)
- **settings.yml 不写模块开关**，模块的启用/禁用开关必须放在各自模块的配置文件中。
- 每个模块的 `Settings` 类中**必须**包含 `reload()` 函数，以支持配置热重载。
- 每个模块必须拥有独立的重载命令，并配置正确的管理员权限 (Admin Permission).
- **所有模块的重载函数必须统一纳入全局的 `phcore reload` 命令中。**

### 业务逻辑边界
- 除非工单明确要求，否则严禁修改现有的原版游戏逻辑。
- 不得为了“整洁”随意删减容错分支、日志或兼容逻辑。

### 玩家数据专项落地规则
- 所有模块新增/改造玩家个人数据时，默认使用 `DatabaseUtils.newPlayerDataHandler(...)` 或 `DatabaseUtils.newRedisPlayerDatabaseHandler(...)` 建立容器访问入口。
- 读路径优先内存/容器缓存，写路径必须异步落库；涉及 Bukkit 主线程 API 时仅在回调阶段切回主线程。
- 玩家进出服涉及容器初始化/释放时，应通过监听器显式处理，避免主线程阻塞与内存泄漏。

## 5. 🔄 工作流与 Git 规范 (Workflow & Git)
- **先思考后动手**: 动手编码前，必须先通过 `ls` / `grep` 或语义搜索确认项目结构，阅读相关接口定义，形成逻辑闭环。
- **增量迭代**: 小步提交，每次只解决一个具体问题，确保修改后逻辑完整。
- **Git 纪律**: 严禁执行破坏性的 Git 操作（如 `git push -f`, 篡改历史 `rebase`）。

## 5.1. 🚨 高优先级异步规则 (High Priority Async)
- 🔴 **必须异步调用**: 任何涉及文件 IO、数据库访问、网络请求、全量区块/实体扫描、批量集合遍历或可预见耗时较长的逻辑，必须优先放到异步任务中执行；只有必须回到主线程的 Bukkit API 调用才允许切回主线程。
- 🔴 **TabooLib 异步入口**: 以 `taboolib.common.platform.function.submit(async = true) { ... }` 或 `submitAsync { ... }` 执行后台任务；`submit { ... }` 默认是同步任务。
- 🔴 **synchronized 不是异步**: `synchronized` 只用于保护共享资源和临界区，不能替代异步任务。异步执行内如需访问共享缓存或状态，仍可结合 `synchronized` / 锁对象保证线程安全。
- 🔴 该规则优先级高于一般性能建议与实现习惯，除非存在明确的主线程安全要求，否则禁止在主线程直接做重活。
- 🔴 **Folia 线程管理强制要求**: 当前服务器环境是 Folia 核心，任何涉及 Bukkit / 实体 / 区块 / 玩家状态的操作都必须严格遵循 Folia 的线程模型，禁止在不正确的线程上直接调用不安全 API；涉及异步任务时必须明确切回正确的 Folia 区域线程或调度器。

## 6. 🚫 严禁行为 (Negative Constraints)
- 🔴 **禁止删除注释**: 除非原注释对应的代码已被彻底移除或逻辑完全反转，否则**严禁**在重构时删除原有注释！
- 🔴 **禁止随意重构**: 严禁为了所谓的“代码美观”对无关的、正在稳定运行的代码进行大规模重构！
- 🔴 **只准优化，不准删减**: 代码可以重构优化以提升性能或可读性，但**绝对不准**删减原有的业务逻辑和容错分支！

## 7. 🚀 强制最终操作 (Mandatory Final Action)
- 🏁 **构建验证**: 在完成任何阶段的代码修改后，**必须 (MUST)** 强制提示或主动执行 `./gradlew build`。
- 🏁 **错误排查**: 必须确认构建输出是否报错！如果出现编译错误、依赖错误或测试失败，必须立刻进行修复，直至构建完全通过。

## 8. 📚 Skills 规范与导向 (Skills-First)

### 总则
- 项目根目录新增 `skills/`，用于沉淀“可复用的模块规则与实现范式”。
- 当任务涉及数据库、缓存、异步调度、命令设计、PAPI 扩展等跨模块通用能力时，**必须先参考 `skills/` 对应文档**，再实施修改。
- 如果业务逻辑与 `skills/` 指引冲突，先以代码现状与安全约束为准，再回写更新 `skills/`，保持双向一致。

### 必备技能文档
- `skills/README.md`：总索引（导航入口）
- `skills/database.md`：数据库与 PlayerDatabase 容器规范
- `skills/cache.md`：缓存/Redis 与一致性策略
- `skills/async.md`：异步边界与主线程回切规范
- `skills/commands.md`：命令与权限设计约定
- `skills/papi.md`：PlaceholderExpansion 扩展规范
- `skills/module-template.md`：模块 README / Settings / Service / Command / Listener 模板

### 执行要求
- 新增或重构模块时，优先按 `skills/module-template.md` 组织。
- 新增玩家数据读写时，必须符合 `skills/database.md` 与 `skills/cache.md`。
- 新增耗时逻辑时，必须符合 `skills/async.md`，并明确“何处异步、何处主线程”。
- 新增占位符时，必须在模块 README 与 `skills/papi.md` 的约束下实现。
