# PlayerInfo 编辑模式与仓库按钮 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 让 playerinfo 的背包/末影箱 GUI 可直接编辑并将改动同步回目标玩家；仪表盘新增仓库按钮对接 PlayerInv 模块。

**架构：** 扩展现有 PlayerInfoService 的事件处理（按 SessionType 区分拦截策略，关闭时回写），OfflineInventoryUtils 新增 `save()` 反射写入离线 NBT。仓库按钮直接调用 PlayerInvService。

**技术栈：** Kotlin, TabooLib, Bukkit/Paper 1.21.11, NMS 反射（NbtIo/ItemStack/CraftItemStack）

---

### 任务 1：OfflineInventoryUtils 新增 save() 方法

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/util/OfflineInventoryUtils.kt`

- [ ] **步骤 1：添加 NMS 反射缓存（写入端）**

在现有反射缓存区域（文件末尾，`resolvePlayerDataFile` 之前）添加以下内容：

```kotlin
private val writeMethod = supplierLazy<Unit, Method> { _ ->
    nbtIoClass[Unit].methods.firstOrNull {
        it.name == "writeCompressed" && it.parameterTypes.size == 2 && it.parameterTypes[1] == java.io.OutputStream::class.java
    } ?: error("找不到 NbtIo.writeCompressed")
}

private val listTagClass = supplierLazy<Unit, Class<*>> {
    Class.forName("net.minecraft.nbt.ListTag")
}

private val compoundTagClass = supplierLazy<Unit, Class<*>> {
    Class.forName("net.minecraft.nbt.CompoundTag")
}

private val saveOptionalMethod = supplierLazy<Unit, Method> { _ ->
    itemStackClass[Unit].methods.firstOrNull {
        it.name == "saveOptional" && it.parameterTypes.size == 1
    } ?: error("找不到 ItemStack.saveOptional")
}

private val asNMSCopyMethod = supplierLazy<Unit, Method> { _ ->
    craftItemStackClass[Unit].getMethod("asNMSCopy", org.bukkit.inventory.ItemStack::class.java)
}

private val tagToString = supplierLazy<Unit, Method> { _ ->
    listTagClass[Unit].getMethod("add", Any::class.java)
}

private val compoundPutMethod = supplierLazy<Unit, Method> { _ ->
    compoundTagClass[Unit].getMethod("put", String::class.java, Any::class.java)
}

private val compoundPutByteMethod = supplierLazy<Unit, Method> { _ ->
    compoundTagClass[Unit].getMethod("putByte", String::class.java, Byte::class.javaPrimitiveType)
}
```

- [ ] **步骤 2：添加 save() 公共方法**

在 `resolvePlayerDataFile` 方法之前添加：

```kotlin
fun save(player: OfflinePlayer, inventory: Array<ItemStack?>?, enderChest: Array<ItemStack?>?): Boolean {
    val playerDataFile = resolvePlayerDataFile(player) ?: return false
    val registryAccess = captureRegistryAccess() ?: return false
    return saveToFile(playerDataFile, registryAccess, player.uniqueId, inventory, enderChest)
}

