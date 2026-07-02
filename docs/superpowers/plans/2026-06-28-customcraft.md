# CustomCraft 自定义合成模块 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 实现基于原版工作台的 Bukkit ShapedRecipe/ShapelessRecipe 配方注册系统，支持管理员 GUI 拖拽创建配方，自动识别 CE/MM/原版/头颅物品库和非库物品（GsonUtils 序列化）。

**架构：** 6 个新文件 + 2 个修改。YAML 定义配方 → CustomCraftRecipeLoader 扫描 → 识别 spec/json 物品格式 → Bukkit.addRecipe() 注册 → 玩家 join 时 discoverRecipe() 解锁。编辑 GUI 采用 3×3 材料区 + R 结果槽 + 保存/清空。

**技术栈：** Kotlin, TabooLib, Paper 1.21.11, Arim GsonUtils, ItemUtils（CE/MM/head/material）

---

### 任务 1：数据模型 — CustomCraftModels

**文件：**
- 创建：`project/core/src/main/kotlin/com/pixlehavencore/feature/customcraft/CustomCraftModels.kt`

- [ ] **步骤 1：创建 CustomCraftModels.kt**

```kotlin
package com.pixlehavencore.feature.customcraft

enum class RecipeType { SHAPED, SHAPELESS }

data class CraftingRecipe(
    val id: String,
    val type: RecipeType,
    val materials: List<RecipeIngredient>,
    val result: RecipeIngredient
)

data class RecipeIngredient(
    val spec: String? = null,
    val json: String? = null,
    val amount: Int = 1,
    val slot: Int? = null
)
```

- [ ] **步骤 2：Build 验证**

```bash
./gradlew build
```

- [ ] **步骤 3：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/customcraft/CustomCraftModels.kt
git commit -m "feat(customcraft): 新增配方数据模型"
```

---

### 任务 2：配置层 — CustomCraftSettings + config.yml

**文件：**
- 创建：`project/core/src/main/kotlin/com/pixlehavencore/feature/customcraft/CustomCraftSettings.kt`
- 创建：`project/core/src/main/resources/feature/customcraft/config.yml`

- [ ] **步骤 1：创建 config.yml**

```yaml
# feature/customcraft/config.yml
version: 1
enabled: true
enableAutoDiscover: true
```

- [ ] **步骤 2：创建 CustomCraftSettings.kt**

```kotlin
package com.pixlehavencore.feature.customcraft

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object CustomCraftSettings {

    @Config("feature/customcraft/config.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    var enableAutoDiscover: Boolean = true
        private set

    fun init() = reload()

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        enableAutoDiscover = config.getBoolean("enableAutoDiscover", true)
    }
}
```

- [ ] **步骤 3：Build 验证**

```bash
./gradlew build
```

- [ ] **步骤 4：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/customcraft/CustomCraftSettings.kt project/core/src/main/resources/feature/customcraft/config.yml
git commit -m "feat(customcraft): 新增配置层"
```

---

### 任务 3：配方加载器 — CustomCraftRecipeLoader

**文件：**
- 创建：`project/core/src/main/kotlin/com/pixlehavencore/feature/customcraft/CustomCraftRecipeLoader.kt`

- [ ] **步骤 1：创建 CustomCraftRecipeLoader.kt**

