package com.pixlehavencore.feature.playerinv

import com.google.gson.reflect.TypeToken
import com.pixlehavencore.bridge.TextBridge
import com.pixlehavencore.util.PlaceholderUtils.resolvePlaceholders
import com.pixlehavencore.util.DatabaseUtils
import com.pixlehavencore.util.EconomyUtils
import com.pixlehavencore.util.InventoryUtils
import com.pixlehavencore.util.ItemUtils
import com.pixlehavencore.util.TextUtils
import com.pixlehavencore.util.ArimJsonUtils
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.warning
import taboolib.expansion.MultipleHandler
import taboolib.platform.util.submit as submitOnEntity
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import taboolib.platform.util.PlayerSessionMap

object PlayerInvService {

    private val itemStackMapListType = object : TypeToken<List<Map<String, String>?>>() {}.type
    private val lockedSlotKey = NamespacedKey("phcore", "playerinv_locked_slot")
    private val manageActionKey = NamespacedKey("phcore", "playerinv_manage_action")
    private var dataSource: HikariDataSource? = null
    private var personalDataHandler: MultipleHandler? = null
    private val openSessions = ConcurrentHashMap<Int, Session>()
    private val pendingMemberInputs = PlayerSessionMap<PendingMemberInput>({ throw IllegalStateException() }, manualRelease = true)

    private const val PERSONAL_KEY_SIZE = "size"
    private const val PERSONAL_KEY_CHUNK_COUNT = "chunk_count"
    private const val PERSONAL_KEY_CHUNK_PREFIX = "chunk_"
    private const val PERSONAL_CHUNK_SIZE = 96

    private const val MANAGE_SLOT_MEMBERS = 10
    private const val MANAGE_SLOT_ADD = 12
    private const val MANAGE_SLOT_TOGGLE_VISIBILITY = 13
    private const val MANAGE_SLOT_REMOVE = 14
    private const val MANAGE_SLOT_BACK = 16
    private const val SHARED_MANAGE_ENTRY_SLOT = 49

    fun init() {
        reload()
    }

    fun reload() {
        PlayerInvSettings.reload()
        closeStorage()
        openSessions.clear()
        pendingMemberInputs.clear()

        if (!PlayerInvSettings.enabled) {
            info("[Warehouse] 模块已禁")
            return
        }

        runCatching {
            dataSource = DatabaseUtils.newHikariDataSource("WarehousePool", 4, 1)
            personalDataHandler = DatabaseUtils.newPlayerDataHandler(personalDataTableName(), autoHook = true, syncTick = 200L)
            ensureTables()
            info("[Warehouse] 数据库已连接")
        }.onFailure { ex ->
            warning("[Warehouse] 初始化失 ${ex.message}")
            closeStorage()
        }
    }

    fun close() {
        closeStorage()
        openSessions.clear()
        pendingMemberInputs.clear()
    }

    fun isReady(): Boolean {
        return dataSource != null && personalDataHandler != null
    }

    fun countPersonalMaterials(owner: UUID, specs: Collection<String>): Map<String, Int> {
        if (!isReady() || specs.isEmpty()) {
            return emptyMap()
        }
        val uniqueSpecs = specs.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (uniqueSpecs.isEmpty()) {
            return emptyMap()
        }
        val personal = loadPersonal(owner)
        return uniqueSpecs.associateWith { spec ->
            personal.items.filterNotNull().filter { ItemUtils.matchesSpec(spec, it) }.sumOf { it.amount }
        }
    }

    fun consumePersonalMaterials(owner: UUID, required: Map<String, Int>): Boolean {
        if (!isReady()) {
            return false
        }
        val normalized = required.entries
            .mapNotNull { (spec, amount) -> spec.trim().takeIf { it.isNotBlank() }?.let { it to amount.coerceAtLeast(0) } }
            .filter { it.second > 0 }
        if (normalized.isEmpty()) {
            return true
        }
        val current = loadPersonal(owner)
        val items = current.items.toMutableList()
        val counts = normalized.associate { (spec, _) ->
            spec to items.filterNotNull().filter { ItemUtils.matchesSpec(spec, it) }.sumOf { it.amount }
        }
        if (normalized.any { (spec, amount) -> (counts[spec] ?: 0) < amount }) {
            return false
        }
        normalized.forEach { (spec, amount) ->
            var remaining = amount
            for (index in items.indices) {
                val stack = items[index] ?: continue
                if (!ItemUtils.matchesSpec(spec, stack)) {
                    continue
                }
                val used = remaining.coerceAtMost(stack.amount)
                stack.amount -= used
                items[index] = if (stack.amount <= 0) null else stack
                remaining -= used
                if (remaining <= 0) {
                    break
                }
            }
        }
        return savePersonal(owner, current.size, items)
    }

    fun normalizeSize(raw: Int): Int {
        val clamped = raw.coerceIn(9, PlayerInvSettings.maxRows * 9)
        return if (clamped % 9 == 0) clamped else clamped - (clamped % 9)
    }

    fun openSelf(player: Player): Boolean {
        if (!isReady()) {
            player.sendMessage(TextUtils.parse(PlayerInvSettings.saveFailedMessage))
            return false
        }
        openSelfAsync(player) { }
        return true
    }

    fun openSelfAsync(player: Player, callback: (Boolean) -> Unit) {
        if (!isReady()) {
            callback(false)
            return
        }
        val ownerId = player.uniqueId
        val playerName = player.name
        val rowsByPerm = PlayerInvSettings.resolvePersonalRows(PlayerInvSettings.PlayerLike { permission -> player.hasPermission(permission) })
        val sizeByPerm = rowsByPerm * 9
        submit(async = true) {
            val current = loadPersonal(ownerId)
            val adjusted = adjustPersonalCapacity(ownerId, current, sizeByPerm)
            val autoSorted = adjusted?.record?.let { InventoryUtils.compact(it.items) }
            player.submitOnRegion {
                if (!player.isOnline) {
                    return@submitOnRegion
                }
                if (adjusted == null || autoSorted == null) {
                    callback(false)
                    return@submitOnRegion
                }
                if (adjusted.overflow.isNotEmpty()) {
                    deliverOverflowItems(player, adjusted.overflow)
                }
                val opened = openSession(
                    viewer = player,
                    title = PlayerInvSettings.title.resolvePlaceholders("{player}" to playerName),
                    size = adjusted.record.size,
                    items = autoSorted,
                    type = SessionType.PERSONAL,
                    owner = ownerId,
                    sharedId = null,
                    sharedName = null,
                    canSort = false,
                    sharedUnlockedSlots = 0
                )
                callback(opened)
            }
        }
    }

    private fun adjustPersonalCapacity(owner: UUID, current: PersonalRecord, targetSize: Int): PersonalAdjustResult? {
        val finalSize = normalizeInventorySize(targetSize, PlayerInvSettings.defaultRows * 9)
        if (current.size == finalSize) {
            return PersonalAdjustResult(current, emptyList())
        }

        if (current.size < finalSize) {
            val expanded = PersonalRecord(
                size = finalSize,
                items = resizeNullableTo(finalSize, current.items.toList())
            )
            if (!savePersonal(owner, expanded.size, expanded.items.toList())) {
                return null
            }
            return PersonalAdjustResult(expanded, emptyList())
        }

        val retained = resizeNullableTo(finalSize, current.items.take(finalSize))
        val overflow = current.items.drop(finalSize)
            .mapNotNull { item ->
                item?.clone()?.takeIf { !it.type.isAir && it.amount > 0 }
            }

        if (!savePersonal(owner, finalSize, retained.toList())) {
            return null
        }

        return PersonalAdjustResult(PersonalRecord(size = finalSize, items = retained), overflow)
    }

    private fun deliverOverflowItems(player: Player, items: List<ItemStack>) {
        items.forEach { item ->
            val leftovers = player.inventory.addItem(item)
            leftovers.values.forEach { left ->
                player.world.dropItemNaturally(player.location, left)
            }
        }
        player.sendMessage(TextUtils.parse(PlayerInvSettings.personalOverflowFallbackMessage))
    }

