package com.pixlehavencore.feature.title

import com.pixlehavencore.util.PlaceholderUtils.resolvePlaceholders
import com.pixlehavencore.util.CraftEngineItemsUtil
import com.pixlehavencore.util.ItemUtils
import com.pixlehavencore.util.TextUtils
import com.pixlehavencore.util.formatDuration
import net.kyori.adventure.text.Component
import com.pixlehavencore.bridge.TextBridge
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.platform.util.PlayerSessionMap
import taboolib.platform.util.submit as submitOnEntity
import java.util.UUID

object TitleMenu {

    private val openViews = PlayerSessionMap<TitleMenuHolder>({ throw IllegalStateException() })

    fun open(player: Player, category: String? = null, page: Int = 0) {
        if (!TitleSettings.enabled) return
        val previews = TitleService.getTitlePreviews(player, category)
        val maxPage = if (previews.isEmpty()) 0 else (previews.size - 1) / TitleSettings.pageSize
        val currentPage = page.coerceIn(0, maxPage)
        val holder = TitleMenuHolder(category = category, page = currentPage)
        val guiTitle = TextBridge.toLegacy(TextBridge.fromMiniMessage(TitleSettings.guiTitle))
        val rows = TitleSettings.guiRows
        player.openMenu<Chest>(guiTitle) {
            val lines = buildList {
                add("#########")
                for (i in 1 until rows - 1) {
                    add("|       |")
                }
                add("#########")
            }
            map(*lines.toTypedArray())
            set('#', ItemStack(TitleSettings.borderAccent))
            set('|', ItemStack(TitleSettings.borderItem))
            onBuild { p, inv ->
                holder.backingInventory = inv
                openViews[p.uniqueId] = holder
                renderInventory(inv, p, holder)
            }
        }
    }

    fun refreshOpenMenus() {
        openViews.entries().toList().forEach { (playerId, holder) ->
            val player = Bukkit.getPlayer(playerId) ?: run {
                openViews.remove(playerId)
                return@forEach
            }
            player.submitOnEntity {
                val topInventory = player.openInventory.topInventory
                if (holder.backingInventory !== topInventory) {
                    openViews.remove(playerId)
                    return@submitOnEntity
                }
                renderInventory(holder.backingInventory, player, holder)
                player.updateInventory()
            }
        }
    }

    fun unregister(playerId: UUID) {
        openViews.remove(playerId)
    }

    fun getOpenHolder(playerId: UUID): TitleMenuHolder? {
        return openViews[playerId]
    }

    fun getOpenHolder(inventory: Inventory): TitleMenuHolder? {
        openViews.entries().forEach { (_, holder) ->
            if (holder.backingInventory === inventory) return holder
        }
        return null
    }

    fun handleClick(player: Player, slot: Int): Boolean {
        val holder = openViews[player.uniqueId] ?: return false

        val categories = TitleService.getCategories()
        val catIndex = TitleSettings.categorySlots.indexOf(slot)
        if (catIndex >= 0) {
            val selectedCat = if (catIndex == 0) null else categories.getOrNull(catIndex - 1)
            open(player, selectedCat, 0)
            return true
        }

        if (slot == TitleSettings.prevPageSlot && holder.page > 0) {
            open(player, holder.category, holder.page - 1)
            return true
        }
        if (slot == TitleSettings.nextPageSlot) {
            open(player, holder.category, holder.page + 1)
            return true
        }

        val titlePreviews = TitleService.getTitlePreviews(player, holder.category)
        val start = holder.page * TitleSettings.pageSize
        val gridIndex = slotToTitleIndex(slot)
        val titleIndex = gridIndex + start
        if (gridIndex >= 0 && titleIndex in titlePreviews.indices) {
            val preview = titlePreviews[titleIndex]
            if (preview.entry == null || preview.isExpired) return true
            if (preview.isActive) {
                TitleService.deactivateTitle(player)
            } else {
                TitleService.activateTitle(player, preview.definition.id)
            }
            open(player, holder.category, holder.page)
            return true
        }

        return true
    }

    private fun titleIndexToSlot(index: Int): Int {
        val baseRow = TitleSettings.titleStartSlot / 9
        val baseCol = TitleSettings.titleStartSlot % 9
        val cols = 9 - baseCol
        val row = baseRow + index / cols
        val col = baseCol + index % cols
        return row * 9 + col
    }