```kotlin
package com.pixlehavencore.feature.customcraft

import com.pixlehavencore.util.ArimFolderUtils
import com.pixlehavencore.util.ItemUtils
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.warning
import taboolib.module.configuration.Configuration
import top.maplex.arim.tools.gson.GsonUtils
import java.io.File

object CustomCraftRecipeLoader {

    private val recipesDir: File
        get() = File(getDataFolder(), "feature/customcraft/recipes")

    fun loadAll(): List<CraftingRecipe> {
        if (!recipesDir.exists()) recipesDir.mkdirs()
        val files = recipesDir.listFiles { f -> f.extension == "yml" } ?: emptyArray()
        return files.mapNotNull { loadFromFile(it) }
    }

    fun load(id: String): CraftingRecipe? {
        val file = File(recipesDir, "$id.yml")
        if (!file.exists()) return null
        return loadFromFile(file)
    }

    private fun loadFromFile(file: File): CraftingRecipe? {
        return runCatching {
            val config = Configuration.loadFromFile(file)
            val id = config.getString("id") ?: file.nameWithoutExtension
            val type = when (config.getString("type")?.lowercase()) {
                "shapeless" -> RecipeType.SHAPELESS
                else -> RecipeType.SHAPED
            }

            val materials = config.getMapList("materials").mapNotNull { map ->
                parseIngredient(map)
            }

            val resultMap = config.getConfigurationSection("result")?.getValues(false) ?: emptyMap()
            val result = parseIngredient(resultMap) ?: return null

            CraftingRecipe(id = id, type = type, materials = materials, result = result)
        }.onFailure { ex ->
            warning("[CustomCraft] 加载配方失败 ${file.name}: ${ex.message}")
        }.getOrNull()
    }

    fun saveToFile(recipe: CraftingRecipe) {
        val file = File(recipesDir, "${recipe.id}.yml")
        recipesDir.mkdirs()
        val config = Configuration.empty()
        config["id"] = recipe.id
        config["type"] = recipe.type.name.lowercase()

        val materialsList = recipe.materials.map { ingredientToMap(it) }
        config["materials"] = materialsList

        config["result"] = ingredientToMap(recipe.result)

        config.saveToFile(file)
    }

    private fun parseIngredient(map: Map<String, Any>): RecipeIngredient? {
        val spec = map["spec"] as? String
        val json = map["json"] as? String
        if (spec == null && json == null) return null
        val amount = (map["amount"] as? Number)?.toInt() ?: 1
        val slot = (map["slot"] as? Number)?.toInt()
        return RecipeIngredient(spec = spec, json = json, amount = amount, slot = slot)
    }

    private fun ingredientToMap(ingredient: RecipeIngredient): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        ingredient.spec?.let { map["spec"] = it }
        ingredient.json?.let { map["json"] = it }
        map["amount"] = ingredient.amount
        ingredient.slot?.let { map["slot"] = it }
        return map
    }

    fun itemToIngredient(item: ItemStack): RecipeIngredient {
        val libId = ItemUtils.getNamespacedItemId(item)
        if (libId != null) {
            return RecipeIngredient(spec = libId, amount = item.amount)
        }
        if (ItemUtils.isHeadSpec("head:") && item.type.name.contains("SKULL")) {
            return RecipeIngredient(spec = "head:player", amount = item.amount)
        }
        val spec = getMaterialSpec(item)
        if (spec != null) {
            return RecipeIngredient(spec = spec, amount = item.amount)
        }
        val json = GsonUtils.toJson(item)
        return RecipeIngredient(json = json, amount = item.amount)
    }

    private fun getMaterialSpec(item: ItemStack): String? {
        if (item.itemMeta != null && (item.itemMeta.hasDisplayName() || item.itemMeta.hasLore() || item.itemMeta.hasEnchants())) {
            return null
        }
        return item.type.name
    }

    fun ingredientToItem(ingredient: RecipeIngredient): ItemStack? {
        if (!ingredient.spec.isNullOrBlank()) {
            return ItemUtils.resolveSpec(ingredient.spec)?.apply { amount = ingredient.amount }
        }
        if (!ingredient.json.isNullOrBlank()) {
            val item = GsonUtils.fromJson(ingredient.json, ItemStack::class.java)
            item?.amount = ingredient.amount
            return item
        }
        return null
    }
}
```

- [ ] **步骤 2：Build 验证**

```bash
./gradlew build
```

- [ ] **步骤 3：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/customcraft/CustomCraftRecipeLoader.kt
git commit -m "feat(customcraft): 新增配方 YAML 加载器（物品库检测+GsonUtils序列化）"
```

---

### 任务 4：核心服务 — CustomCraftService

**文件：**
- 创建：`project/core/src/main/kotlin/com/pixlehavencore/feature/customcraft/CustomCraftService.kt`

- [ ] **步骤 1：创建 CustomCraftService.kt**

```kotlin
package com.pixlehavencore.feature.customcraft

import com.pixlehavencore.util.ItemUtils
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.RecipeChoice
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.ShapelessRecipe
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning

object CustomCraftService {

    private val recipes = mutableMapOf<String, CraftingRecipe>()
    private val registeredKeys = mutableListOf<NamespacedKey>()

    fun init() {
        CustomCraftSettings.init()
        if (!CustomCraftSettings.enabled) return
        loadAllRecipes()
    }

    fun reload() {
        unregisterAll()
        CustomCraftSettings.reload()
        loadAllRecipes()
    }

    fun stop() {
        unregisterAll()
    }

