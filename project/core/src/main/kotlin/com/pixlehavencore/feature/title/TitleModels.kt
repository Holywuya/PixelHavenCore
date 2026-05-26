package com.pixlehavencore.feature.title

import java.util.UUID

data class TitleDefinition(
    val id: String,
    val displayName: String,
    val description: List<String>,
    val icon: String,
    val category: String,
    val rarity: String,
    val permission: String,
    val craftEngineDisplay: String?,
    val sourcePath: String,
)

data class PlayerTitleEntry(
    val titleId: String,
    val obtainedAt: Long,
    val expiresAt: Long,
) {
    val isPermanent: Boolean get() = expiresAt == 0L

    fun isExpired(now: Long = System.currentTimeMillis()): Boolean =
        !isPermanent && expiresAt > 0 && now >= expiresAt
}

data class PlayerTitleState(
    val playerUuid: UUID,
    val playerName: String,
    val activeTitleId: String?,
    val ownedTitles: List<PlayerTitleEntry>,
    val updatedAt: Long,
)

data class TitlePreview(
    val definition: TitleDefinition,
    val entry: PlayerTitleEntry?,
    val isActive: Boolean,
    val isExpired: Boolean,
    val remainingTime: Long?,
)