private fun saveToFile(
    playerDataFile: File,
    registryAccess: Any,
    playerId: UUID,
    inventory: Array<ItemStack?>?,
    enderChest: Array<ItemStack?>?
): Boolean {
    if (!playerDataFile.exists()) return false

    return runCatching {
        val rootTag = FileInputStream(playerDataFile).use { stream ->
            readMethod[Unit].invoke(null, stream, unlimitedAccounter[Unit])
        }

        if (inventory != null) {
            val invList = buildItemListTag(inventory, registryAccess, isEnderChest = false)
            compoundPutMethod[Unit].invoke(rootTag, "Inventory", invList)
        }

        if (enderChest != null) {
            val ecList = buildItemListTag(enderChest, registryAccess, isEnderChest = true)
            compoundPutMethod[Unit].invoke(rootTag, "EnderItems", ecList)
        }

        FileOutputStream(playerDataFile).use { stream ->
            writeMethod[Unit].invoke(null, rootTag, stream)
        }
        true
    }.onFailure { ex ->
        warning("[OfflineInventory] 保存离线玩家数据失败($playerId): ${ex.message}")
    }.getOrDefault(false)
}
```

- [ ] **步骤 3：添加 buildItemListTag 辅助方法**

在 `saveToFile` 之后、`captureRegistryAccess` 之前添加：

```kotlin
private fun buildItemListTag(items: Array<ItemStack?>, registryAccess: Any, isEnderChest: Boolean): Any {
    val listTag = listTagClass[Unit].getDeclaredConstructor().newInstance()
    for (index in items.indices) {
        val item = items[index] ?: continue
        val nmsItem = asNMSCopyMethod[Unit].invoke(null, item) ?: continue
        val optionalTag = saveOptionalMethod[Unit].invoke(nmsItem, registryAccess)
        val compoundMethod = optionalTag?.javaClass?.getMethod("orElse", Any::class.java) ?: continue
        val tag = compoundMethod.invoke(optionalTag, null) ?: continue
        if (!compoundTagClass[Unit].isInstance(tag)) continue
        val slot = denormalizeSlot(index, isEnderChest)
        if (slot == -1) continue
        compoundPutByteMethod[Unit].invoke(tag, "Slot", slot.toByte())
        tagToString[Unit].invoke(listTag, tag)
    }
    return listTag
}

private fun denormalizeSlot(index: Int, isEnderChest: Boolean): Int {
    if (isEnderChest) return if (index in 0..26) index else -1
    return when (index) {
        in 0..35 -> index
        36 -> 100
        37 -> 101
        38 -> 102
        39 -> 103
        40 -> -106
        else -> -1
    }
}
```

- [ ] **步骤 4：更新文件头导入**

在现有 import 块末尾添加：

```kotlin
import java.io.FileOutputStream
```

- [ ] **步骤 5：Build 验证**

```bash
./gradlew build
```

预期：BUILD SUCCESSFUL

- [ ] **步骤 6：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/util/OfflineInventoryUtils.kt
git commit -m "feat(offline-inventory): 新增离线玩家背包 NBT 写入 save() 方法"
```

---

### 任务 2：PlayerInfoService — 仪表盘增加仓库按钮

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/playerinfo/PlayerInfoService.kt`

- [ ] **步骤 1：添加 PlayerInv 导入**

在文件头导入区域 `import com.pixlehavencore.util.TextUtils` 之后添加：

```kotlin
import com.pixlehavencore.feature.playerinv.PlayerInvService
import com.pixlehavencore.feature.playerinv.PlayerInvSettings
```

- [ ] **步骤 2：仪表盘 slot 17 增加仓库按钮**

修改 `openDashboard` 方法，在 slot 16（末影箱按钮）之后增加 slot 17 的仓库按钮。找到以下代码块：

```kotlin
                inventory.setItem(15, buildActionItem(Material.CHEST, "&e查看背包", listOf("&7点击打开背包"), "inv"))
                inventory.setItem(16, buildActionItem(Material.ENDER_CHEST, "&e查看末影箱", listOf("&7点击打开末影箱"), "ec"))

                sessions[System.identityHashCode(inventory)] = Session(
```

替换为：

```kotlin
                inventory.setItem(15, buildActionItem(Material.CHEST, "&e查看背包", listOf("&7点击打开背包"), "inv"))
                inventory.setItem(16, buildActionItem(Material.ENDER_CHEST, "&e查看末影箱", listOf("&7点击打开末影箱"), "ec"))
                inventory.setItem(17, buildActionItem(Material.CHEST_MINECART, "&e个人仓库", listOf("&7点击打开目标玩家仓库"), "ware"))

                sessions[System.identityHashCode(inventory)] = Session(
```

- [ ] **步骤 3：onClick 中增加 ware 动作处理**

修改 `onClick` 方法中的 `when (action)` 分支，在 `"back"` 分支之后增加：

找到：

```kotlin
            "back" -> {
                player.closeInventory()
                openDashboard(player, Bukkit.getOfflinePlayer(session.target))
            }
        }
```

替换为：

```kotlin
            "back" -> {
                player.closeInventory()
                openDashboard(player, Bukkit.getOfflinePlayer(session.target))
            }
            "ware" -> {
                player.closeInventory()
                openWarehouse(player, Bukkit.getOfflinePlayer(session.target))
            }
        }