    fun getRecipe(id: String): CraftingRecipe? = recipes[id]

    fun getAllRecipes(): List<CraftingRecipe> = recipes.values.toList()

    fun getRegisteredKeys(): List<NamespacedKey> = registeredKeys.toList()

    fun discoverAllRecipes(player: org.bukkit.entity.Player) {
        if (!CustomCraftSettings.enableAutoDiscover) return
        registeredKeys.forEach { key ->
            runCatching { player.discoverRecipe(key) }
        }
    }

    private fun loadAllRecipes() {
        recipes.clear()
        val loaded = CustomCraftRecipeLoader.loadAll()
        loaded.forEach { recipe ->
            recipes[recipe.id] = recipe
            registerBukkitRecipe(recipe)
        }
        info("[CustomCraft] 已加载 ${recipes.size} 个配方")
    }

    fun loadAndRegister(id: String): Boolean {
        val recipe = CustomCraftRecipeLoader.load(id) ?: return false
        unregisterRecipe(recipe.id)
        recipes[recipe.id] = recipe
        registerBukkitRecipe(recipe)
        return true
    }

    fun saveAndRegister(recipe: CraftingRecipe) {
        CustomCraftRecipeLoader.saveToFile(recipe)
        unregisterRecipe(recipe.id)
        recipes[recipe.id] = recipe
        registerBukkitRecipe(recipe)
    }

    private fun unregisterRecipe(id: String) {
        val key = NamespacedKey("phcore", id.lowercase())
        Bukkit.removeRecipe(key)
        registeredKeys.remove(key)
    }

    private fun unregisterAll() {
        registeredKeys.forEach { Bukkit.removeRecipe(it) }
        registeredKeys.clear()
        recipes.clear()
    }

    private fun registerBukkitRecipe(recipe: CraftingRecipe) {
        val key = NamespacedKey("phcore", recipe.id.lowercase())
        val resultItem = CustomCraftRecipeLoader.ingredientToItem(recipe.result) ?: return

        runCatching {
            when (recipe.type) {
                RecipeType.SHAPED -> {
                    val shaped = ShapedRecipe(key, resultItem)
                    val shape = buildShape(recipe.materials)
                    shaped.shape(shape[0], shape[1], shape[2])
                    val charMap = mutableMapOf<Char, Material>()
                    val choices = mutableMapOf<Char, RecipeChoice>()
                    recipe.materials.sortedBy { it.slot }.forEach { ing ->
                        val char = ('a' + (ing.slot ?: 0))
                        val item = CustomCraftRecipeLoader.ingredientToItem(ing)
                        if (item != null) {
                            if (ing.spec != null) {
                                choices[char] = RecipeChoice.ExactChoice(item)
                            } else {
                                charMap[char] = item.type
                            }
                        }
                    }
                    charMap.forEach { (c, m) -> shaped.setIngredient(c, m) }
                    choices.forEach { (c, choice) -> shaped.setIngredient(c, choice) }
                    Bukkit.addRecipe(shaped)
                }
                RecipeType.SHAPELESS -> {
                    val shapeless = ShapelessRecipe(key, resultItem)
                    recipe.materials.forEach { ing ->
                        val item = CustomCraftRecipeLoader.ingredientToItem(ing)
                        if (item != null) {
                            if (ing.spec != null) {
                                shapeless.addIngredient(item.amount, RecipeChoice.ExactChoice(item))
                            } else {
                                shapeless.addIngredient(item.amount, item.type)
                            }
                        }
                    }
                    Bukkit.addRecipe(shapeless)
                }
            }
            registeredKeys.add(key)
        }.onFailure { ex ->
            warning("[CustomCraft] 注册配方失败 ${recipe.id}: ${ex.message}")
        }
    }

    private fun buildShape(materials: List<RecipeIngredient>): Array<String> {
        val grid = CharArray(9) { ' ' }
        materials.filter { it.slot != null }.forEach { ing ->
            val char = ('a' + (ing.slot ?: 0))
            grid[ing.slot ?: 0] = char
        }
        return arrayOf(
            String(charArrayOf(grid[0], grid[1], grid[2])),
            String(charArrayOf(grid[3], grid[4], grid[5])),
            String(charArrayOf(grid[6], grid[7], grid[8]))
        )
    }
}
```

- [ ] **步骤 2：Build 验证**

```bash
./gradlew build
```

- [ ] **步骤 3：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/customcraft/CustomCraftService.kt
git commit -m "feat(customcraft): 新增核心服务（配方注册/管理）"
```

