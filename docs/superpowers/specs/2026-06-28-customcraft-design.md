# CustomCraft 自定义合成模块设计

日期：2026-06-28

## 概述

新增 `feature/customcraft` 模块，提供基于原版工作台的 Bukkit ShapedRecipe/ShapelessRecipe 注册系统。支持管理员通过 GUI 拖拽物品创建配方，自动识别 CraftEngine/MythicMobs 物品库物品和非库物品，保存为 YAML 文件并即时生效。

## 架构

### 模块结构

```
feature/customcraft/
├── CustomCraftSettings.kt       # @Config 绑定配置
├── CustomCraftService.kt        # 核心服务：加载配方、注册 Bukkit Recipe、列表查询
├── CustomCraftCommand.kt        # /customcraft create|reload|list 管理命令
├── CustomCraftEditorMenu.kt     # 配方编辑 GUI（3×3材料 + 1结果 + 保存/清空）
├── CustomCraftRecipeLoader.kt   # 配方 YAML 文件夹扫描加载
├── CustomCraftModels.kt         # 配方数据模型
└── resources/feature/customcraft/
    ├── config.yml               # 模块配置
    └── recipes/                 # 配方 YAML 存储目录
        └── *.yml
```

### 数据流

```
YAML 配方文件 → CustomCraftRecipeLoader 扫描
  → 识别物品格式：
      库物品 (ce:/mm:/head:/material: spec) → ItemUtils 解析
      非库物品 (JSON) → GsonUtils.fromJson()
  → 构建 Bukkit ShapedRecipe / ShapelessRecipe
  → Bukkit.addRecipe() 注册
  → 玩家加入时 discoverRecipe() 自动解锁

GUI 编辑流程：
  /customcraft create <id>
  → 打开编辑 GUI（3×3 材料 + 1 结果 + 保存/清空）
  → 管理员拖入物品到各格子
  → 点「保存」
    → 检测每格物品类型：
        库物品 → 记录 spec: "ce:xxx" + amount
        非库物品 → 记录 json: GsonUtils.toJson(item) + amount
    → 写入 recipes/<id>.yml
    → 即时注册 Bukkit 配方
    → 关闭 GUI
```

## 数据模型

```kotlin
data class CraftingRecipe(
    val id: String,
    val type: RecipeType,
    val materials: List<RecipeIngredient>,
    val result: RecipeIngredient
)

enum class RecipeType { SHAPED, SHAPELESS }

data class RecipeIngredient(
    val spec: String?,     // 库物品 spec（ce:xxx, mm:xxx, DIAMOND）
    val json: String?,     // 非库物品 JSON（GsonUtils 序列化）
    val amount: Int = 1,
    val slot: Int? = null  // SHAPED: 0-8; SHAPELESS: null
)
```

- `spec` 和 `json` 互斥
- SHAPED 配方的 `slot` 对应 3×3 网格位置（0=左上，8=右下）
- SHAPELESS 配方忽略 slot 字段

## YAML 配方格式

```yaml
# recipes/dragon_sword.yml
id: dragon_sword
type: shaped
materials:
  - slot: 1
    spec: "ce:phcore:dragon_scale"
    amount: 2
  - slot: 3
    spec: "DIAMOND_SWORD"
    amount: 1
  - slot: 4
    spec: "mm:SkeletonKingDrop"
    amount: 1
  - slot: 7
    spec: "BLAZE_ROD"
    amount: 1
result:
  json: '{"==":"org.bukkit.inventory.ItemStack","v":3837,...}'
  amount: 1
```

## 编辑 GUI 布局

6 行 × 9 格：

```
行1: [装饰] [装饰] [装饰] [空]   [空]   [空]   [空] [装饰] [装饰]
行2: [装饰] [ 0  ] [ 1  ] [ 2 ]  [空]   [空]   [ → ] [ R  ] [装饰]
行3: [装饰] [ 3  ] [ 4  ] [ 5 ]  [空]   [空]   [空] [装饰] [装饰]
行4: [装饰] [ 6  ] [ 7  ] [ 8 ]  [空]   [空]   [空] [装饰] [装饰]
行5: [装饰] [装饰] [装饰] [空]   [结果] [空]   [空] [装饰] [装饰]
行6: [装饰] [装饰] [装饰] [装饰] [保存] [清空] [装饰] [装饰] [装饰]
```

| 位置 | 槽位 | 说明 |
|------|------|------|
| 0-8（3×3区） | 10,11,12 / 19,20,21 / 28,29,30 | 材料放置区 |
| → | 16 | 箭头装饰 |
| R | 17 | 产出预览（自动计算，不可编辑） |
| 结果 | 31 | 管理员拖入最终合成结果物品 |
| 保存 | 40 | 保存为 YAML 并注册配方 |
| 清空 | 41 | 清空所有格子 |

## 物品序列化策略

| 物品类型 | 存储格式 | 序列化方式 |
|----------|---------|-----------|
| CraftEngine | `spec: "ce:phcore:xxx"` | ItemUtils.getNamespacedItemIdBySpec |
| MythicMobs | `spec: "mm:xxx"` | 同上 |
| 原版材质 | `spec: "DIAMOND"` | Material.name |
| 头颅 | `spec: "head:Playername"` | isHeadSpec 判定 |
| 非库物品（带 NBT/附魔/名称） | `json: "{...}"` | GsonUtils.toJson(item) |

- 反序列化：`spec` → ItemUtils.resolveSpec；`json` → GsonUtils.fromJson(json, ItemStack.class)
- GsonUtils 是 Arim 工具箱的内置 Gson 工具，自带 Bukkit ConfigurationSerializable 支持

## 命令

| 命令 | 功能 | 权限 |
|------|------|------|
| `/customcraft create <id>` | 打开配方编辑 GUI | phcore.admin |
| `/customcraft reload` | 从 YAML 重新加载全部配方 | phcore.admin |
| `/customcraft list` | 列出所有已注册配方 | phcore.admin |

命令头使用 `@CommandHeader(name = "customcraft", permissionDefault = PermissionDefault.TRUE)`

## 文件变更

| 操作 | 文件 |
|------|------|
| 新增 | `feature/customcraft/CustomCraftModels.kt` |
| 新增 | `feature/customcraft/CustomCraftSettings.kt` |
| 新增 | `feature/customcraft/CustomCraftRecipeLoader.kt` |
| 新增 | `feature/customcraft/CustomCraftService.kt` |
| 新增 | `feature/customcraft/CustomCraftEditorMenu.kt` |
| 新增 | `feature/customcraft/CustomCraftCommand.kt` |
| 新增 | `resources/feature/customcraft/config.yml` |
| 修改 | `PixleHavenCore.kt` — 注册生命周期 |
| 修改 | `mainCommand.kt` — 注册 ReloadStep |

## 线程安全（Folia 合规）

- 配方注册通过 `Bukkit.addRecipe()` 在服务器主线程执行
- YAML 文件 IO 使用 TabooLib `submit(async = true)` 异步执行
- GUI 操作在玩家所在区域线程处理