    private fun slotToTitleIndex(slot: Int): Int {
        val baseRow = TitleSettings.titleStartSlot / 9
        val baseCol = TitleSettings.titleStartSlot % 9
        val cols = 9 - baseCol
        val clickRow = slot / 9
        val clickCol = slot % 9
        if (clickRow < baseRow || clickCol < baseCol || clickCol >= baseCol + cols) return -1
        return (clickRow - baseRow) * cols + (clickCol - baseCol)
    }

    private fun resolveRarityColor(rarity: String): String {
        return TitleSettings.rarityColors[rarity.lowercase()] ?: "&7"
    }

    private fun renderCategoryGrid(inventory: Inventory, holder: TitleMenuHolder) {
        val categories = TitleService.getCategories()
        val slots = TitleSettings.categorySlots
        if (slots.isEmpty()) return
        val allSelected = holder.category == null
        inventory.setItem(slots[0], ItemUtils.namedItem(
            if (allSelected) Material.LIME_STAINED_GLASS_PANE else Material.WHITE_STAINED_GLASS_PANE,
            if (allSelected) "&a${TitleSettings.msgGuiCategoryAll}" else "&7${TitleSettings.msgGuiCategoryAll}"
        ))
        categories.forEachIndexed { index, cat ->
            val slotIndex = index + 1
            if (slotIndex < slots.size) {
                val selected = holder.category == cat
                inventory.setItem(slots[slotIndex], ItemUtils.namedItem(
                    if (selected) Material.LIME_STAINED_GLASS_PANE else Material.WHITE_STAINED_GLASS_PANE,
                    if (selected) "&a$cat" else "&7$cat"
                ))
            }
        }
    }

    private fun renderNavigation(inventory: Inventory, player: Player, totalTitles: Int, currentPage: Int, maxPage: Int) {
        val accent = ItemStack(TitleSettings.borderAccent)
        for (col in 0..8) {
            val slot = 45 + col
            if (slot != TitleSettings.prevPageSlot && slot != TitleSettings.infoSlot && slot != TitleSettings.nextPageSlot) {
                inventory.setItem(slot, accent)
            }
        }
        if (currentPage > 0) {
            inventory.setItem(TitleSettings.prevPageSlot, ItemUtils.namedItem(Material.ARROW, TitleSettings.msgGuiPrevPage))
        } else {
            inventory.setItem(TitleSettings.prevPageSlot, accent)
        }
        if (currentPage < maxPage) {
            inventory.setItem(TitleSettings.nextPageSlot, ItemUtils.namedItem(Material.ARROW, TitleSettings.msgGuiNextPage))
        } else {
            inventory.setItem(TitleSettings.nextPageSlot, accent)
        }
        inventory.setItem(TitleSettings.infoSlot, createInfoItem(player, totalTitles, currentPage, maxPage))
    }

    private fun renderInventory(inventory: Inventory, player: Player, holder: TitleMenuHolder) {
        val totalSize = TitleSettings.guiRows * 9
        renderCategoryGrid(inventory, holder)
        val previews = TitleService.getTitlePreviews(player, holder.category)
        val start = holder.page * TitleSettings.pageSize
        val end = (start + TitleSettings.pageSize).coerceAtMost(previews.size)
        previews.subList(start, end).forEachIndexed { index, preview ->
            val slot = titleIndexToSlot(index)
            if (slot in 0 until totalSize) {
                inventory.setItem(slot, createTitleItem(player, preview))
            }
        }
        val maxPage = if (previews.isEmpty()) 0 else (previews.size - 1) / TitleSettings.pageSize
        renderNavigation(inventory, player, previews.size, holder.page, maxPage)
    }

