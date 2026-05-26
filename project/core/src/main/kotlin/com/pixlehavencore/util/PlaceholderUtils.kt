package com.pixlehavencore.util

/**
 * 通用占位符替换工具。
 *
 * 所有模块的消息模板统一使用 `{key}` 风格占位符，通过此工具集中替换，
 * 避免各处散落 `.replace("{player}", ...)` 调用。
 */
object PlaceholderUtils {

    /**
     * 将文本中的所有占位符替换为对应值。
     *
     * @param map `{key}` → 值 的映射表
     */
    fun String.resolvePlaceholders(map: Map<String, String>): String {
        if (map.isEmpty()) return this
        var result = this
        // 按 key 长度降序替换，避免短 key 误匹配长 key 的前缀
        map.entries.sortedByDescending { it.key.length }.forEach { (key, value) ->
            result = result.replace(key, value)
        }
        return result
    }

    /**
     * 将文本中的所有占位符替换为对应值（vararg 版本）。
     *
     * 用法：
     * ```
     * msg.resolvePlaceholders("{player}" to name, "{world}" to worldName)
     * ```
     */
    fun String.resolvePlaceholders(vararg pairs: Pair<String, String>): String {
        return resolvePlaceholders(pairs.toMap())
    }
}
