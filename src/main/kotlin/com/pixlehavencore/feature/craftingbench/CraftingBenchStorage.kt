package com.pixlehavencore.feature.craftingbench

import com.google.gson.reflect.TypeToken
import com.pixlehavencore.util.ArimJsonUtils
import com.pixlehavencore.util.DatabaseUtils
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import taboolib.expansion.MultipleHandler
import java.util.UUID

object CraftingBenchStorage {

    private const val TABLE_NAME = "crafting_bench_player_state"
    private const val KEY_QUEUE = "queue"
    private const val KEY_CLAIMS = "claims"

    private val queueListType = object : TypeToken<List<StoredTask>>() {}.type
    private val claimListType = object : TypeToken<List<ClaimEntry>>() {}.type

    @Volatile
    private var handler: MultipleHandler? = null

    fun init(onLoaded: (StorageSnapshot) -> Unit, onFailure: (Throwable) -> Unit) {
        close()
        submitAsync {
            runCatching {
                handler = DatabaseUtils.newPlayerDataHandler(TABLE_NAME, syncTick = 200L)
                loadSnapshot()
            }.onSuccess { snapshot ->
                submit { onLoaded(snapshot) }
            }.onFailure { ex ->
                warning("[CraftingBench] 初始化存储失败: ${ex.message}")
                submit { onFailure(ex) }
            }
        }
    }

    fun flushAsync(snapshot: StorageSnapshot) {
        if (handler == null) {
            return
        }
        submitAsync {
            runCatching {
                flush(snapshot)
            }.onFailure { ex ->
                warning("[CraftingBench] 异步写入队列失败: ${ex.message}")
            }
        }
    }

    fun flush(snapshot: StorageSnapshot) {
        val currentHandler = handler ?: return
        val queueByOwner = snapshot.tasks.groupBy { it.owner }
        val claimByOwner = snapshot.claims.groupBy { it.owner }
        val owners = (queueByOwner.keys + claimByOwner.keys).toSet()
        owners.forEach { owner ->
            val user = owner.toString()
            val queuePayload = ArimJsonUtils.toJson(queueByOwner[owner].orEmpty().map { task ->
                StoredTask(
                    taskId = task.taskId,
                    recipeId = task.recipeId,
                    craftCount = task.craftCount,
                    totalTicks = task.totalTicks,
                    remainingTicks = task.remainingTicks,
                    submittedAt = task.submittedAt,
                )
            })
            val claimPayload = ArimJsonUtils.toJson(claimByOwner[owner].orEmpty())
            currentHandler.database[user, KEY_QUEUE] = queuePayload
            currentHandler.database[user, KEY_CLAIMS] = claimPayload
        }
    }

    fun close() {
        handler = null
    }

    private fun loadSnapshot(): StorageSnapshot {
        val currentHandler = handler ?: return StorageSnapshot(emptyList(), emptyList())
        val tasks = mutableListOf<CraftingTask>()
        currentHandler.database.getListByKey(KEY_QUEUE).forEach { (user, payload) ->
            val owner = runCatching { UUID.fromString(user) }.getOrNull() ?: return@forEach
            val list = runCatching {
                ArimJsonUtils.gson().fromJson<List<StoredTask>>(payload, queueListType)
            }.getOrNull().orEmpty()
            list.forEach { stored ->
                tasks += CraftingTask(
                    taskId = stored.taskId,
                    owner = owner,
                    recipeId = stored.recipeId,
                    craftCount = stored.craftCount.coerceAtLeast(1),
                    totalTicks = stored.totalTicks,
                    remainingTicks = stored.remainingTicks,
                    submittedAt = stored.submittedAt,
                )
            }
        }
        val claims = mutableListOf<ClaimEntry>()
        currentHandler.database.getListByKey(KEY_CLAIMS).forEach { (user, payload) ->
            val owner = runCatching { UUID.fromString(user) }.getOrNull() ?: return@forEach
            val list = runCatching {
                ArimJsonUtils.gson().fromJson<List<ClaimEntry>>(payload, claimListType)
            }.getOrNull().orEmpty()
            claims += list.filter { it.owner == owner }
        }
        return StorageSnapshot(tasks = tasks, claims = claims)
    }
}

private data class StoredTask(
    val taskId: Long,
    val recipeId: String,
    val craftCount: Int,
    val totalTicks: Long,
    val remainingTicks: Long,
    val submittedAt: Long,
)

data class StorageSnapshot(
    val tasks: List<CraftingTask>,
    val claims: List<ClaimEntry>,
)