    fun openOther(viewer: Player, target: OfflinePlayer): Boolean {
        if (!isReady()) {
            viewer.sendMessage(TextUtils.parse(PlayerInvSettings.saveFailedMessage))
            return false
        }
        openOtherAsync(viewer, target) { }
        return true
    }

    fun openOtherAsync(viewer: Player, target: OfflinePlayer, callback: (Boolean) -> Unit) {
        if (!isReady()) {
            callback(false)
            return
        }
        val ownerId = target.uniqueId
        val title = PlayerInvSettings.title.resolvePlaceholders("{player}" to (target.name ?: ownerId.toString()))
            submit(async = true) {
                val personal = loadPersonal(ownerId)
                val autoSorted = InventoryUtils.compact(personal.items)
                viewer.submitOnRegion {
                    if (!viewer.isOnline) {
                    return@submitOnRegion
                }
                val opened = openSession(
                    viewer = viewer,
                    title = title,
                    size = personal.size,
                    items = autoSorted,
                    type = SessionType.PERSONAL,
                    owner = ownerId,
                    sharedId = null,
                    sharedName = null,
                    canSort = false,
                    sharedUnlockedSlots = 0
                )
                callback(opened)
            }
        }
    }

    fun createShared(owner: Player, rawName: String): SharedCreateResult {
        if (!isReady()) {
            return SharedCreateResult.FAILED
        }
        val name = normalizeSharedName(rawName) ?: return SharedCreateResult.INVALID_NAME
        return runCatching {
            withConnection { connection ->
                connection.autoCommit = false
                try {
                    val ownerRecord = ensureOwnerRecord(connection, owner.uniqueId)
                    if (ownerRecord.sharedQuota <= 0) {
                        connection.rollback()
                        return@withConnection SharedCreateResult.NO_QUOTA
                    }
                    if (findSharedByName(connection, name) != null) {
                        connection.rollback()
                        return@withConnection SharedCreateResult.EXISTS
                    }

                    val sharedId = UUID.randomUUID()
                    val now = Timestamp(System.currentTimeMillis())
                    val quotedName = DatabaseUtils.quoted("name")
                    connection.prepareStatement(
                        "INSERT INTO ${PlayerInvSettings.sharedTable} " +
                            "(shared_id, owner_uuid, $quotedName, size, unlocked_slots, inventory_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                    ).use { statement ->
                        statement.setString(1, sharedId.toString())
                        statement.setString(2, owner.uniqueId.toString())
                        statement.setString(3, name)
                        statement.setInt(4, PlayerInvSettings.sharedMaxRows * 9)
                        statement.setInt(5, PlayerInvSettings.sharedInitialRows * 9)
                        statement.setString(6, serializeInventory(emptyList()))
                        statement.setTimestamp(7, now)
                        statement.setTimestamp(8, now)
                        statement.executeUpdate()
                    }

                    upsertSharedMember(connection, sharedId, owner.uniqueId, SharedRole.OWNER)
                    updateSharedQuota(connection, owner.uniqueId, ownerRecord.sharedQuota - 1)
                    connection.commit()
                    SharedCreateResult.OK
                } catch (ex: Exception) {
                    runCatching { connection.rollback() }
                    throw ex
                } finally {
                    runCatching { connection.autoCommit = true }
                }
            }
        }.onFailure { ex ->
            warning("[Warehouse] 创建共享仓库失败(${owner.name}): ${ex.message}")
        }.getOrDefault(SharedCreateResult.FAILED)
    }

    fun openShared(viewer: Player, rawName: String, forceAdmin: Boolean = false): SharedOpenResult {
        if (!isReady()) {
            viewer.sendMessage(TextUtils.parse(PlayerInvSettings.saveFailedMessage))
            return SharedOpenResult.FAILED
        }
        val name = normalizeSharedName(rawName) ?: return SharedOpenResult.NOT_FOUND
        return runCatching {
            withConnection { connection ->
                val shared = findSharedByName(connection, name) ?: return@withConnection SharedOpenResult.NOT_FOUND
                val isAdmin = viewer.hasPermission("phcore.admin")
                // 公开仓库跳过成员检查，私有仓库才需要验role
                val hasAccess = isAdmin || forceAdmin || shared.isPublic ||
                    getSharedRole(connection, shared.id, viewer.uniqueId) != null
                if (!hasAccess) {
                    return@withConnection SharedOpenResult.NO_ACCESS
                }

                val baseItems = sanitizeSharedInventory(deserializeInventory(shared.inventoryJson, shared.size))
                val sorted = InventoryUtils.compact(baseItems)
                openSession(
                    viewer = viewer,
                    title = PlayerInvSettings.sharedTitle.resolvePlaceholders("{name}" to shared.name),
                    size = shared.size,
                    items = sorted,
                    type = SessionType.SHARED,
                    owner = shared.owner,
                    sharedId = shared.id,
                    sharedName = shared.name,
                    canSort = false,
                    sharedUnlockedSlots = shared.unlockedSlots
                )
                SharedOpenResult.OK
            }
        }.onFailure { ex ->
            warning("[Warehouse] 打开共享仓库失败(${viewer.name}, $name): ${ex.message}")
        }.getOrDefault(SharedOpenResult.FAILED)
    }

    fun openSharedAsync(viewer: Player, rawName: String, forceAdmin: Boolean = false, callback: (SharedOpenResult) -> Unit) {
        if (!isReady()) {
            callback(SharedOpenResult.FAILED)
            return
        }
        val name = normalizeSharedName(rawName)
        if (name == null) {
            callback(SharedOpenResult.NOT_FOUND)
            return
        }
        val viewerId = viewer.uniqueId
        val isAdmin = viewer.hasPermission("phcore.admin")

        submit(async = true) {
            val prepared = runCatching {
                withConnection { connection ->
                    val shared = findSharedByName(connection, name)
                        ?: return@withConnection SharedOpenAsyncResult(SharedOpenResult.NOT_FOUND, null)
                    // 公开仓库跳过成员检查，私有仓库才需要验role
                    val hasAccess = isAdmin || forceAdmin || shared.isPublic ||
                        getSharedRole(connection, shared.id, viewerId) != null
                    if (!hasAccess) {
                        return@withConnection SharedOpenAsyncResult(SharedOpenResult.NO_ACCESS, null)
                    }

                    val baseItems = sanitizeSharedInventory(deserializeInventory(shared.inventoryJson, shared.size))
                    val sorted = InventoryUtils.compact(baseItems)
                    SharedOpenAsyncResult(
                        result = SharedOpenResult.OK,
                        payload = SharedOpenPayload(
                            owner = shared.owner,
                            sharedId = shared.id,
                            sharedName = shared.name,
                            size = shared.size,
                            sharedUnlockedSlots = shared.unlockedSlots,
                            items = sorted
                        )
                    )
                }
            }.onFailure { ex ->
                warning("[Warehouse] 打开共享仓库失败(${viewer.name}, $name): ${ex.message}")
            }.getOrDefault(SharedOpenAsyncResult(SharedOpenResult.FAILED, null))

            viewer.submitOnRegion {
                if (!viewer.isOnline) {
                    return@submitOnRegion
                }
                val payload = prepared.payload
                if (prepared.result == SharedOpenResult.OK && payload != null) {
                    openSession(
                        viewer = viewer,
                        title = PlayerInvSettings.sharedTitle.resolvePlaceholders("{name}" to payload.sharedName),
                        size = payload.size,
                        items = payload.items,
                        type = SessionType.SHARED,
                        owner = payload.owner,
                        sharedId = payload.sharedId,
                        sharedName = payload.sharedName,
                        canSort = false,
                        sharedUnlockedSlots = payload.sharedUnlockedSlots
                    )
                }
                callback(prepared.result)
            }
        }
    }

