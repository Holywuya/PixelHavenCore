package com.pixlehavencore.feature.flight

import com.pixlehavencore.util.DataStore
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import java.util.UUID

object FlightBonusStorage {

    private const val TABLE_NAME = "flight_bonus"
    private const val KEY_BONUS = "permanent_bonus"
    private const val KEY_BASE = "daily_base"
    private const val KEY_DAY = "daily_day"

    private val store = DataStore(TABLE_NAME)

    fun init() {
        store.init()
    }

    fun close() {
        store.close()
    }

    fun loadBonus(uuid: UUID): Int {
        return runCatching {
            store.get(uuid.toString(), KEY_BONUS)?.toIntOrNull() ?: 0
        }.getOrElse { ex ->
            warning("[FlightBonus] 读取额外时间失败($uuid): ${ex.message}")
            0
        }
    }

    fun saveBonus(uuid: UUID, bonus: Int) {
        submitAsync {
            runCatching {
                store.set(uuid.toString(), KEY_BONUS, bonus.toString())
            }.onFailure { ex ->
                warning("[FlightBonus] 保存额外时间失败($uuid): ${ex.message}")
            }
        }
    }

    fun loadBaseSeconds(uuid: UUID): Int? {
        return runCatching {
            store.get(uuid.toString(), KEY_BASE)?.toIntOrNull()
        }.getOrElse { ex ->
            warning("[FlightBonus] 读取基础时间失败($uuid): ${ex.message}")
            null
        }
    }

    fun loadDay(uuid: UUID): Long? {
        return runCatching {
            store.get(uuid.toString(), KEY_DAY)?.toLongOrNull()
        }.getOrElse { ex ->
            warning("[FlightBonus] 读取日期失败($uuid): ${ex.message}")
            null
        }
    }

    fun saveBaseInfo(uuid: UUID, baseSeconds: Int, effectiveDay: Long) {
        submitAsync {
            runCatching {
                store.set(uuid.toString(), KEY_BASE, baseSeconds.toString())
                store.set(uuid.toString(), KEY_DAY, effectiveDay.toString())
            }.onFailure { ex ->
                warning("[FlightBonus] 保存基础信息失败($uuid): ${ex.message}")
            }
        }
    }
}