```

- [ ] **步骤 4：添加 openWarehouse 方法**

在 `openDashboard` 方法之后、`openInventoryView` 之前添加：

```kotlin
    private fun openWarehouse(viewer: Player, target: OfflinePlayer) {
        if (!PlayerInvSettings.enabled) {
            viewer.sendMessage(TextUtils.parseChat("&c仓库模块未启用"))
            return
        }
        PlayerInvService.openOtherAsync(viewer, target) { opened ->
            if (!opened) {
                viewer.sendMessage(TextUtils.parseChat("&c打开目标玩家仓库失败"))
            }
        }
    }
```

- [ ] **步骤 5：Build 验证**

```bash
./gradlew build
```

预期：BUILD SUCCESSFUL

- [ ] **步骤 6：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/playerinfo/PlayerInfoService.kt
git commit -m "feat(playerinfo): 仪表盘新增个人仓库按钮，对接 PlayerInv 模块"
```

---

### 任务 3：PlayerInfoService — 编辑模式事件处理

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/playerinfo/PlayerInfoService.kt`

- [ ] **步骤 1：重写 onClick — 按 SessionType 区分行为**

将现有的 `onClick` 方法替换为：

```kotlin
    @SubscribeEvent
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val session = sessions[System.identityHashCode(event.view.topInventory)] ?: return
        if (session.viewer != player.uniqueId) return

        if (session.type == SessionType.DASHBOARD) {
            event.isCancelled = true
            if (event.clickedInventory != event.view.topInventory) return
            val action = getAction(event.currentItem) ?: return
            when (action) {
                "inv" -> {
                    player.closeInventory()
                    openInventoryView(player, Bukkit.getOfflinePlayer(session.target))
                }
                "ec" -> {
                    player.closeInventory()
                    openEnderChestView(player, Bukkit.getOfflinePlayer(session.target))
                }
                "back" -> {
                    player.closeInventory()
                    openDashboard(player, Bukkit.getOfflinePlayer(session.target))
                }
                "ware" -> {
                    player.closeInventory()
                    openWarehouse(player, Bukkit.getOfflinePlayer(session.target))
                }
            }
            return
        }

        if (event.clickedInventory != event.view.topInventory) return

        val clickedItem = event.currentItem
        if (isProtectedSlot(clickedItem)) {
            if (getAction(clickedItem) == "back") {
                event.isCancelled = true
                player.closeInventory()
                openDashboard(player, Bukkit.getOfflinePlayer(session.target))
            } else {
                event.isCancelled = true
            }
        }
    }
```

- [ ] **步骤 2：重写 onDrag — 按 SessionType 区分行为**

将现有的 `onDrag` 方法替换为：

```kotlin
    @SubscribeEvent
    fun onDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        val session = sessions[System.identityHashCode(event.view.topInventory)] ?: return
        if (session.viewer != player.uniqueId) return

        if (session.type == SessionType.DASHBOARD) {
            event.isCancelled = true
            return
        }

        val topSize = event.view.topInventory.size
        val anyProtectedSlot = event.rawSlots.any { slot ->
            slot < topSize && isProtectedSlot(event.view.topInventory.getItem(slot))
        }
        if (anyProtectedSlot) {
            event.isCancelled = true
        }
    }