---

### 任务 5：编辑 GUI — CustomCraftEditorMenu

**文件：**
- 创建：`project/core/src/main/kotlin/com/pixlehavencore/feature/customcraft/CustomCraftEditorMenu.kt`

- [ ] **步骤 1：创建 CustomCraftEditorMenu.kt**

```kotlin
package com.pixlehavencore.feature.customcraft

import com.pixlehavencore.bridge.TextBridge
import com.pixlehavencore.util.ItemUtils
import com.pixlehavencore.util.TextUtils
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import taboolib.common.platform.event.SubscribeEvent

object CustomCraftEditorMenu {

    private const val ROWS = 5
    private val editSessions = mutableMapOf<Int, EditorSession>()
    private val actionKey = NamespacedKey("phcore", "customcraft_action")

    private val matSlots = intArrayOf(10, 11, 12, 19, 20, 21, 28, 29, 30)
    private val resultSlot = 25
    private val saveSlot = 40
    private val clearSlot = 41
    private val decorativeSlots = listOf(
        0, 1, 2, 7, 8,
        9, 15, 16, 17,
        18, 22, 26,
        27, 33, 34, 35,
        36, 37, 38, 39, 42, 43, 44
    )

    private data class EditorSession(
        val player: Player,
        val recipeId: String
    )

    fun open(player: Player, recipeId: String) {
        val title = TextUtils.parse("&8编辑配方 - $recipeId")
        val inv = Bukkit.createInventory(null, ROWS * 9, title)
        val filler = decorativeItem()

        decorativeSlots.forEach { inv.setItem(it, filler) }

        inv.setItem(saveSlot, actionItem(Material.LIME_CONCRETE, "&a保存配方", "save"))
        inv.setItem(clearSlot, actionItem(Material.RED_CONCRETE, "&c清空所有格子", "clear"))

        editSessions[System.identityHashCode(inv)] = EditorSession(player, recipeId)
        player.openInventory(inv)
    }

    @SubscribeEvent
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val session = editSessions[System.identityHashCode(event.view.topInventory)] ?: return
        if (session.player.uniqueId != player.uniqueId) return

        val clicked = event.clickedInventory
        if (clicked != event.view.topInventory) return

        val slot = event.slot
        if (slot in decorativeSlots) {
            event.isCancelled = true
            return
        }

        if (slot == saveSlot) {
            event.isCancelled = true
            saveRecipe(player, event.view.topInventory, session)
            return
        }

        if (slot == clearSlot) {
            event.isCancelled = true
            clearEditor(event.view.topInventory)
            return
        }
    }

    @SubscribeEvent
    fun onClose(event: InventoryCloseEvent) {
        editSessions.remove(System.identityHashCode(event.inventory))
    }

    private fun saveRecipe(player: Player, inv: Inventory, session: EditorSession) {
        val materials = mutableListOf<RecipeIngredient>()

        for (i in matSlots.indices) {
            val item = inv.getItem(matSlots[i])
            if (item != null && item.type != Material.AIR) {
                val ing = CustomCraftRecipeLoader.itemToIngredient(item)
                materials.add(ing.copy(slot = i))
            }
        }

        val resultItem = inv.getItem(resultSlot)
        if (resultItem == null || resultItem.type == Material.AIR) {
            player.sendMessage(TextUtils.parse("&c请在 R 格放入合成结果物品"))
            return
        }
        val result = CustomCraftRecipeLoader.itemToIngredient(resultItem)

        val type = if (materials.size <= 4) RecipeType.SHAPELESS else RecipeType.SHAPED

        val recipe = CraftingRecipe(
            id = session.recipeId,
            type = type,
            materials = materials,
            result = result
        )

        CustomCraftService.saveAndRegister(recipe)
        player.closeInventory()
        player.sendMessage(TextUtils.parse("&a配方 &e${session.recipeId} &a已保存并注册"))
    }

    private fun clearEditor(inv: Inventory) {
        matSlots.forEach { inv.setItem(it, null) }
        inv.setItem(resultSlot, null)
    }

    private fun decorativeItem(): ItemStack {
        val item = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        TextBridge.setDisplayName(item, TextUtils.parseItem("&7"))
        return item
    }

    private fun actionItem(material: Material, name: String, action: String): ItemStack {
        val item = ItemStack(material)
        TextBridge.setDisplayName(item, TextUtils.parseItem(name))
        item.editMeta { meta ->
            meta.persistentDataContainer.set(actionKey, PersistentDataType.STRING, action)
        }
        return item
    }
}
```

