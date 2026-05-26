package com.pixlehavencore.feature.optimization.entityclearer

import com.pixlehavencore.bridge.TextBridge
import com.pixlehavencore.util.PlaceholderUtils.resolvePlaceholders
import com.pixlehavencore.util.TextUtils
import com.pixlehavencore.util.cancelTaskSafely
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.entity.Entity
import org.bukkit.entity.Item
import org.bukkit.entity.Monster
import taboolib.common.platform.function.submit

import taboolib.platform.util.submit as submitOnEntity
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object EntityClearerService {

    private const val CHUNKS_PER_TICK = 12
    private val countdownNotified = mutableSetOf<Long>()
    private var schedulerTask: Any? = null
    private var clearWorkerTask: Any? = null
    private var clearQueue: ConcurrentLinkedQueue<Chunk> = ConcurrentLinkedQueue()
    private val activeChunkTasks = AtomicInteger(0)
    private val cycleFinished = AtomicBoolean(false)
    private var nextCycleAtMillis = 0L
    private val cycleRemoved = AtomicInteger(0)

    fun init() {
        EntityClearerSettings.init()
        stopTasks()
        if (!isEnabled()) {
            return
        }
        scheduleNextCycle()
        startScheduler()
    }

    fun reload() {
        init()
    }

    fun isEnabled(): Boolean {
        return EntityClearerSettings.enabled
    }

    fun stopTasks() {
        schedulerTask.cancelTaskSafely()
        clearWorkerTask.cancelTaskSafely()
        schedulerTask = null
        clearWorkerTask = null
        clearQueue.clear()
        activeChunkTasks.set(0)
        cycleFinished.set(false)
        cycleRemoved.set(0)
        nextCycleAtMillis = 0L
        countdownNotified.clear()
    }

    private fun startScheduler() {
        schedulerTask = submit(delay = 20L, period = 20L) {
            if (!isEnabled()) {
                return@submit
            }
            if (clearWorkerTask != null) {
                return@submit
            }
            val now = System.currentTimeMillis()
            val remainingSeconds = ((nextCycleAtMillis - now) + 999L) / 1000L
            if (remainingSeconds <= 0L) {
                startClearCycle()
                return@submit
            }
            if (remainingSeconds in EntityClearerSettings.countdownSeconds && countdownNotified.add(remainingSeconds)) {
                val message = EntityClearerSettings.countdownMessage
                    .resolvePlaceholders("{seconds}" to remainingSeconds.toString())
                broadcastActionBar(message)
            }
        }
    }

    private fun scheduleNextCycle() {
        nextCycleAtMillis = System.currentTimeMillis() + EntityClearerSettings.scanIntervalSeconds * 1000L
        countdownNotified.clear()
    }

    private fun startClearCycle() {
        cycleRemoved.set(0)
        activeChunkTasks.set(0)
        cycleFinished.set(false)
        clearQueue = ConcurrentLinkedQueue(snapshotChunks())
        if (clearQueue.isEmpty()) {
            finishClearCycle()
            return
        }
        clearWorkerTask = submit(period = 1L) {
            if (!isEnabled()) {
                clearWorkerTask.cancelTaskSafely()
                clearWorkerTask = null
                return@submit
            }
            if (cycleFinished.get()) {
                return@submit
            }

            var processed = 0
            while (processed < CHUNKS_PER_TICK) {
                val chunk = clearQueue.poll() ?: break
                activeChunkTasks.incrementAndGet()
                chunk.submitOnEntity {
                    try {
                        cycleRemoved.addAndGet(scanChunk(chunk))
                    } finally {
                        if (activeChunkTasks.decrementAndGet() == 0 && clearQueue.isEmpty()) {
                            finishClearCycle()
                        }
                    }
                }
                processed++
            }

            if (clearQueue.isEmpty() && activeChunkTasks.get() == 0) {
                finishClearCycle()
            }
        }
    }

    private fun finishClearCycle() {
        if (!cycleFinished.compareAndSet(false, true)) {
            return
        }
        clearWorkerTask.cancelTaskSafely()
        clearWorkerTask = null
        val message = EntityClearerSettings.cycleSummaryMessage
            .resolvePlaceholders("{count}" to cycleRemoved.get().toString())
        broadcastActionBar(message)
        cycleRemoved.set(0)
        scheduleNextCycle()
    }

    private fun snapshotChunks(): List<Chunk> {
        // Folia: submit(period = 1L) 调度到全局区域调度器（TabooLib 行为），
        // Bukkit.getWorlds() 和 world.loadedChunks（只读快照）在此上下文中是安全的
        return Bukkit.getWorlds().flatMap { world -> world.loadedChunks.asList() }
    }

    private fun scanChunk(chunk: Chunk): Int {
        val targets = mutableListOf<Entity>()
        chunk.entities.forEach { entity ->
            when {
                entity is Item && EntityClearerSettings.itemsEnabled -> targets.add(entity)
                entity is Monster && EntityClearerSettings.mobsEnabled && TextBridge.getEntityCustomName(entity) == null -> targets.add(entity)
            }
        }
        return clearEntities(targets)
    }

    private fun clearEntities(targets: List<Entity>): Int {
        var removed = 0
        targets.forEach { entity ->
            if (!entity.isValid) {
                return@forEach
            }
            entity.remove()
            removed++
        }
        return removed
    }

    private fun broadcastActionBar(message: String) {
        val component = TextUtils.parse(message)
        Bukkit.getOnlinePlayers().forEach { player ->
            player.submitOnEntity {
                TextBridge.sendActionBar(player, component)
            }
        }
    }

}
