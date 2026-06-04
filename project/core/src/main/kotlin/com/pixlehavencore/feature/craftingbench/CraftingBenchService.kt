package com.pixlehavencore.feature.craftingbench

import com.pixlehavencore.feature.playerinv.PlayerInvService
import com.pixlehavencore.util.ADMIN_PERMISSION
import com.pixlehavencore.util.ItemUtils
import com.pixlehavencore.util.cancelTaskSafely
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.warning
import taboolib.platform.util.submit as submitOnEntity
import taboolib.platform.util.submit as submitOnLocation
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToLong

object CraftingBenchService {

    private val recipes = ConcurrentHashMap<String, CraftingRecipe>()
    private val queues = ConcurrentHashMap<UUID, MutableList<CraftingTask>>()
    private val pendingClaims = ConcurrentHashMap<UUID, MutableList<ClaimEntry>>()
    private val taskIdGenerator = AtomicLong(1L)

    @Volatile
    private var storageReady = false
    private var tickTask: Any? = null
    private var flushTask: Any? = null
    private var menuRefreshTask: Any? = null

    fun init() {
        stop()
        CraftingBenchSettings.init()
        recipes.clear()
        recipes.putAll(CraftingBenchRecipeLoader.loadAll())
        if (!CraftingBenchSettings.enabled) {
            info("[CraftingBench] 模块已禁用。")
            return
        }
        storageReady = false
        CraftingBenchStorage.init(
            onLoaded = { snapshot ->
                applySnapshot(snapshot)
                storageReady = true
                startTickTask()
                startFlushTask()
                startMenuRefreshTask()
                info("[CraftingBench] 已加载 ${recipes.size} 个配方，工作台映射 ${CraftingBenchSettings.blockMappings.size} 个，恢复队列 ${snapshot.tasks.size} 条。")
            },
            onFailure = { ex ->
                storageReady = false
                warning("[CraftingBench] 存储初始化失败，模块暂不可用: ${ex.message}")
            }
        )
    }

    fun reload() {
        init()
    }

    fun stop() {
        tickTask.cancelTaskSafely()
        flushTask.cancelTaskSafely()
        menuRefreshTask.cancelTaskSafely()
        tickTask = null
        flushTask = null
        menuRefreshTask = null
        if (storageReady) {
            runCatching { CraftingBenchStorage.flush(snapshotState()) }
                .onFailure { ex -> warning("[CraftingBench] 关服写入失败: ${ex.message}") }
        }
        CraftingBenchStorage.close()
        storageReady = false
        queues.clear()
        pendingClaims.clear()
    }

    fun isEnabled(): Boolean {
        return CraftingBenchSettings.enabled
    }

    fun resolveTierByBlockId(blockId: String): BenchTier? {
        return CraftingBenchSettings.getTierByBlockId(blockId)
    }

    fun getAvailableRecipes(player: Player, tier: BenchTier): List<RecipePreview> {
        val allSpecs = recipes.values.flatMap { recipe -> recipe.materials.map { it.item } }.distinct()
        val warehouseCounts = loadWarehouseCounts(player, allSpecs)
        val inventoryCounts = countAllInventoryMaterials(player, allSpecs)
        return recipes.values
            .filter { recipe ->
                recipe.requiredBenchTier.isNotBlank() &&
                    CraftingBenchSettings.isTierAllowed(recipe.requiredBenchTier, tier) &&
                    (recipe.unlockPermission.isBlank() || player.hasPermission(recipe.unlockPermission) || player.hasPermission(ADMIN_PERMISSION))
            }
            .sortedBy { it.id }
            .map { recipe ->
                RecipePreview(
                    recipe = recipe,
                    canCraft = canCraft(player, tier, recipe),
                    enoughMaterials = hasMaterials(player, recipe, inventoryCounts, warehouseCounts, 1),
                    estimatedSeconds = calculateTimeSeconds(recipe, tier, player, 1),
                )
            }
    }