- [ ] **步骤 2：Build 验证**

```bash
./gradlew build
```

- [ ] **步骤 3：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/customcraft/CustomCraftEditorMenu.kt
git commit -m "feat(customcraft): 新增配方编辑 GUI（3×3材料+R结果+保存/清空）"
```

---

### 任务 6：命令 — CustomCraftCommand

**文件：**
- 创建：`project/core/src/main/kotlin/com/pixlehavencore/feature/customcraft/CustomCraftCommand.kt`

- [ ] **步骤 1：创建 CustomCraftCommand.kt**

```kotlin
package com.pixlehavencore.feature.customcraft

import com.pixlehavencore.util.ADMIN_PERMISSION
import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePermission
import com.pixlehavencore.util.requirePlayer
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand

@CommandHeader(name = "customcraft", permissionDefault = PermissionDefault.TRUE)
object CustomCraftCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
            sender.msg("<gold>=== CustomCraft 帮助 ===")
            sender.msg("<aqua>/customcraft create <id> <gray>- 创建配方（打开编辑 GUI）")
            sender.msg("<aqua>/customcraft reload <gray>- 重载全部配方")
            sender.msg("<aqua>/customcraft list <gray>- 列出所有配方")
        }
    }

    @CommandBody
    val create = subCommand {
        dynamic(comment = "recipeId") {
            execute<ProxyCommandSender> { sender, _, argument ->
                if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
                val player = sender.requirePlayer() ?: return@execute
                val id = argument.toString().trim()
                if (id.isBlank()) {
                    sender.msg("<red>配方 ID 不能为空")
                    return@execute
                }
                CustomCraftEditorMenu.open(player.cast(), id)
            }
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
            CustomCraftService.reload()
            sender.msg("<green>CustomCraft 配方已重载")
        }
    }

    @CommandBody
    val list = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission(ADMIN_PERMISSION)) return@execute
            val recipes = CustomCraftService.getAllRecipes()
            if (recipes.isEmpty()) {
                sender.msg("<gray>暂无配方")
            } else {
                sender.msg("<gold>=== 配方列表 (${recipes.size}) ===")
                recipes.forEach { r ->
                    sender.msg("<yellow>${r.id} <gray>- ${r.type.name} (${r.materials.size} 材料)")
                }
            }
        }
    }
}
```

- [ ] **步骤 2：Build 验证**

```bash
./gradlew build
```

- [ ] **步骤 3：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/customcraft/CustomCraftCommand.kt
git commit -m "feat(customcraft): 新增 /customcraft 管理命令"
```

---

### 任务 7：集成 — PixleHavenCore.kt + mainCommand.kt

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/PixleHavenCore.kt`
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/mainCommand.kt`

- [ ] **步骤 1：PixleHavenCore.kt 注册生命周期**

添加 import：
```kotlin
import com.pixlehavencore.feature.customcraft.CustomCraftService
import com.pixlehavencore.feature.customcraft.CustomCraftSettings
```

在 `onEnable()` 中添加 `CustomCraftService.init()`，在 `logModulesStatus()` 添加状态条目，在 `onDisable()` 添加 `CustomCraftService.stop()`。

- [ ] **步骤 2：mainCommand.kt 注册 ReloadStep**

添加 import：
```kotlin
import com.pixlehavencore.feature.customcraft.CustomCraftService
```

在 `reloadAllModules()` 的 steps 列表中添加：
```kotlin
ReloadStep("customcraft", false) { CustomCraftService.reload() },
```

- [ ] **步骤 3：Build 验证**

```bash
./gradlew build
```

- [ ] **步骤 4：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/PixleHavenCore.kt project/core/src/main/kotlin/com/pixlehavencore/mainCommand.kt
git commit -m "feat(customcraft): 集成到插件生命周期和重载系统"
```

---

### 任务 8：全量构建验证

- [ ] **步骤 1：清理并完整构建**

```bash
./gradlew clean build
```

预期：BUILD SUCCESSFUL

- [ ] **步骤 2：检查 git status**

```bash
git status
```

预期：工作区干净

- [ ] **步骤 3：验证提交历史**

```bash
git log --oneline -10
```
