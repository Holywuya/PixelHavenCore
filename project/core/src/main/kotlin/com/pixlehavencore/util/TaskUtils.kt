package com.pixlehavencore.util

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import taboolib.common.util.supplierLazy
import java.lang.reflect.Method

private val cancelMethod = supplierLazy<Any, Method?>(typeIsolation = true) { task ->
    task.javaClass.methods
        .firstOrNull { it.name == "cancel" && it.parameterTypes.isEmpty() }
}

/**
 * 通过反射安全取消 TabooLib 异步/定时任务。
 * TabooLib 的 submit/submitAsync 返回的对象类型不公开，只能通过反射调用 cancel()。
 */
fun Any?.cancelTaskSafely() {
    if (this == null) return
    runCatching {
        cancelMethod[this]?.invoke(this)
    }
}

/**
 * Per-key 细粒度锁，避免对整个缓存加锁。
 *
 * 用法：
 * ```
 * private val locks = PerKeyLock<UUID>()
 * synchronized(locks[playerUuid]) { ... }
 * ```
 */
class PerKeyLock<K> {
    private val map = ConcurrentHashMap<K, Any>()
    operator fun get(key: K): Any = map.computeIfAbsent(key) { Any() }
    fun remove(key: K) { map.remove(key) }
    fun clear() { map.clear() }
}

/**
 * 将毫秒格式化为可读的天/时/分/秒字符串。
 */
fun Long.formatDuration(): String {
    if (this <= 0) return "0秒"
    val days = this / MILLIS_PER_DAY
    val hours = (this % MILLIS_PER_DAY) / MILLIS_PER_HOUR
    val minutes = (this % MILLIS_PER_HOUR) / MILLIS_PER_MINUTE
    val seconds = (this % MILLIS_PER_MINUTE) / MILLIS_PER_SECOND
    return buildString {
        if (days > 0) append("${days}天")
        if (hours > 0) append("${hours}小时")
        if (minutes > 0) append("${minutes}分")
        if (seconds > 0 && isEmpty()) append("${seconds}秒")
    }
}

/** 解析时长字符串为毫秒。支持 "30d"/"7h"/"60m"/"30s"/"permanent"。默认单位：天。 */
fun parseDurationMillis(input: String): Long {
    val trimmed = input.trim().lowercase()
    if (trimmed == "permanent" || trimmed == "perm" || trimmed == "永久") return 0L
    val multiplier = when {
        trimmed.endsWith("d") -> MILLIS_PER_DAY
        trimmed.endsWith("h") -> MILLIS_PER_HOUR
        trimmed.endsWith("m") -> MILLIS_PER_MINUTE
        trimmed.endsWith("s") -> MILLIS_PER_SECOND
        else -> MILLIS_PER_DAY
    }
    val number = trimmed.replace(Regex("[dhms]"), "").trim().toLongOrNull() ?: return 0L
    return number * multiplier
}

const val MILLIS_PER_DAY = 86_400_000L
const val MILLIS_PER_HOUR = 3_600_000L
const val MILLIS_PER_MINUTE = 60_000L
const val MILLIS_PER_SECOND = 1_000L
