package com.pixlehavencore.feature.playerstate

import org.bukkit.Location
import java.util.UUID

object PlayerStateService {

    fun init() {
        PlayerStateSettings.init()
        PlayerStateStorage.init()
    }

    fun reload() {
        PlayerStateSettings.reload()
        PlayerStateStorage.reload()
    }

    fun stop() {
        PlayerStateStorage.close()
    }

    fun isEnabled(): Boolean = PlayerStateSettings.enabled

    // ========== 登录态 ==========

    fun getOrCreate(uuid: UUID): PlayerStateData = PlayerStateStorage.getOrCreate(uuid)

    fun get(uuid: UUID): PlayerStateData? = PlayerStateStorage.get(uuid)

    fun loadFromDatabase(uuid: UUID, playerName: String): PlayerStateData? =
        PlayerStateStorage.loadFromDatabase(uuid, playerName)

    fun isFirstJoin(uuid: UUID): Boolean {
        val data = PlayerStateStorage.get(uuid) ?: return true
        return data.firstJoinTime == 0L && data.joinCount == 0
    }

    fun getFirstJoinTime(uuid: UUID): Long? {
        val data = get(uuid) ?: return null
        return data.firstJoinTime.takeIf { it > 0 }
    }

    fun getLastJoinTime(uuid: UUID): Long? {
        val data = get(uuid) ?: return null
        return data.lastJoinTime.takeIf { it > 0 }
    }

    fun getJoinCount(uuid: UUID): Int {
        return get(uuid)?.joinCount ?: 0
    }

    fun getLastQuitTime(uuid: UUID): Long? {
        val data = get(uuid) ?: return null
        return data.lastQuitTime.takeIf { it > 0 }
    }

    fun getPlayerName(uuid: UUID): String? {
        return get(uuid)?.playerName?.takeIf { it.isNotBlank() }
    }

    // ========== 位置 ==========

    fun getLastDeathLocation(uuid: UUID): Location? {
        val raw = get(uuid)?.lastDeathLocation?.takeIf { it.isNotBlank() } ?: return null
        return PlayerStateStorage.deserializeLocation(raw)
    }

    fun setLastDeathLocation(uuid: UUID, loc: Location) {
        val data = getOrCreate(uuid)
        data.lastDeathLocation = PlayerStateStorage.serializeLocation(loc) ?: return
        PlayerStateStorage.saveImmediate(uuid)
    }

    fun getLastTeleportLocation(uuid: UUID): Location? {
        val raw = get(uuid)?.lastTeleportLocation?.takeIf { it.isNotBlank() } ?: return null
        return PlayerStateStorage.deserializeLocation(raw)
    }

    fun setLastTeleportLocation(uuid: UUID, loc: Location) {
        val data = getOrCreate(uuid)
        data.lastTeleportLocation = PlayerStateStorage.serializeLocation(loc) ?: return
        PlayerStateStorage.saveImmediate(uuid)
    }

    // ========== 管理 ==========

    fun reset(uuid: UUID) {
        val data = getOrCreate(uuid)
        data.firstJoinTime = 0L
        data.lastJoinTime = 0L
        data.lastQuitTime = 0L
        data.joinCount = 0
        data.lastDeathLocation = ""
        data.lastTeleportLocation = ""
        PlayerStateStorage.saveImmediate(uuid)
    }
}
