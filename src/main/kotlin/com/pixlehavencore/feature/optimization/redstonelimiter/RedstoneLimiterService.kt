package com.pixlehavencore.feature.optimization.redstonelimiter

import org.bukkit.entity.Player
import com.pixlehavencore.feature.chat.WorldNameMapper
import com.pixlehavencore.util.PlaceholderUtils.resolvePlaceholders
import com.pixlehavencore.util.cancelTaskSafely
import taboolib.common.platform.ProxyPlayer
import taboolib.common.platform.function.info
import taboolib.common.platform.function.onlinePlayers
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.warning
import com.pixlehavencore.util.TextUtils
import taboolib.platform.util.submit as submitOnEntity
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object RedstoneLimiterService {

    private val tracker: ConcurrentHashMap<TrackKey, SlidingWindow> = ConcurrentHashMap()
    private val notifyCooldowns: ConcurrentHashMap<TrackKey, Long> = ConcurrentHashMap()
    private val totalBlocked: AtomicLong = AtomicLong(0)
    private val currentTracked: AtomicInteger = AtomicInteger(0)
    private var cleanupTask: Any? = null

    fun init() {
        RedstoneLimiterSettings.init()
        stop()
        if (!RedstoneLimiterSettings.enabled) return
        scheduleCleanup()
        info("[RedstoneLimiter] 模块已启动，生效世界: ${RedstoneLimiterSettings.enabledWorlds}")
    }

    fun reload() {
        init()
    }

    fun stop() {
        cleanupTask.cancelTaskSafely()
        cleanupTask = null
        tracker.clear()
        notifyCooldowns.clear()
        currentTracked.set(0)
    }

    fun isEnabled(): Boolean = RedstoneLimiterSettings.enabled

    // 检测并判定是否阻断，返回 true 表示已阻断
    fun onRedstoneEvent(worldName: String, x: Int, y: Int, z: Int, blockType: String): Boolean {
        val now = System.currentTimeMillis()
        val key = TrackKey(worldName, x, y, z, intern = true)
        val windowMs = RedstoneLimiterSettings.windowSeconds * 1000L

        val window = tracker.computeIfAbsent(key) {
            currentTracked.incrementAndGet()
            SlidingWindow(windowMs)
        }
        window.record(now)
        val frequency = window.getFrequency(now)
        val threshold = RedstoneLimiterSettings.thresholdActivationsPerSecond

        if (frequency > threshold) {
            totalBlocked.incrementAndGet()
            notifyAdmins(key, blockType, frequency)
            return true
        }
        return false
    }

    fun getStats(): StatsSnapshot {
        return StatsSnapshot(
            totalBlocked = totalBlocked.get(),
            currentTracked = currentTracked.get(),
            enabledWorlds = RedstoneLimiterSettings.enabledWorlds,
        )
    }

    // 向在线 OP 广播阻断通知（异步执行，不阻塞事件处理）
    private fun notifyAdmins(key: TrackKey, blockType: String, frequency: Double) {
        if (!RedstoneLimiterSettings.notifyEnabled) {
            info("[RedstoneLimiter] 阻断高频红石: ${key.worldName} (${key.x},${key.y},${key.z}) $blockType 频率: ${"%.1f".format(frequency)}/s")
            return
        }

        val now = System.currentTimeMillis()
        val cooldownMs = RedstoneLimiterSettings.notifyCooldownSeconds * 1000L
        val lastNotify = notifyCooldowns.getOrDefault(key, 0L)

        if (now - lastNotify < cooldownMs) {
            return
        }
        notifyCooldowns[key] = now

        val message = TextUtils.parse(RedstoneLimiterSettings.notifyMessage
            .resolvePlaceholders(
                "{world}" to WorldNameMapper.resolve(key.worldName),
                "{x}" to key.x.toString(),
                "{y}" to key.y.toString(),
                "{z}" to key.z.toString(),
                "{block}" to blockType,
                "{frequency}" to "%.1f".format(frequency)
            ))

        info("[RedstoneLimiter] 阻断高频红石: ${key.worldName} (${key.x},${key.y},${key.z}) $blockType 频率: ${"%.1f".format(frequency)}/s")

        // Folia: 异步遍历在线 OP，通过 submitOnEntity 在实体线程发送消息
        submit(async = true) {
            onlinePlayers().forEach { proxy ->
                val player = proxy.cast<Player>() ?: return@forEach
                if (player.isOp) {
                    player.submitOnEntity {
                        runCatching { player.sendMessage(message) }.onFailure {
                            warning("[RedstoneLimiter] 向 OP ${player.name} 发送通知失败: ${it.message}")
                        }
                    }
                }
            }
        }
    }

    // 定期清理过期追踪点
    private fun scheduleCleanup() {
        val intervalTicks = RedstoneLimiterSettings.cleanupIntervalSeconds * 20L
        cleanupTask = submit(period = intervalTicks, async = true) {
            cleanupExpired()
        }
    }

    private fun cleanupExpired() {
        val now = System.currentTimeMillis()
        val expiryMs = RedstoneLimiterSettings.windowSeconds * 1000L * 2
        val maxPoints = RedstoneLimiterSettings.maxTrackedPoints

        // 移除过期条目
        val iter = tracker.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (entry.value.isExpired(now, expiryMs)) {
                iter.remove()
                notifyCooldowns.remove(entry.key)
                currentTracked.decrementAndGet()
            }
        }

        // 紧急回收：超出最大追踪点数时，淘汰最久未活跃的 10%
        val size = tracker.size
        if (size > maxPoints) {
            val evictCount = (size * 0.1).toInt().coerceAtLeast(1)
            val sortedByAccess = tracker.entries.sortedBy { it.value.lastAccessTime }
            for (i in 0 until evictCount.coerceAtMost(sortedByAccess.size)) {
                val entry = sortedByAccess[i]
                tracker.remove(entry.key)
                notifyCooldowns.remove(entry.key)
                currentTracked.decrementAndGet()
            }
            warning("[RedstoneLimiter] 追踪点数($size)超出上限($maxPoints)，已紧急回收 $evictCount 个最久未活跃条目")
        }
    }

}