```

- [ ] **步骤 3：添加 isProtectedSlot 辅助方法**

在 `getAction` 方法之后、`buildDecorativeItem` 之前添加：

```kotlin
    private fun isProtectedSlot(item: ItemStack?): Boolean {
        if (item == null) return false
        if (getAction(item) != null) return true
        if (item.type == Material.GRAY_STAINED_GLASS_PANE) return true
        return false
    }
```

- [ ] **步骤 4：Build 验证**

```bash
./gradlew build
```

预期：BUILD SUCCESSFUL

- [ ] **步骤 5：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/playerinfo/PlayerInfoService.kt
git commit -m "feat(playerinfo): 编辑模式 — 背包/末影箱 GUI 支持拿取放入操作"
```

---

### 任务 4：PlayerInfoService — 关闭时回写同步

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/playerinfo/PlayerInfoService.kt`

- [ ] **步骤 1：定义内容提取常量**

在文件头部常量区域，`decorativeSlotsEC` 之后添加：

```kotlin
    private const val INV_CONTENT_SIZE = 41
    private const val EC_CONTENT_SIZE = 27
```

- [ ] **步骤 2：重写 onClose — 增加回写逻辑**

将现有的 `onClose` 方法替换为：

```kotlin
    @SubscribeEvent
    fun onClose(event: InventoryCloseEvent) {
        val inventory = event.inventory
        val session = sessions.remove(System.identityHashCode(inventory)) ?: return

        when (session.type) {
            SessionType.INVENTORY -> saveInventoryChanges(inventory, session)
            SessionType.ENDER_CHEST -> saveEnderChestChanges(inventory, session)
            else -> Unit
        }
    }
```

- [ ] **步骤 3：添加 saveInventoryChanges 方法**

在 `onClose` 方法之后添加：

```kotlin
    private fun saveInventoryChanges(inventory: Inventory, session: Session) {
        val target = Bukkit.getOfflinePlayer(session.target)
        val items = Array<ItemStack?>(INV_CONTENT_SIZE) { index ->
            if (index < inventory.size) inventory.getItem(index) else null
        }

        val online = target.player
        if (online != null) {
            online.submitOnEntity {
                online.inventory.contents = items
            }
        } else {
            submit(async = true) {
                OfflineInventoryUtils.save(target, inventory = items, enderChest = null)
            }
        }
    }
```

- [ ] **步骤 4：添加 saveEnderChestChanges 方法**

```kotlin
    private fun saveEnderChestChanges(inventory: Inventory, session: Session) {
        val target = Bukkit.getOfflinePlayer(session.target)
        val items = Array<ItemStack?>(EC_CONTENT_SIZE) { index ->
            if (index < inventory.size) inventory.getItem(index) else null
        }

        val online = target.player
        if (online != null) {
            online.submitOnEntity {
                online.enderChest.contents = items
            }
        } else {
            submit(async = true) {
                OfflineInventoryUtils.save(target, inventory = null, enderChest = items)
            }
        }
    }
```

- [ ] **步骤 5：Build 验证**

```bash
./gradlew build
```

预期：BUILD SUCCESSFUL

- [ ] **步骤 6：Commit**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/playerinfo/PlayerInfoService.kt
git commit -m "feat(playerinfo): 关闭编辑 GUI 时自动同步物品到目标玩家"
```

---

### 任务 5：全量构建验证

- [ ] **步骤 1：清理并完整构建**

```bash
./gradlew clean build
```

预期：BUILD SUCCESSFUL，无编译错误

- [ ] **步骤 2：检查 git status 确认无遗漏文件**

```bash
git status
```

预期：工作区干净（仅修改了 OfflineInventoryUtils.kt 和 PlayerInfoService.kt）

- [ ] **步骤 3：最终验证提交历史**

```bash
git log --oneline -5
```

预期：看到 4 个新 commit，分别对应任务 1-4
