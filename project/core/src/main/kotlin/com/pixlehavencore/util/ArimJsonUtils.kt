package com.pixlehavencore.util

import com.google.gson.JsonElement
import top.maplex.arim.tools.gson.GsonUtils

/**
 * 统一封装 Arim GsonUtils，减少模块内直接依赖细节。
 */
object ArimJsonUtils {

    fun toJson(value: Any?): String {
        return GsonUtils.toJson(value)
    }

    fun <T> fromJson(json: String, clazz: Class<T>): T {
        return GsonUtils.fromJson(json, clazz)
    }

    fun parseTree(json: String): JsonElement? {
        return runCatching { GsonUtils.getGson().fromJson(json, JsonElement::class.java) }.getOrNull()
    }

    fun gson() = GsonUtils.getGson()
}