    fun getRecipe(recipeId: String): CraftingRecipe? {
        return recipes[recipeId]
    }

    fun getAllRecipeIds(): List<String> {
        return recipes.keys.sorted()
    }

    fun hasActiveTasksForRecipe(recipeId: String): Boolean {
        return queues.values.any { queue ->
            synchronized(queue) { queue.any { it.recipeId == recipeId } }
        }
    }

    fun getRecipeMaterialStatuses(player: Player, recipe: CraftingRecipe, craftCount: Int = 1): List<RecipeMaterialStatus> {
        val finalCount = craftCount.coerceAtLeast(1)
        val specs = recipe.materials.map { it.item }
        val warehouseCounts = loadWarehouseCounts(player, specs)
        val inventoryCounts = countAllInventoryMaterials(player, specs)
        return recipe.materials.map { material ->
            val inventoryAmount = inventoryCounts[material.item] ?: 0
            val requiredAmount = material.amount * finalCount
            val warehouseAmount = warehouseCounts[material.item] ?: 0
            val warehouseWillUse = (requiredAmount - inventoryAmount).coerceAtLeast(0).coerceAtMost(warehouseAmount)
            RecipeMaterialStatus(
                material = material,
                inventoryAmount = inventoryAmount,
                warehouseAmount = warehouseAmount,
                requiredAmount = requiredAmount,
                warehouseWillUse = warehouseWillUse,
            )
        }
    }

    fun estimateCraftSeconds(player: Player, tier: BenchTier, recipe: CraftingRecipe, craftCount: Int = 1): Double {
        val finalCount = craftCount.coerceAtLeast(1)
        return calculateTimeSeconds(recipe, tier, player, finalCount)
    }

    fun getPlayerTasks(playerId: UUID): List<CraftingTask> {
        return queues[playerId]?.let { synchronized(it) { it.toList() } }?.sortedBy { it.taskId }.orEmpty()
    }

    fun getPendingClaimCount(playerId: UUID): Int {
        return pendingClaims[playerId]?.let { synchronized(it) { it.size } } ?: 0
    }

    fun submitCraft(player: Player, tier: BenchTier, recipeId: String, craftCount: Int = 1): SubmitResult {
        if (!storageReady) {
            return SubmitResult(false, "制作台数据仍在初始化，请稍后再试。")
        }
        val finalCount = craftCount.coerceAtLeast(1)
        val recipe = recipes[recipeId] ?: return SubmitResult(false, "配方不存在。")
        if (!CraftingBenchSettings.canUseTier(player::hasPermission, tier) && !player.hasPermission(ADMIN_PERMISSION)) {
            return SubmitResult(false, "你没有使用该工作台的权限。")
        }
        if (!CraftingBenchSettings.isTierAllowed(recipe.requiredBenchTier, tier)) {
            return SubmitResult(false, "当前工作台等级不足。")
        }
        if (recipe.unlockPermission.isNotBlank() && !player.hasPermission(recipe.unlockPermission) && !player.hasPermission(ADMIN_PERMISSION)) {
            return SubmitResult(false, "你尚未解锁这个配方。")
        }
        val queue = queues.computeIfAbsent(player.uniqueId) { mutableListOf() }
        val queueLimit = CraftingBenchSettings.resolveQueueLimit(player::hasPermission)
        synchronized(queue) {
            if (queue.size >= queueLimit) {
                return SubmitResult(false, "你的制作队列已满。")
            }
        }
        val specs = recipe.materials.map { it.item }
        val warehouseCounts = loadWarehouseCounts(player, specs)
        val inventoryCounts = countAllInventoryMaterials(player, specs)
        if (!hasMaterials(player, recipe, inventoryCounts, warehouseCounts, finalCount)) {
            return SubmitResult(false, "材料不足。")
        }
        if (!consumeMaterials(player, recipe, warehouseCounts, finalCount)) {
            return SubmitResult(false, "提取材料失败，请稍后再试。")
        }
        val totalTicks = (calculateTimeSeconds(recipe, tier, player, finalCount) * 20.0).roundToLong().coerceAtLeast(1L)
        val task = CraftingTask(
            taskId = taskIdGenerator.getAndIncrement(),
            owner = player.uniqueId,
            recipeId = recipe.id,
            craftCount = finalCount,
            totalTicks = totalTicks,
            remainingTicks = totalTicks,
            submittedAt = System.currentTimeMillis(),
        )
        synchronized(queue) {
            queue += task
        }
        flushStateAsync()
        return SubmitResult(true, "已加入制作队列。", task.taskId)
    }