    fun setPersonalSize(target: OfflinePlayer, size: Int): Boolean {
        if (!isReady()) {
            return false
        }
        val finalSize = normalizeSize(size)
        val current = loadPersonal(target.uniqueId)
        savePersonal(target.uniqueId, finalSize, current.items.toList())
        return true
    }

    fun addSharedMember(operator: Player, rawName: String, target: OfflinePlayer): SharedMemberResult {
        return changeSharedMember(operator, rawName, target, add = true)
    }

    fun removeSharedMember(operator: Player, rawName: String, target: OfflinePlayer): SharedMemberResult {
        return changeSharedMember(operator, rawName, target, add = false)
    }

    fun listSharedMembers(operator: Player, rawName: String): SharedMemberListResult {
        if (!isReady()) {
            return SharedMemberListResult.FAILED
        }
        val name = normalizeSharedName(rawName) ?: return SharedMemberListResult.NOT_FOUND
        return runCatching {
            withConnection { connection ->
                val shared = findSharedByName(connection, name) ?: return@withConnection SharedMemberListResult.NOT_FOUND
                if (!canManageShared(connection, operator, shared)) {
                    return@withConnection SharedMemberListResult.NO_ACCESS
                }
                SharedMemberListResult.OK(querySharedMembers(connection, shared.id))
            }
        }.onFailure { ex ->
            warning("[Warehouse] 查询共享成员失败(${operator.name}, $name): ${ex.message}")
        }.getOrDefault(SharedMemberListResult.FAILED)
    }