    private fun createTitleItem(player: Player, preview: TitlePreview): ItemStack {
        val base = resolveTitleDisplayItem(preview)
        TextBridge.setDisplayName(base, TextBridge.fromMiniMessage(preview.definition.displayName))
        TextBridge.setLore(base, buildTitleLore(preview))
        if (preview.isActive) {
            val meta = base.itemMeta ?: return base
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true)
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS)
            base.itemMeta = meta
        }
        return base
    }

    private fun resolveTitleDisplayItem(preview: TitlePreview): ItemStack {
        val ceSpec = preview.definition.craftEngineDisplay?.let { "ce:$it" }
        if (ceSpec != null && CraftEngineItemsUtil.isAvailable()) {
            CraftEngineItemsUtil.getItemBySpec(ceSpec, null)?.let { return it.clone() }
        }
        val icon = preview.definition.icon
        if (icon.isNotBlank()) {
            if (icon.contains(":")) {
                CraftEngineItemsUtil.getItemBySpec(icon, null)?.let { return it.clone() }
            } else {
                ItemUtils.matchMaterial(icon)?.let { return ItemStack(it) }
            }
        }
        val material = when {
            preview.isActive -> TitleSettings.activeIndicator
            preview.isExpired -> TitleSettings.expiredIndicator
            preview.entry != null -> TitleSettings.availableIndicator
            else -> TitleSettings.lockedIndicator
        }
        return ItemStack(material)
    }

    private fun buildTitleLore(preview: TitlePreview): List<Component> {
        val lore = mutableListOf<Component>()
        lore.add(TextUtils.parse("&8&m─────────────────────"))
        preview.definition.description.forEach { line ->
            lore.add(TextUtils.parse(line))
        }
        lore.add(Component.text(""))
        when {
            preview.isActive -> {
                lore.add(TextUtils.parse(TitleSettings.msgGuiEquipped))
                lore.add(TextUtils.parse(TitleSettings.msgGuiClickUnequip))
            }
            preview.isExpired -> {
                lore.add(TextUtils.parse(TitleSettings.msgGuiExpired))
            }
            preview.entry != null -> {
                lore.add(TextUtils.parse(TitleSettings.msgGuiAvailable))
                lore.add(TextUtils.parse(TitleSettings.msgGuiClickEquip))
                if (preview.remainingTime != null) {
                    lore.add(TextUtils.parse(TitleSettings.msgGuiRemaining.resolvePlaceholders("{time}" to preview.remainingTime.formatDuration())))
                } else if (preview.entry.isPermanent) {
                    lore.add(TextUtils.parse(TitleSettings.msgGuiPermanent))
                }
            }
            else -> {
                lore.add(TextUtils.parse(TitleSettings.msgGuiNotOwned))
            }
        }
        lore.add(Component.text(""))
        val rarityColor = resolveRarityColor(preview.definition.rarity)
        lore.add(Component.textOfChildren(
            TextUtils.parse(TitleSettings.msgGuiCategory.resolvePlaceholders("{category}" to preview.definition.category)),
            TextUtils.parse(" &8| "),
            TextUtils.parse(TitleSettings.msgGuiRarity.resolvePlaceholders("{rarity}" to "$rarityColor${preview.definition.rarity}"))
        ))
        return lore
    }

    private fun createInfoItem(player: Player, totalTitles: Int, currentPage: Int, maxPage: Int): ItemStack {
        val state = TitleStorage.getData(player.uniqueId)
        val ownedCount = state?.ownedTitles?.count { !it.isExpired() } ?: 0
        val activeTitle = state?.activeTitleId?.let { TitleSettings.getTitle(it) }
        val item = ItemStack(Material.BOOK)
        TextBridge.setDisplayName(item, TextUtils.parseItem(TitleSettings.msgGuiTitleSystem))
        val infoLore: List<Component> = listOf(
            Component.textOfChildren(
                TextUtils.parseItem("&7当前称号: "),
                if (activeTitle != null) TextUtils.parseItem(activeTitle.displayName) else TextUtils.parseItem(TitleSettings.msgNoTitleActive)
            ),
            TextUtils.parseItem(TitleSettings.msgGuiOwned.resolvePlaceholders("{count}" to ownedCount.toString())),
            TextUtils.parseItem(TitleSettings.msgGuiTotal.resolvePlaceholders("{count}" to totalTitles.toString())),
            TextUtils.parseItem(TitleSettings.msgGuiPageInfo
                .resolvePlaceholders("{current}" to (currentPage + 1).toString(), "{total}" to (maxPage + 1).toString())),
        )
        TextBridge.setLore(item, infoLore)
        return item
    }
}

class TitleMenuHolder(
    val category: String?,
    val page: Int,
) {
    lateinit var backingInventory: Inventory
}