    fun cancelTask(player: Player, taskId: Long): String {
        if (!storageReady) {
            return "制作台数据仍在初始化，请稍后再试。"
        }
        val queue = queues[player.uniqueId] ?: return "未找到该制作任务。"
        val task = synchronized(queue) { queue.firstOrNull { it.taskId == taskId } } ?: return "未找到该制作任务。"
        val recipe = recipes[task.recipeId]
        synchronized(queue) {
            queue.removeIf { it.taskId == taskId }
            // 在 synchronized 块内检查队列是否为空
            if (queue.isEmpty()) {
                queues.remove(player.uniqueId)
            }
        }
        if (recipe != null) {
            refundMaterials(player, recipe, task.craftCount)
        }
        flushStateAsync()
        return "已取消任务 #$taskId。"
    }

    fun flushPendingClaims(player: Player) {
        val claims = pendingClaims.remove(player.uniqueId).orEmpty()
        if (claims.isEmpty()) {
            return
        }
        claims.forEach { claim ->
            deliverSpec(player, claim.itemSpec, claim.amount)
        }
        player.sendMessage("&a你有 ${claims.size} 个离线制作产物已发放。".replace('&', '§'))
        flushStateAsync()
    }

    private fun canCraft(player: Player, tier: BenchTier, recipe: CraftingRecipe): Boolean {
        return CraftingBenchSettings.canUseTier(player::hasPermission, tier) &&
            (recipe.unlockPermission.isBlank() || player.hasPermission(recipe.unlockPermission) || player.hasPermission(ADMIN_PERMISSION)) &&
            hasMaterials(player, recipe, countAllInventoryMaterials(player, recipe.materials.map { it.item }), loadWarehouseCounts(player, recipe.materials.map { it.item }), 1)
    }

    private fun hasMaterials(player: Player, recipe: CraftingRecipe, inventoryCounts: Map<String, Int>, warehouseCounts: Map<String, Int>, craftCount: Int): Boolean {
        val finalCount = craftCount.coerceAtLeast(1)
        return recipe.materials.all { material ->
            (inventoryCounts[material.item] ?: 0) + (warehouseCounts[material.item] ?: 0) >= material.amount * finalCount
        }
    }