    fun listSharedByOwner(owner: OfflinePlayer): List<String> {
        if (!isReady()) return emptyList()
        return runCatching {
            withConnection { connection ->
                val quotedName = DatabaseUtils.quoted("name")
                connection.prepareStatement(
                    "SELECT $quotedName FROM ${PlayerInvSettings.sharedTable} WHERE owner_uuid = ? ORDER BY $quotedName ASC"
                ).use { statement ->
                    statement.setString(1, owner.uniqueId.toString())
                    statement.executeQuery().use { result ->
                        buildList {
                            while (result.next()) {
                                add(result.getString("name"))
                            }
                        }
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun upgradeShared(operator: Player, rawName: String): SharedUpgradeResult {
        if (!isReady()) {
            return SharedUpgradeResult.FAILED
        }
        val name = normalizeSharedName(rawName) ?: return SharedUpgradeResult.NOT_FOUND
        return runCatching {
            withConnection { connection ->
                val shared = findSharedByName(connection, name) ?: return@withConnection SharedUpgradeResult.NOT_FOUND
                if (!canManageShared(connection, operator, shared)) {
                    return@withConnection SharedUpgradeResult.NO_ACCESS
                }
                if (shared.size >= PlayerInvSettings.sharedMaxRows * 9) {
                    return@withConnection SharedUpgradeResult.REACHED_MAX
                }
                val next = (shared.size + 9).coerceAtMost(PlayerInvSettings.sharedMaxRows * 9)
                connection.prepareStatement(
                    "UPDATE ${PlayerInvSettings.sharedTable} SET size = ?, updated_at = ? WHERE shared_id = ?"
                ).use { statement ->
                    statement.setInt(1, next)
                    statement.setTimestamp(2, Timestamp(System.currentTimeMillis()))
                    statement.setString(3, shared.id.toString())
                    statement.executeUpdate()
                }
                SharedUpgradeResult.OK(next)
            }
        }.onFailure { ex ->
            warning("[Warehouse] 升级共享仓库失败(${operator.name}, $name): ${ex.message}")
        }.getOrDefault(SharedUpgradeResult.FAILED)
    }

    fun grantSharedQuota(target: OfflinePlayer, amount: Int): Boolean {
        if (!isReady() || amount <= 0) {
            return false
        }
        return runCatching {
            withConnection { connection ->
                val owner = ensureOwnerRecord(connection, target.uniqueId)
                updateSharedQuota(connection, target.uniqueId, owner.sharedQuota + amount)
                true
            }
        }.onFailure { ex ->
            warning("[Warehouse] 发放共享仓库创建次数失败(${target.name}): ${ex.message}")
        }.getOrDefault(false)
    }

    fun handleMemberChatInput(player: Player, message: String): Boolean {
        val pending = pendingMemberInputs.get(player.uniqueId) ?: return false
        if (message.equals("cancel", ignoreCase = true)) {
            pendingMemberInputs.remove(player.uniqueId)
            player.sendMessage(TextUtils.parse(PlayerInvSettings.chatInputCancelled))
            reopenManageAfterChat(player, pending.sharedName)
            return true
        }
        val target = resolveOfflinePlayer(message.trim())
        if (target == null) {
            player.sendMessage(TextUtils.parse(PlayerInvSettings.chatInputPlayerNotFound.resolvePlaceholders("{player}" to message.trim())))
            return true
        }

        // Folia: DB 操作必须在异步线程执行，回调通过 submitOnRegion 回到玩家区域线程
        val sharedName = pending.sharedName
        val mode = pending.mode
        pendingMemberInputs.remove(player.uniqueId)
        submit(async = true) {
            val result = if (mode == PendingMode.ADD) {
                addSharedMember(player, sharedName, target)
            } else {
                removeSharedMember(player, sharedName, target)
            }
            player.submitOnEntity {
                when (result) {
                    SharedMemberResult.OK -> player.sendMessage(TextUtils.parse(PlayerInvSettings.chatInputDone))
                    SharedMemberResult.NO_ACCESS -> player.sendMessage(TextUtils.parse(PlayerInvSettings.sharedManageNoPermission))
                    SharedMemberResult.NOT_FOUND -> player.sendMessage(TextUtils.parse(PlayerInvSettings.sharedNotFoundMessage.resolvePlaceholders("{name}" to sharedName)))
                    SharedMemberResult.CANNOT_REMOVE_OWNER -> player.sendMessage(TextUtils.parse("&c不能移除共享仓库创建者"))
                    else -> player.sendMessage(TextUtils.parse("&c操作失败"))
                }
                reopenManageAfterChat(player, sharedName)
            }
        }
        return true
    }

    fun handleInventoryClick(player: Player, inventory: Inventory, slot: Int, click: ClickType): Boolean {
        val session = openSessions[System.identityHashCode(inventory)] ?: return false
        if (session.viewer != player.uniqueId || slot !in 0 until inventory.size) {
            return false
        }

        if (session.type == SessionType.SHARED_MANAGE) {
            return handleManageClick(player, session, slot, click)
        }

        if (session.type == SessionType.SHARED) {
            if (slot == SHARED_MANAGE_ENTRY_SLOT) {
                if (session.owner != player.uniqueId) {
                    player.sendMessage(TextUtils.parse(PlayerInvSettings.sharedManageNoPermission))
                } else {
                    openSharedManage(player, session)
                }
                return true
            }
            val top = session.sharedUnlockedSlots.coerceIn(0, inventory.size)
            if (slot >= top) {
                if (click == ClickType.LEFT) {
                    val unlocked = tryUnlockSharedSlot(player, session, slot)
                    if (unlocked == UnlockResult.NO_MONEY) {
                        player.sendMessage(
                            TextUtils.parse(PlayerInvSettings.sharedUnlockNeedMoneyMessage
                                .resolvePlaceholders("{cost}" to "%.2f".format(PlayerInvSettings.sharedUnlockCost)))
                        )
                    }
                }
                return true
            }
        }
        return false
    }

    fun handleClose(player: Player, inventory: Inventory) {
        if (!PlayerInvSettings.enabled) {
            return
        }
        val key = System.identityHashCode(inventory)
        val session = openSessions.remove(key) ?: return
        if (session.viewer != player.uniqueId) {
            return
        }
        if (session.type == SessionType.SHARED_MANAGE) {
            return
        }

        val saveContents = inventory.contents.mapIndexed { index, item ->
            if (session.type == SessionType.SHARED && index >= session.sharedUnlockedSlots) null
            else if (isVirtualSharedItem(item)) null
            else item?.clone()
        }
        submit(async = true) {
            val success = when (session.type) {
                SessionType.PERSONAL -> savePersonal(session.owner, inventory.size, saveContents)
                SessionType.SHARED -> {
                    val sharedId = session.sharedId
                    sharedId != null && saveShared(sharedId, inventory.size, session.sharedUnlockedSlots, saveContents)
                }
                SessionType.SHARED_MANAGE -> true
            }
            if (!success) {
                player.submitOnRegion {
                    player.sendMessage(TextUtils.parse(PlayerInvSettings.saveFailedMessage))
                }
            }
        }
    }

    fun isPlayerInvInventory(player: Player, inventory: Inventory): Boolean {
        val session = openSessions[System.identityHashCode(inventory)] ?: return false
        return session.viewer == player.uniqueId
    }

    private fun openSession(
        viewer: Player,
        title: String,
        size: Int,
        items: Array<ItemStack?>,
        type: SessionType,
        owner: UUID,
        sharedId: UUID?,
        sharedName: String?,
        canSort: Boolean,
        sharedUnlockedSlots: Int
    ): Boolean {
        val inventory = Bukkit.createInventory(null, size, TextUtils.parse(title))
        inventory.contents = resizeNullableTo(size, items.toList())
        val session = Session(
            viewer = viewer.uniqueId,
            owner = owner,
            type = type,
            inventory = inventory,
            sharedId = sharedId,
            sharedName = sharedName,
            canSort = canSort,
            sharedUnlockedSlots = sharedUnlockedSlots.coerceIn(0, size)
        )

        if (type == SessionType.SHARED) {
            applySharedLockOverlay(session)
        }

        openSessions[System.identityHashCode(inventory)] = session
        viewer.openInventory(inventory)
        return true
    }

    private fun handleManageClick(player: Player, session: Session, slot: Int, click: ClickType): Boolean {
        val action = getManageAction(session.inventory.getItem(slot)) ?: return true
        when (action) {
            "members" -> {
                val sharedName = session.sharedName ?: return true
                submit(async = true) {
                    val members = querySharedMembersByName(sharedName)
                    player.submitOnRegion {
                        if (!player.isOnline) {
                            return@submitOnRegion
                        }
                        player.sendMessage(TextUtils.parse(PlayerInvSettings.sharedMembersChatHeader.resolvePlaceholders("{name}" to sharedName)))
                        members.forEach {
                            player.sendMessage(TextUtils.parse(PlayerInvSettings.sharedMembersChatItem.resolvePlaceholders("{player}" to it.playerName)))
                        }
                    }
                }
            }
            "add" -> {
                pendingMemberInputs.set(player.uniqueId, PendingMemberInput(session.sharedName ?: return true, PendingMode.ADD))
                player.closeInventory()
                player.sendMessage(TextUtils.parse(PlayerInvSettings.chatInputAddPrompt))
            }
            "remove" -> {
                pendingMemberInputs.set(player.uniqueId, PendingMemberInput(session.sharedName ?: return true, PendingMode.REMOVE))
                player.closeInventory()
                player.sendMessage(TextUtils.parse(PlayerInvSettings.chatInputRemovePrompt))
            }
            "back" -> {
                val sharedName = session.sharedName ?: return true
                player.closeInventory()
                player.submitOnRegion(delay = 1L) {
                    openSharedAsync(player, sharedName) { result ->
                        when (result) {
                            SharedOpenResult.OK -> Unit
                            SharedOpenResult.NOT_FOUND -> player.sendMessage(
                                TextUtils.parse(PlayerInvSettings.sharedNotFoundMessage
                                    .resolvePlaceholders("{name}" to sharedName))
                            )

                            SharedOpenResult.NO_ACCESS -> player.sendMessage(
                                TextUtils.parse(PlayerInvSettings.sharedNoAccessMessage
                                    .resolvePlaceholders("{name}" to sharedName))
                            )

                            SharedOpenResult.FAILED -> player.sendMessage(TextUtils.parse("&c打开共享仓库失败"))
                        }
                    }
                }
            }
            "toggle_visibility" -> {
                val sharedName = session.sharedName ?: return true
                val owner = session.owner
                val sharedId = session.sharedId
                player.closeInventory()
                submit(async = true) {
                    val result = toggleSharedVisibility(player, sharedName)
                    player.submitOnRegion {
                        if (!player.isOnline) return@submitOnRegion
                        when (result) {
                            is SharedSetVisibilityResult.OK -> {
                                val msg = if (result.isPublic) PlayerInvSettings.sharedSetPublicMessage
                                          else PlayerInvSettings.sharedSetPrivateMessage
                                player.sendMessage(TextUtils.parse(msg.resolvePlaceholders("{name}" to sharedName)))
                                player.submitOnRegion(delay = 1L) {
                                    openSharedManage(player, owner, sharedId, sharedName)
                                }
                            }

                            SharedSetVisibilityResult.NO_ACCESS ->
                                player.sendMessage(TextUtils.parse(PlayerInvSettings.sharedManageNoPermission))

                            else -> player.sendMessage(TextUtils.parse("&c操作失败"))
                        }
                    }
                }
            }
        }
        return true
    }

    private fun openSharedManage(player: Player, sourceSession: Session) {
        openSharedManage(player, sourceSession.owner, sourceSession.sharedId, sourceSession.sharedName ?: return)
    }

    private fun openSharedManage(player: Player, owner: UUID, sharedId: UUID?, sharedName: String) {
        if (owner != player.uniqueId && !player.hasPermission("phcore.admin")) {
            player.sendMessage(TextUtils.parse(PlayerInvSettings.sharedManageNoPermission))
            return
        }
        submit(async = true) {
            // 单次连接同时获取 isPublic 与成员列表，避免多次 DB 往返
            val (isPublic, members) = runCatching {
                withConnection { connection ->
                    val shared = findSharedByName(connection, sharedName)
                        ?: return@withConnection false to emptyList<SharedMemberInfo>()
                    val memberList = if (!shared.isPublic) querySharedMembers(connection, shared.id) else emptyList()
                    shared.isPublic to memberList
                }
            }.getOrDefault(false to emptyList())

            player.submitOnRegion {
                if (!player.isOnline) return@submitOnRegion
                val inventory = Bukkit.createInventory(null, 27, TextUtils.parse(PlayerInvSettings.sharedManagerTitle.resolvePlaceholders("{name}" to sharedName)))

                // slot 10: 成员按钮，公开时显示公开提示，私有时显示成员预览
                inventory.setItem(MANAGE_SLOT_MEMBERS, if (isPublic) {
                    buildManageItem(
                        PlayerInvSettings.sharedManagerMembersItem,
                        PlayerInvSettings.sharedMembersPublicName,
                        PlayerInvSettings.sharedMembersPublicLore,
                        null
                    )
                } else {
                    buildManageItem(
                        PlayerInvSettings.sharedManagerMembersItem,
                        PlayerInvSettings.sharedMembersName,
                        buildMemberPreviewLore(members),
                        "members"
                    )
                })

                // slot 12/14: 添加/移除成员按钮 仅私有时显示
                if (!isPublic) {
                    inventory.setItem(MANAGE_SLOT_ADD, buildManageItem(
                        PlayerInvSettings.sharedManagerAddItem,
                        PlayerInvSettings.sharedAddName,
                        PlayerInvSettings.sharedAddLore,
                        "add"
                    ))
                    inventory.setItem(MANAGE_SLOT_REMOVE, buildManageItem(
                        PlayerInvSettings.sharedManagerRemoveItem,
                        PlayerInvSettings.sharedRemoveName,
                        PlayerInvSettings.sharedRemoveLore,
                        "remove"
                    ))
                }

                // slot 13: 切换可见性按钮（始终显示）
                inventory.setItem(MANAGE_SLOT_TOGGLE_VISIBILITY, buildManageItem(
                    PlayerInvSettings.sharedManagerToggleVisibilityItem,
                    if (isPublic) PlayerInvSettings.sharedToggleToPrivateName else PlayerInvSettings.sharedToggleToPublicName,
                    if (isPublic) PlayerInvSettings.sharedToggleToPrivateLore else PlayerInvSettings.sharedToggleToPublicLore,
                    "toggle_visibility"
                ))

                // slot 16: 返回按钮（不变）
                inventory.setItem(MANAGE_SLOT_BACK, buildManageItem(
                    PlayerInvSettings.sharedManagerBackItem,
                    PlayerInvSettings.sharedBackName,
                    PlayerInvSettings.sharedBackLore,
                    "back"
                ))

                val manageSession = Session(
                    viewer = player.uniqueId,
                    owner = owner,
                    type = SessionType.SHARED_MANAGE,
                    inventory = inventory,
                    sharedId = sharedId,
                    sharedName = sharedName,
                    canSort = false,
                    sharedUnlockedSlots = 0
                )

                openSessions[System.identityHashCode(inventory)] = manageSession
                player.openInventory(inventory)
            }
        }
    }

    private fun buildMemberPreviewLore(members: List<SharedMemberInfo>): List<String> {
        val preview = members.take(PlayerInvSettings.memberPreviewLimit).joinToString("&7, ") { "&f${it.playerName}" }
        return PlayerInvSettings.sharedMembersLore.map { line ->
            line.resolvePlaceholders("{members}" to if (preview.isBlank()) "&7(无成" else preview)
        }
    }

    private fun buildManageItem(materialSpec: String, name: String, lore: List<String>, action: String?): ItemStack {
        val item = if (ItemUtils.isHeadSpec(materialSpec)) {
            ItemUtils.resolveHead(materialSpec)
        } else {
            null
        } ?: ItemStack(ItemUtils.matchMaterial(materialSpec, Material.STONE) ?: Material.STONE)
        TextBridge.setDisplayName(item, TextUtils.parseItem(name))
        @Suppress("UNCHECKED_CAST")
        TextBridge.setLore(item, TextUtils.parseItemLore(lore) as List<net.kyori.adventure.text.Component>)
        // action null 时不写入 PDC，点击时 getManageAction 返回 null，handleManageClick 直接 return true（无操作）
        if (action != null) {
            val meta = item.itemMeta ?: return item
            meta.persistentDataContainer.set(manageActionKey, PersistentDataType.STRING, action)
            item.itemMeta = meta
        }
        return item
    }

    private fun getManageAction(item: ItemStack?): String? {
        val meta = item?.itemMeta ?: return null
        return meta.persistentDataContainer.get(manageActionKey, PersistentDataType.STRING)
    }

    private fun tryUnlockSharedSlot(player: Player, session: Session, clickedSlot: Int): UnlockResult {
        if (session.type != SessionType.SHARED) return UnlockResult.NOT_SHARED
        if (session.sharedUnlockedSlots < 0) return UnlockResult.PENDING // 上一次解锁仍在处理中
        val hasManageEntry = session.owner == session.viewer && session.inventory.size > SHARED_MANAGE_ENTRY_SLOT
        val effectiveNext = if (hasManageEntry && session.sharedUnlockedSlots == SHARED_MANAGE_ENTRY_SLOT) {
            SHARED_MANAGE_ENTRY_SLOT + 1
        } else {
            session.sharedUnlockedSlots
        }
        if (clickedSlot != effectiveNext) return UnlockResult.NOT_NEXT
        val sharedId = session.sharedId ?: return UnlockResult.NOT_SHARED
        if (!EconomyUtils.has(player, PlayerInvSettings.sharedUnlockCost.toBigDecimal())) return UnlockResult.NO_MONEY

        // 标记内存防止连点
        session.sharedUnlockedSlots = -1

        submit(async = true) {
            val newUnlockedFromDb: Int = runCatching {
                withConnection { connection ->
                    val row = connection.prepareStatement(
                        "SELECT unlocked_slots, size FROM ${PlayerInvSettings.sharedTable} WHERE shared_id = ? LIMIT 1"
                    ).use { statement ->
                        statement.setString(1, sharedId.toString())
                        statement.executeQuery().use { result ->
                            if (!result.next()) return@withConnection -1
                            result.getInt("unlocked_slots") to result.getInt("size")
                        }
                    }
                    val dbUnlocked = row.first
                    val size = row.second
                    val effectiveUnlocked = if (hasManageEntry && dbUnlocked == SHARED_MANAGE_ENTRY_SLOT) {
                        SHARED_MANAGE_ENTRY_SLOT + 1
                    } else {
                        dbUnlocked
                    }
                    if (clickedSlot != effectiveUnlocked || effectiveUnlocked >= size) {
                        return@withConnection -1
                    }
                    val newUnlocked = if (hasManageEntry && effectiveUnlocked + 1 == SHARED_MANAGE_ENTRY_SLOT) {
                        SHARED_MANAGE_ENTRY_SLOT + 1
                    } else {
                        effectiveUnlocked + 1
                    }
                    connection.prepareStatement(
                        "UPDATE ${PlayerInvSettings.sharedTable} SET unlocked_slots = ?, updated_at = ? WHERE shared_id = ?"
                    ).use { statement ->
                        statement.setInt(1, newUnlocked)
                        statement.setTimestamp(2, Timestamp(System.currentTimeMillis()))
                        statement.setString(3, sharedId.toString())
                        if (statement.executeUpdate() > 0) newUnlocked else -1
                    }
                }
            }.getOrDefault(-1)

            player.submitOnRegion {
                if (!player.isOnline || newUnlockedFromDb < 0) {
                    session.sharedUnlockedSlots = effectiveNext // 恢复
                    applySharedLockOverlay(session)
                    return@submitOnRegion
                }
                
                if (!EconomyUtils.withdraw(player, PlayerInvSettings.sharedUnlockCost.toBigDecimal())) {
                    session.sharedUnlockedSlots = effectiveNext // 恢复
                    applySharedLockOverlay(session)
                    return@submitOnRegion
                }

                session.sharedUnlockedSlots = newUnlockedFromDb
                session.inventory.setItem(clickedSlot, null)
                applySharedLockOverlay(session)

                player.sendMessage(
                    TextUtils.parse(PlayerInvSettings.sharedUnlockSuccessMessage
                        .resolvePlaceholders("{cost}" to "%.2f".format(PlayerInvSettings.sharedUnlockCost)))
                )
            }
        }
        return UnlockResult.PENDING
    }

    private fun applySharedLockOverlay(session: Session) {
        if (session.type != SessionType.SHARED) return
        // owner 视图下，slot 49 固定为管理入口按钮，不参与锁定覆盖逻辑
        val hasManageEntry = session.owner == session.viewer && session.inventory.size > SHARED_MANAGE_ENTRY_SLOT
        val top = session.sharedUnlockedSlots.coerceIn(0, session.inventory.size)
        for (slot in 0 until top) {
            if (isLockedSlotItem(session.inventory.getItem(slot))) {
                session.inventory.setItem(slot, null)
            }
        }
        for (slot in top until session.inventory.size) {
            // 管理入口按钮所在槽位由后续逻辑单独处理，此处跳过以免覆盖为锁定图标
            if (hasManageEntry && slot == SHARED_MANAGE_ENTRY_SLOT) continue
            // top 恰好是管理入口槽位，高亮应落在下一个可解锁槽位
            val highlight = if (hasManageEntry && top == SHARED_MANAGE_ENTRY_SLOT) {
                slot == SHARED_MANAGE_ENTRY_SLOT + 1
            } else {
                slot == top
            }
            session.inventory.setItem(slot, buildLockedSlotItem(highlight))
        }

        if (hasManageEntry) {
            session.inventory.setItem(
                SHARED_MANAGE_ENTRY_SLOT,
                buildManageItem(
                    PlayerInvSettings.sharedManagerEntryItem,
                    PlayerInvSettings.sharedEntryName,
                    PlayerInvSettings.sharedEntryLore + PlayerInvSettings.sharedManageHintLore,
                    "entry"
                )
            )
        }
    }

    private fun buildLockedSlotItem(highlight: Boolean): ItemStack {
        val item = ItemStack(PlayerInvSettings.sharedLockedMaterial)
        TextBridge.setDisplayName(item, TextUtils.parseItem(PlayerInvSettings.sharedLockedName))
        @Suppress("UNCHECKED_CAST")
        TextBridge.setLore(item, TextUtils.parseItemLore(PlayerInvSettings.sharedLockedLore.map {
            it.resolvePlaceholders("{cost}" to "%.2f".format(PlayerInvSettings.sharedUnlockCost))
        }) as List<net.kyori.adventure.text.Component>)
        val meta = item.itemMeta ?: return item
        meta.persistentDataContainer.set(lockedSlotKey, PersistentDataType.BYTE, 1)
        if (highlight) {
            meta.addEnchant(Enchantment.LURE, 1, true)
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        }
        item.itemMeta = meta
        return item
    }

    private fun isLockedSlotItem(item: ItemStack?): Boolean {
        val meta = item?.itemMeta ?: return false
        return meta.persistentDataContainer.has(lockedSlotKey, PersistentDataType.BYTE)
    }

    private fun isManageEntryItem(item: ItemStack?): Boolean {
        return getManageAction(item) == "entry"
    }

    private fun isVirtualSharedItem(item: ItemStack?): Boolean {
        return isLockedSlotItem(item) || isManageEntryItem(item)
    }

    private fun sanitizeSharedInventory(items: Array<ItemStack?>): Array<ItemStack?> {
        return items.map { item ->
            if (isVirtualSharedItem(item)) null else item?.clone()
        }.toTypedArray()
    }

    private fun reopenManageAfterChat(player: Player, sharedName: String) {
        player.submitOnRegion(delay = 1L) {
            submit(async = true) {
                val shared = querySharedByName(sharedName)
                if (shared != null && shared.owner == player.uniqueId) {
                    player.submitOnRegion {
                        if (!player.isOnline) {
                            return@submitOnRegion
                        }
                        openSharedManage(player, shared.owner, shared.id, shared.name)
                    }
                }
            }
        }
    }

    private fun querySharedByName(name: String): SharedRecord? {
        if (!isReady()) return null
        return runCatching {
            withConnection { connection -> findSharedByName(connection, name) }
        }.getOrNull()
    }

    private fun querySharedMembersByName(name: String): List<SharedMemberInfo> {
        val shared = querySharedByName(name) ?: return emptyList()
        return runCatching {
            withConnection { connection -> querySharedMembers(connection, shared.id) }
        }.getOrDefault(emptyList())
    }

    private fun querySharedMembers(connection: Connection, sharedId: UUID): List<SharedMemberInfo> {
        return connection.prepareStatement(
            "SELECT player_uuid, role FROM ${PlayerInvSettings.sharedMemberTable} WHERE shared_id = ?"
        ).use { statement ->
            statement.setString(1, sharedId.toString())
            statement.executeQuery().use { result ->
                val list = mutableListOf<SharedMemberInfo>()
                while (result.next()) {
                    val id = UUID.fromString(result.getString("player_uuid"))
                    val role = runCatching { SharedRole.valueOf(result.getString("role").uppercase(Locale.ROOT)) }.getOrDefault(SharedRole.MEMBER)
                    list += SharedMemberInfo(Bukkit.getOfflinePlayer(id).name ?: id.toString(), role == SharedRole.OWNER)
                }
                list.sortedWith(compareByDescending<SharedMemberInfo> { it.owner }.thenBy { it.playerName.lowercase(Locale.ROOT) })
            }
        }
    }

    private fun resizeNullableTo(size: Int, list: List<ItemStack?>): Array<ItemStack?> {
        return InventoryUtils.resizeNullable(size, list)
    }

    private fun loadPersonal(owner: UUID): PersonalRecord {
        val defaultSize = PlayerInvSettings.defaultRows * 9
        val default = PersonalRecord(size = defaultSize, items = arrayOfNulls(defaultSize))
        val handler = personalDataHandler ?: return default
        val user = owner.toString()
        return runCatching {
            val container = handler.setupDataContainer(user)
            val size = normalizeInventorySize(container[PERSONAL_KEY_SIZE]?.toIntOrNull() ?: defaultSize, defaultSize)
            val chunkCount = (container[PERSONAL_KEY_CHUNK_COUNT]?.toIntOrNull() ?: 0).coerceAtLeast(0)
            val json = if (chunkCount <= 0) {
                "[]"
            } else {
                buildString {
                    for (index in 0 until chunkCount) {
                        append(container[PERSONAL_KEY_CHUNK_PREFIX + index].orEmpty())
                    }
                }.ifBlank { "[]" }
            }
            PersonalRecord(size = size, items = deserializeInventory(json, size))
        }.getOrElse { ex ->
            warning("[Warehouse] 读取个人仓库失败(${owner}): ${ex.message}")
            default
        }
    }

    private fun savePersonal(owner: UUID, size: Int, items: List<ItemStack?>): Boolean {
        val handler = personalDataHandler ?: return false
        val finalSize = normalizeInventorySize(size, PlayerInvSettings.defaultRows * 9)
        val json = serializeInventory(resizeNullableTo(finalSize, items).toList())
        return runCatching {
            val user = owner.toString()
            val container = handler.setupDataContainer(user)
            container[PERSONAL_KEY_SIZE] = finalSize

            val chunks = json.chunked(PERSONAL_CHUNK_SIZE)
            val newCount = chunks.size
            val oldCount = container[PERSONAL_KEY_CHUNK_COUNT]?.toIntOrNull()?.coerceAtLeast(0) ?: 0

            container[PERSONAL_KEY_CHUNK_COUNT] = newCount
            chunks.forEachIndexed { index, chunk ->
                container[PERSONAL_KEY_CHUNK_PREFIX + index] = chunk
            }
            if (oldCount > newCount) {
                for (index in newCount until oldCount) {
                    container[PERSONAL_KEY_CHUNK_PREFIX + index] = ""
                }
            }
            true
        }.onFailure { ex ->
            warning("[Warehouse] 保存个人仓库失败(${owner}): ${ex.message}")
        }.getOrDefault(false)
    }

    private fun saveShared(sharedId: UUID, size: Int, unlockedSlots: Int, items: List<ItemStack?>): Boolean {
        val ds = dataSource ?: return false
        val finalSize = normalizeInventorySize(size, PlayerInvSettings.sharedMaxRows * 9)
        val finalUnlocked = unlockedSlots.coerceIn(0, finalSize)
        val now = Timestamp(System.currentTimeMillis())
        val json = serializeInventory(resizeNullableTo(finalSize, items).toList())
        return runCatching {
            ds.connection.use { connection ->
                connection.prepareStatement(
                    "UPDATE ${PlayerInvSettings.sharedTable} SET size = ?, unlocked_slots = ?, inventory_json = ?, updated_at = ? WHERE shared_id = ?"
                ).use { statement ->
                    statement.setInt(1, finalSize)
                    statement.setInt(2, finalUnlocked)
                    statement.setString(3, json)
                    statement.setTimestamp(4, now)
                    statement.setString(5, sharedId.toString())
                    statement.executeUpdate() > 0
                }
            }
        }.onFailure { ex ->
            warning("[Warehouse] 保存共享仓库失败(${sharedId}): ${ex.message}")
        }.getOrDefault(false)
    }

    private fun serializeInventory(items: List<ItemStack?>): String {
        val payload = items.map { item ->
            if (item == null || item.type.isAir || isVirtualSharedItem(item)) null
            else mapOf("b64" to Base64.getEncoder().encodeToString(item.serializeAsBytes()))
        }
        return ArimJsonUtils.toJson(payload)
    }

    private fun deserializeInventory(json: String, size: Int): Array<ItemStack?> {
        val result = arrayOfNulls<ItemStack>(size)
        val rawList = runCatching {
            @Suppress("UNCHECKED_CAST")
            ArimJsonUtils.gson().fromJson(json, itemStackMapListType) as? List<Map<String, String>?>
        }.getOrElse { ex ->
            warning("[Warehouse] 反序列化仓库 JSON 失败: ${ex.message}")
            emptyList()
        } ?: emptyList()

        for (index in 0 until minOf(rawList.size, result.size)) {
            val map = rawList[index] ?: continue
            val base64 = map["b64"]?.trim().orEmpty()
            if (base64.isBlank()) {
                result[index] = null
                continue
            }
            result[index] = runCatching {
                ItemStack.deserializeBytes(Base64.getDecoder().decode(base64))
            }.getOrElse { ex ->
                warning("[Warehouse] 反序列化物品失败(slot=$index): ${ex.message}")
                null
            }
        }
        return result
    }

    private fun changeSharedMember(operator: Player, rawName: String, target: OfflinePlayer, add: Boolean): SharedMemberResult {
        if (!isReady()) return SharedMemberResult.FAILED
        val name = normalizeSharedName(rawName) ?: return SharedMemberResult.NOT_FOUND
        return runCatching {
            withConnection { connection ->
                val shared = findSharedByName(connection, name) ?: return@withConnection SharedMemberResult.NOT_FOUND
                if (!canManageShared(connection, operator, shared)) return@withConnection SharedMemberResult.NO_ACCESS
                if (target.uniqueId == shared.owner && !add) return@withConnection SharedMemberResult.CANNOT_REMOVE_OWNER

                if (add) upsertSharedMember(connection, shared.id, target.uniqueId, SharedRole.MEMBER)
                else connection.prepareStatement(
                    "DELETE FROM ${PlayerInvSettings.sharedMemberTable} WHERE shared_id = ? AND player_uuid = ? AND role <> ?"
                ).use { statement ->
                    statement.setString(1, shared.id.toString())
                    statement.setString(2, target.uniqueId.toString())
                    statement.setString(3, SharedRole.OWNER.name)
                    statement.executeUpdate()
                }
                SharedMemberResult.OK
            }
        }.onFailure { ex ->
            warning("[Warehouse] 变更共享成员失败(${operator.name}, $name): ${ex.message}")
        }.getOrDefault(SharedMemberResult.FAILED)
    }

    private fun resolveOfflinePlayer(name: String): OfflinePlayer? =
        com.pixlehavencore.util.resolveOfflinePlayer(name)

    private fun ensureOwnerRecord(connection: Connection, owner: UUID): OwnerRecord {
        queryOwnerRecord(connection, owner)?.let { return it }
        val now = Timestamp(System.currentTimeMillis())
        val ownerTable = "${PlayerInvSettings.databaseTable}_owner"
        if (DatabaseUtils.isMySql) {
            connection.prepareStatement(
                "INSERT INTO $ownerTable (owner_uuid, shared_quota, updated_at) VALUES (?, 0, ?) ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at)"
            ).use { statement ->
                statement.setString(1, owner.toString())
                statement.setTimestamp(2, now)
                statement.executeUpdate()
            }
        } else {
            connection.prepareStatement(
                "INSERT INTO $ownerTable (owner_uuid, shared_quota, updated_at) VALUES (?, 0, ?) ON CONFLICT(owner_uuid) DO NOTHING"
            ).use { statement ->
                statement.setString(1, owner.toString())
                statement.setTimestamp(2, now)
                statement.executeUpdate()
            }
        }
        return queryOwnerRecord(connection, owner) ?: OwnerRecord(owner, 0)
    }

    private fun queryOwnerRecord(connection: Connection, owner: UUID): OwnerRecord? {
        val ownerTable = "${PlayerInvSettings.databaseTable}_owner"
        return connection.prepareStatement("SELECT owner_uuid, shared_quota FROM $ownerTable WHERE owner_uuid = ? LIMIT 1").use { statement ->
            statement.setString(1, owner.toString())
            statement.executeQuery().use { result ->
                if (!result.next()) null else OwnerRecord(UUID.fromString(result.getString("owner_uuid")), result.getInt("shared_quota"))
            }
        }
    }

    private fun updateSharedQuota(connection: Connection, owner: UUID, quota: Int) {
        val ownerTable = "${PlayerInvSettings.databaseTable}_owner"
        val now = Timestamp(System.currentTimeMillis())
        connection.prepareStatement("UPDATE $ownerTable SET shared_quota = ?, updated_at = ? WHERE owner_uuid = ?").use { statement ->
            statement.setInt(1, quota.coerceAtLeast(0))
            statement.setTimestamp(2, now)
            statement.setString(3, owner.toString())
            statement.executeUpdate()
        }
    }

    private fun findSharedByName(connection: Connection, name: String): SharedRecord? {
        val quotedName = DatabaseUtils.quoted("name")
        return connection.prepareStatement(
            "SELECT shared_id, owner_uuid, $quotedName, size, unlocked_slots, is_public, inventory_json FROM ${PlayerInvSettings.sharedTable} WHERE $quotedName = ? LIMIT 1"
        ).use { statement ->
            statement.setString(1, name)
            statement.executeQuery().use { result -> result.toSharedRecord() }
        }
    }

    private fun ResultSet.toSharedRecord(): SharedRecord? {
        if (!next()) return null
        return SharedRecord(
            id = UUID.fromString(getString("shared_id")),
            owner = UUID.fromString(getString("owner_uuid")),
            name = getString("name"),
            size = normalizeInventorySize(getInt("size"), PlayerInvSettings.sharedMaxRows * 9),
            unlockedSlots = getInt("unlocked_slots").coerceAtLeast(0),
            inventoryJson = getString("inventory_json") ?: "[]",
            isPublic = runCatching { getInt("is_public") != 0 }.getOrDefault(false)
        )
    }

    private fun getSharedRole(connection: Connection, sharedId: UUID, player: UUID): SharedRole? {
        return connection.prepareStatement(
            "SELECT role FROM ${PlayerInvSettings.sharedMemberTable} WHERE shared_id = ? AND player_uuid = ? LIMIT 1"
        ).use { statement ->
            statement.setString(1, sharedId.toString())
            statement.setString(2, player.toString())
            statement.executeQuery().use { result ->
                if (!result.next()) null
                else runCatching { SharedRole.valueOf(result.getString("role").uppercase(Locale.ROOT)) }.getOrNull()
            }
        }
    }

    private fun canManageShared(connection: Connection, operator: Player, shared: SharedRecord): Boolean {
        if (operator.hasPermission("phcore.admin")) return true
        return getSharedRole(connection, shared.id, operator.uniqueId) == SharedRole.OWNER
    }

    private fun upsertSharedMember(connection: Connection, sharedId: UUID, player: UUID, role: SharedRole) {
        val now = Timestamp(System.currentTimeMillis())
        if (DatabaseUtils.isMySql) {
            connection.prepareStatement(
                "INSERT INTO ${PlayerInvSettings.sharedMemberTable} (shared_id, player_uuid, role, updated_at) VALUES (?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE role = VALUES(role), updated_at = VALUES(updated_at)"
            ).use { statement ->
                statement.setString(1, sharedId.toString())
                statement.setString(2, player.toString())
                statement.setString(3, role.name)
                statement.setTimestamp(4, now)
                statement.executeUpdate()
            }
        } else {
            connection.prepareStatement(
                "INSERT INTO ${PlayerInvSettings.sharedMemberTable} (shared_id, player_uuid, role, updated_at) VALUES (?, ?, ?, ?) " +
                    "ON CONFLICT(shared_id, player_uuid) DO UPDATE SET role = excluded.role, updated_at = excluded.updated_at"
            ).use { statement ->
                statement.setString(1, sharedId.toString())
                statement.setString(2, player.toString())
                statement.setString(3, role.name)
                statement.setTimestamp(4, now)
                statement.executeUpdate()
            }
        }
    }

    private fun ensureTables() {
        withConnection { connection ->
            val updatedAtColumn = if (DatabaseUtils.isMySql) {
                "updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
            } else {
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
            }

            val ownerTable = "${PlayerInvSettings.databaseTable}_owner"
            connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS $ownerTable (" +
                    "owner_uuid VARCHAR(36) PRIMARY KEY," +
                    "shared_quota INT NOT NULL DEFAULT 0," +
                    updatedAtColumn +
                    ")"
            ).use(PreparedStatement::execute)

            connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS ${PlayerInvSettings.sharedTable} (" +
                    "shared_id VARCHAR(36) PRIMARY KEY," +
                    "owner_uuid VARCHAR(36) NOT NULL," +
                    (if (DatabaseUtils.isMySql) "`name` VARCHAR(32) NOT NULL UNIQUE," else "\"name\" VARCHAR(32) NOT NULL UNIQUE,") +
                    "size INT NOT NULL DEFAULT ${PlayerInvSettings.sharedMaxRows * 9}," +
                    "unlocked_slots INT NOT NULL DEFAULT ${PlayerInvSettings.sharedInitialRows * 9}," +
                    "is_public ${if (DatabaseUtils.isMySql) "TINYINT(1)" else "INTEGER"} NOT NULL DEFAULT 0," +
                    "inventory_json TEXT NOT NULL," +
                    "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    updatedAtColumn +
                    ")"
            ).use(PreparedStatement::execute)

            // 旧表迁移：为已存在的 shared_inv 表补 is_public 列（静默忽略“列已存在”错误）
            runCatching {
                connection.prepareStatement(
                    if (DatabaseUtils.isMySql)
                        "ALTER TABLE ${PlayerInvSettings.sharedTable} ADD COLUMN is_public TINYINT(1) NOT NULL DEFAULT 0"
                    else
                        "ALTER TABLE ${PlayerInvSettings.sharedTable} ADD COLUMN is_public INTEGER NOT NULL DEFAULT 0"
                ).use(PreparedStatement::execute)
            } // 列已存在时数据库会报错，catch 后静默忽略即
            connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS ${PlayerInvSettings.sharedMemberTable} (" +
                    "shared_id VARCHAR(36) NOT NULL," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "role VARCHAR(16) NOT NULL DEFAULT 'MEMBER'," +
                    updatedAtColumn +
                    ", PRIMARY KEY (shared_id, player_uuid)" +
                    ")"
            ).use(PreparedStatement::execute)
        }
    }

    private fun <T> withConnection(block: (Connection) -> T): T {
        val ds = dataSource ?: error("Warehouse datasource unavailable")
        ds.connection.use { connection ->
            return block(connection)
        }
    }

    private fun Player.submitOnRegion(delay: Long = 0L, action: Player.() -> Unit) {
        if (delay <= 0L) {
            submitOnEntity { action() }
        } else {
            submitOnEntity(delay = delay) { action() }
        }
    }

    private fun toggleSharedVisibility(operator: Player, rawName: String): SharedSetVisibilityResult {
        if (!isReady()) return SharedSetVisibilityResult.FAILED
        val name = normalizeSharedName(rawName) ?: return SharedSetVisibilityResult.NOT_FOUND
        return runCatching {
            withConnection { connection ->
                val shared = findSharedByName(connection, name)
                    ?: return@withConnection SharedSetVisibilityResult.NOT_FOUND
                if (!canManageShared(connection, operator, shared))
                    return@withConnection SharedSetVisibilityResult.NO_ACCESS
                val newPublic = !shared.isPublic
                connection.prepareStatement(
                    "UPDATE ${PlayerInvSettings.sharedTable} SET is_public = ?, updated_at = ? WHERE shared_id = ?"
                ).use { stmt ->
                    stmt.setInt(1, if (newPublic) 1 else 0)
                    stmt.setTimestamp(2, Timestamp(System.currentTimeMillis()))
                    stmt.setString(3, shared.id.toString())
                    stmt.executeUpdate()
                }
                SharedSetVisibilityResult.OK(newPublic)
            }
        }.onFailure { ex ->
            warning("[Warehouse] 切换共享仓库可见性失${operator.name}, $name): ${ex.message}")
        }.getOrDefault(SharedSetVisibilityResult.FAILED)
    }

    private fun closeStorage() {
        DatabaseUtils.closeMultipleHandler(personalDataHandler)
        personalDataHandler = null
        dataSource?.close()
        dataSource = null
    }

    private fun personalDataTableName(): String {
        return "${PlayerInvSettings.databaseTable}_personal"
    }

    private fun normalizeInventorySize(raw: Int, fallback: Int): Int {
        val source = if (raw <= 0) fallback else raw
        val max = PlayerInvSettings.sharedMaxRows * 9
        val clamped = source.coerceIn(9, max)
        return if (clamped % 9 == 0) clamped else clamped - (clamped % 9)
    }

    private fun normalizeSharedName(raw: String): String? {
        val text = raw.trim()
        if (text.isBlank()) return null
        return if (text.length > 32) text.substring(0, 32) else text
    }

    private data class PersonalRecord(
        val size: Int,
        val items: Array<ItemStack?>
    )

    private data class PersonalAdjustResult(
        val record: PersonalRecord,
        val overflow: List<ItemStack>
    )

    private data class SharedOpenPayload(
        val owner: UUID,
        val sharedId: UUID,
        val sharedName: String,
        val size: Int,
        val sharedUnlockedSlots: Int,
        val items: Array<ItemStack?>
    )

    private data class SharedOpenAsyncResult(
        val result: SharedOpenResult,
        val payload: SharedOpenPayload?
    )

    private data class OwnerRecord(
        val owner: UUID,
        val sharedQuota: Int
    )

    private data class SharedRecord(
        val id: UUID,
        val owner: UUID,
        val name: String,
        val size: Int,
        val unlockedSlots: Int,
        val inventoryJson: String,
        val isPublic: Boolean = false
    )

    private data class Session(
        val viewer: UUID,
        val owner: UUID,
        val type: SessionType,
        val inventory: Inventory,
        val sharedId: UUID?,
        val sharedName: String?,
        val canSort: Boolean,
        var sharedUnlockedSlots: Int
    )

    private data class PendingMemberInput(
        val sharedName: String,
        val mode: PendingMode
    )

    private enum class PendingMode {
        ADD,
        REMOVE
    }

    private enum class SharedRole {
        OWNER,
        MEMBER
    }

    private enum class SessionType {
        PERSONAL,
        SHARED,
        SHARED_MANAGE
    }

    private enum class UnlockResult {
        OK,
        PENDING,
        NO_MONEY,
        FAILED,
        NOT_SHARED,
        NOT_NEXT
    }

    enum class SharedCreateResult {
        OK,
        EXISTS,
        NO_QUOTA,
        INVALID_NAME,
        FAILED
    }

    enum class SharedOpenResult {
        OK,
        NOT_FOUND,
        NO_ACCESS,
        FAILED
    }

    enum class SharedMemberResult {
        OK,
        NOT_FOUND,
        NO_ACCESS,
        CANNOT_REMOVE_OWNER,
        PLAYER_NOT_FOUND,
        FAILED
    }

    sealed class SharedUpgradeResult {
        data object NOT_FOUND : SharedUpgradeResult()
        data object NO_ACCESS : SharedUpgradeResult()
        data object REACHED_MAX : SharedUpgradeResult()
        data object FAILED : SharedUpgradeResult()
        data class OK(val size: Int) : SharedUpgradeResult()
    }

    sealed class SharedMemberListResult {
        data object NOT_FOUND : SharedMemberListResult()
        data object NO_ACCESS : SharedMemberListResult()
        data object FAILED : SharedMemberListResult()
        data class OK(val members: List<SharedMemberInfo>) : SharedMemberListResult()
    }

    data class SharedMemberInfo(
        val playerName: String,
        val owner: Boolean
    )

    sealed class SharedSetVisibilityResult {
        data object NOT_FOUND : SharedSetVisibilityResult()
        data object NO_ACCESS : SharedSetVisibilityResult()
        data object FAILED : SharedSetVisibilityResult()
        data class OK(val isPublic: Boolean) : SharedSetVisibilityResult()
    }

}
