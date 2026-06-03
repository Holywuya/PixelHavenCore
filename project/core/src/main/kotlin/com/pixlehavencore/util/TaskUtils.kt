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
    }.onFailure { ex ->
        // 仅记录警告，不中断流程
        taboolib.common.platform.function.warning("[TaskUtils] 取消任务失败: ${ex.message}")
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

/** 解析时长字符串为毫秒。支持 "30d"/"7h"/"60m"/"30s"/"permanent"。默认单位：天。 */

/** 解析时长字符串为毫秒。支持 "30d"/"7h"/"60m"/"30s"/"permanent"。默认单位：天。 */
/**
 * 解析时长字符串为毫秒
 * 支持格式：数字+单位（d/h/m/s），纯数字默认为天
 * 例如："1d" = 1天, "2h" = 2小时, "30m" = 30分钟, "60s" = 60秒, "30" = 30天
 * 特殊值："permanent"/"perm"/"永久" 返回 0L
 */
fun parseDurationMillis(input: String): Long {
    val trimmed = input.trim().lowercase()
    if (trimmed == "permanent" || trimmed == "perm" || trimmed == "永久") return 0L
    
    // 提取数字部分和单位部分
    val numberPart = trimmed.takeWhile { it.isDigit() || it == '.' }
    val unitPart = trimmed.substring(numberPart.length).trim()
    
    val multiplier = when (unitPart) {
        "d", "天" -> MILLIS_PER_DAY
        "h", "小时" -> MILLIS_PER_HOUR
        "m", "分", "分钟" -> MILLIS_PER_MINUTE
        "s", "秒" -> MILLIS_PER_SECOND
        "" -> MILLIS_PER_DAY  // 纯数字默认为天
        else -> return 0L  // 未知单位返回 0（永久）
    }
    
    val number = numberPart.toLongOrNull() ?: return 0L
    return number * multiplier
}

const val MILLIS_PER_DAY = 86_400_000L
const val MILLIS_PER_HOUR = 3_600_000L
const val MILLIS_PER_MINUTE = 60_000L
const val MILLIS_PER_SECOND = 1_000L

/**
 * 格式化时长（毫秒）为人类可读的字符串
 * 例如：1天2小时30分钟、2小时15分钟、45分钟、30秒
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
        if (minutes > 0) append("${minutes}分钟")
        if (seconds > 0 && isEmpty()) append("${seconds}秒")
    }
}

/**
 * 格式化时长（秒）为人类可读的字符串
 */
fun Int.formatDuration(): String = this.toLong().formatDuration()