    /**
     * 单次遍历背包统计多种材料规格的库存量，避免对每种材料独立遍历整个背包。
     */
    private fun countAllInventoryMaterials(player: Player, specs: List<String>): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        if (specs.isEmpty()) return result
        player.inventory.contents.filterNotNull().forEach { stack ->
            specs.forEach { spec ->
                if (ItemUtils.matchesSpec(spec, stack)) {
                    result.merge(spec, stack.amount) { a, b -> a + b }
                }
            }
        }
        return result
    }

    private fun consumeMaterials(player: Player, recipe: CraftingRecipe, warehouseCounts: Map<String, Int>, craftCount: Int): Boolean {
        val finalCount = craftCount.coerceAtLeast(1)
        // 预计算所有材料的背包库存量（单次遍历替代 N 次独立遍历）
        val inventoryCounts = countAllInventoryMaterials(player, recipe.materials.map { it.item })
        val warehouseRequired = linkedMapOf<String, Int>()
        recipe.materials.forEach { material ->
            val inventoryCount = inventoryCounts[material.item] ?: 0
            val requiredAmount = material.amount * finalCount
            val shortage = (requiredAmount - inventoryCount).coerceAtLeast(0)
            val warehouseAvailable = warehouseCounts[material.item] ?: 0
            if (shortage > warehouseAvailable) {
                return false
            }
            if (shortage > 0) {
                warehouseRequired[material.item] = shortage
            }
        }
        if (warehouseRequired.isNotEmpty() && !PlayerInvService.consumePersonalMaterials(player.uniqueId, warehouseRequired)) {
            return false
        }
        recipe.materials.forEach { material ->
            var remaining = ((material.amount * finalCount) - (warehouseRequired[material.item] ?: 0)).coerceAtLeast(0)
            val contents = player.inventory.contents
            for (slot in contents.indices) {
                val stack = contents[slot] ?: continue
                if (!ItemUtils.matchesSpec(material.item, stack)) {
                    continue
                }
                val used = remaining.coerceAtMost(stack.amount)
                stack.amount -= used
                if (stack.amount <= 0) {
                    player.inventory.setItem(slot, null)
                } else {
                    player.inventory.setItem(slot, stack)
                }
                remaining -= used
                if (remaining <= 0) {
                    break
                }
            }
        }
        player.updateInventory()
        return true
    }

    private fun loadWarehouseCounts(player: Player, specs: Collection<String>): Map<String, Int> {
        if (!PlayerInvService.isReady()) {
            return emptyMap()
        }
        return PlayerInvService.countPersonalMaterials(player.uniqueId, specs)
    }

    private fun refundMaterials(player: Player, recipe: CraftingRecipe, craftCount: Int) {
        val finalCount = craftCount.coerceAtLeast(1)
        val refundRatio = CraftingBenchSettings.queueSettings.cancelRefundRatio
        recipe.materials.forEach { material ->
            val refundAmount = (material.amount * finalCount * refundRatio).roundToLong().toInt().coerceAtLeast(0)
            if (refundAmount <= 0) {
                return@forEach
            }
            deliverSpec(player, material.item, refundAmount)
        }
    }

    private fun calculateTimeSeconds(recipe: CraftingRecipe, tier: BenchTier, player: Player, craftCount: Int): Double {
        var time = recipe.craftTimeSeconds * craftCount.coerceAtLeast(1) * tier.speedModifier
        CraftingBenchSettings.specializations.forEach { specialization ->
            if (player.hasPermission(specialization.permission) && specialization.appliesTo.contains(recipe.category)) {
                time *= (1.0 - specialization.timeReduction)
            }
        }
        return time.coerceAtLeast(0.1)
    }

    private fun startTickTask() {
        tickTask = submit(period = 20L) {
            tickQueues()
        }
    }

    private fun startFlushTask() {
        flushTask = submit(async = true, period = 100L) {
            if (storageReady) {
                runCatching { CraftingBenchStorage.flush(snapshotState()) }
                    .onFailure { ex -> warning("[CraftingBench] 周期写入失败: ${ex.message}") }
            }
        }
    }

    private fun startMenuRefreshTask() {
        menuRefreshTask = submit(period = 20L) {
            if (storageReady) {
                CraftingBenchMenu.refreshOpenMenus()
            }
        }
    }

    private fun tickQueues() {
        if (!storageReady) {
            return
        }
        var changed = false
        queues.entries.toList().forEach { (ownerId, tasks) ->
            val task = synchronized(tasks) { tasks.firstOrNull() } ?: run {
                queues.remove(ownerId)
                changed = true
                return@forEach
            }
            // Folia: Bukkit.getPlayer 在全局区域调度器上调用是安全的（只读在线玩家列表）
            val player = Bukkit.getPlayer(task.owner)
            if (player == null && !CraftingBenchSettings.queueSettings.allowOfflineCrafting) {
                return@forEach
            }
            task.remainingTicks = (task.remainingTicks - 20L).coerceAtLeast(0L)
            changed = true
            if (task.remainingTicks > 0L) {
                return@forEach
            }
            completeTask(task, player)
            synchronized(tasks) {
                tasks.removeIf { it.taskId == task.taskId }
            }
            if (tasks.isEmpty()) {
                queues.remove(ownerId)
            }
            flushStateAsync()
        }
        if (changed) {
            CraftingBenchMenu.refreshOpenMenus()
        }
    }

    private fun completeTask(task: CraftingTask, onlinePlayer: Player?) {
        val recipe = recipes[task.recipeId] ?: return
        if (onlinePlayer != null) {
            onlinePlayer.submitOnEntity {
                if (CraftingBenchSettings.queueSettings.autoClaimOnline) {
                    recipe.results.forEach { result ->
                        val finalAmount = (result.amount * task.craftCount).coerceAtLeast(1)
                        deliverSpec(onlinePlayer, result.item, finalAmount)
                    }
                    onlinePlayer.sendMessage("&a制作完成：${recipe.displayName}。".replace('&', '§'))
                } else {
                    recipe.results.forEach { result ->
                        val finalAmount = (result.amount * task.craftCount).coerceAtLeast(1)
                        addPendingClaim(ClaimEntry(onlinePlayer.uniqueId, result.item, finalAmount, task.taskId))
                    }
                    onlinePlayer.sendMessage("&e制作完成：${recipe.displayName} 已进入待领取。".replace('&', '§'))
                }
            }
            return
        }
        // 离线完成：每个产出独立生成 ClaimEntry
        recipe.results.forEach { result ->
            val finalAmount = (result.amount * task.craftCount).coerceAtLeast(1)
            addPendingClaim(ClaimEntry(task.owner, result.item, finalAmount, task.taskId))
        }
    }

    private fun deliverSpec(player: Player, spec: String, amount: Int) {
        val item = ItemUtils.resolveSpec(spec)?.clone() ?: return
        item.amount = amount.coerceAtLeast(1)
        deliverItem(player, item)
    }

    private fun deliverItem(player: Player, item: ItemStack) {
        val leftovers = player.inventory.addItem(item)
        if (leftovers.isEmpty()) {
            return
        }
        if (CraftingBenchSettings.queueSettings.fullInventoryAction == "drop") {
            // Folia: dropItemNaturally 需要在位置所属的区域线程上执行
            val location = player.location
            val world = player.world
            location.submitOnLocation {
                leftovers.values.forEach { left ->
                    world.dropItemNaturally(location, left)
                }
            }
        }
    }

    private fun applySnapshot(snapshot: StorageSnapshot) {
        queues.clear()
        pendingClaims.clear()
        snapshot.tasks
            .filter { recipes.containsKey(it.recipeId) }
            .groupBy { it.owner }
            .forEach { (ownerId, tasks) ->
                queues[ownerId] = tasks.sortedBy { it.taskId }.toMutableList()
            }
        snapshot.claims.forEach { claim ->
            pendingClaims.computeIfAbsent(claim.owner) { mutableListOf() }.add(claim)
        }
        val nextId = (snapshot.tasks.maxOfOrNull { it.taskId } ?: 0L) + 1L
        taskIdGenerator.set(nextId.coerceAtLeast(1L))
    }

    private fun snapshotState(): StorageSnapshot {
        val tasks = mutableListOf<CraftingTask>()
        queues.forEach { (_, queue) ->
            synchronized(queue) {
                queue.forEach { task ->
                    tasks += task.copy()
                }
            }
        }
        val claims = pendingClaims.values.flatMap { list -> synchronized(list) { list.toList() } }
        return StorageSnapshot(tasks = tasks, claims = claims)
    }

    private fun flushStateAsync() {
        if (!storageReady) {
            return
        }
        CraftingBenchStorage.flushAsync(snapshotState())
    }

    private fun addPendingClaim(claim: ClaimEntry) {
        val list = pendingClaims.computeIfAbsent(claim.owner) { mutableListOf() }
        synchronized(list) {
            list += claim
        }
    }

}

data class SubmitResult(
    val success: Boolean,
    val message: String,
    val taskId: Long? = null,
)
