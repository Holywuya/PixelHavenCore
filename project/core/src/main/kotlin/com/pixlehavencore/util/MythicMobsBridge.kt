package com.pixlehavencore.util

import io.lumine.mythic.bukkit.BukkitAPIHelper
import io.lumine.mythic.bukkit.MythicBukkit
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import taboolib.common.util.supplierLazy
import java.lang.reflect.Method
import java.lang.reflect.Field

data class MythicMobInfo(
    val id: String,
    val displayName: String,
    val level: Int,
)

object MythicMobsBridge {

    private const val MYTHIC_MOBS_PLUGIN = "MythicMobs"

    @Volatile
    private var availabilityResolved = false

    @Volatile
    private var available = false

    fun refreshAvailability(): Boolean {
        availabilityResolved = false
        val ready = isAvailable()
        if (ready) {
            info("[MythicMobs] 已接入 MythicMobs 原生 API。")
        } else {
            info("[MythicMobs] 未检测到可用的 MythicMobs。")
        }
        return ready
    }

    fun isAvailable(): Boolean {
        if (availabilityResolved) {
            return available
        }
        synchronized(this) {
            if (availabilityResolved) {
                return available
            }
            available = detectAvailability()
            availabilityResolved = true
            return available
        }
    }

    fun resolveMobInfo(entity: Entity): MythicMobInfo? {
        val apiHelper = getApiHelper() ?: return null
        val isMythicMob = runCatching {
            apiHelper.isMythicMob(entity)
        }.onFailure { ex ->
            warning("[MythicMobs] 检查实体失败: ${ex.message}")
        }.getOrDefault(false)
        if (!isMythicMob) {
            return null
        }

        val activeMob = runCatching {
            apiHelper.getMythicMobInstance(entity)
        }.onFailure { ex ->
            warning("[MythicMobs] 获取 ActiveMob 失败: ${ex.message}")
        }.getOrNull() ?: return null

        return runCatching {
            val mobId = activeMob.type.internalName.trim()
            if (mobId.isBlank()) {
                return null
            }
            val displayName = activeMob.displayName.trim()
            MythicMobInfo(mobId, displayName.ifBlank { mobId }, resolveMobLevel(activeMob))
        }.onFailure { ex ->
            warning("[MythicMobs] 解析信息失败: ${ex.message}")
        }.getOrNull()
    }

    private val levelMethod = supplierLazy<Any, Method?>(typeIsolation = true) { activeMob ->
        listOf("getLevel", "level")
            .firstNotNullOfOrNull { methodName ->
                runCatching {
                    activeMob.javaClass.methods
                        .firstOrNull { it.name == methodName && it.parameterCount == 0 }
                }.getOrNull()
            }
    }

    private val levelField = supplierLazy<Any, Field?>(typeIsolation = true) { activeMob ->
        runCatching {
            activeMob.javaClass.declaredFields
                .firstOrNull { it.name.equals("level", ignoreCase = true) }
                ?.apply { isAccessible = true }
        }.getOrNull()
    }

    private fun resolveMobLevel(activeMob: Any): Int {
        val method = levelMethod[activeMob]
        if (method != null) {
            val byMethod = runCatching {
                method.invoke(activeMob) as? Number
            }.getOrNull()?.toInt()
            if (byMethod != null && byMethod > 0) {
                return byMethod
            }
        }
        val field = levelField[activeMob]
        if (field != null) {
            val byField = runCatching {
                field.get(activeMob) as? Number
            }.getOrNull()?.toInt()
            if (byField != null) {
                return byField.coerceAtLeast(1)
            }
        }
        return 1
    }

    fun spawnMob(mobId: String, location: Location, level: Int = 1): Entity? {
        val apiHelper = getApiHelper() ?: return null
        return runCatching {
            apiHelper.spawnMythicMob(mobId, location, level.coerceAtLeast(1))
        }.onFailure { ex ->
            warning("[MythicMobs] 生成失败($mobId): ${ex.message}")
        }.getOrNull()
    }

    private fun getApiHelper(): BukkitAPIHelper? {
        if (!isAvailable()) {
            return null
        }
        return runCatching {
            MythicBukkit.inst().apiHelper
        }.onFailure { ex ->
            warning("[MythicMobs] 获取 API Helper 失败: ${ex.message}")
        }.getOrNull()
    }

    private fun detectAvailability(): Boolean {
        val plugin = Bukkit.getPluginManager().getPlugin(MYTHIC_MOBS_PLUGIN)
        if (plugin == null || !plugin.isEnabled) {
            return false
        }

        return runCatching {
            MythicBukkit.inst().apiHelper != null
        }.onFailure { ex ->
            warning("[MythicMobs] 检测 API 失败: ${ex.message}")
        }.getOrDefault(false)
    }
}
