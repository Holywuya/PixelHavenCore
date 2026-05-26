package com.pixlehavencore.feature.title

import com.pixlehavencore.util.ItemUtils
import org.bukkit.Material
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object TitleSettings {

    @Config("feature/title/config.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set
    var papiEnabled: Boolean = true
        private set

    // GUI
    var guiTitle: String = "<gradient:gold:yellow>称号系统</gradient>"
        private set
    var guiRows: Int = 6
        private set
    var borderItem: Material = Material.GRAY_STAINED_GLASS_PANE
        private set
    var categorySlots: List<Int> = listOf(7, 17, 26, 35, 44)
        private set
    var titleStartSlot: Int = 10
        private set
    var pageSize: Int = 28
        private set
    var prevPageSlot: Int = 48
        private set
    var infoSlot: Int = 49
        private set
    var nextPageSlot: Int = 50
        private set
    var activeIndicator: Material = Material.LIME_STAINED_GLASS_PANE
        private set
    var expiredIndicator: Material = Material.RED_STAINED_GLASS_PANE
        private set
    var availableIndicator: Material = Material.WHITE_STAINED_GLASS_PANE
        private set
    var lockedIndicator: Material = Material.ORANGE_STAINED_GLASS_PANE
        private set
    var borderAccent: Material = Material.BLACK_STAINED_GLASS_PANE
        private set
    var rarityColors: Map<String, String> = mapOf(
        "common" to "&7",
        "uncommon" to "&a",
        "rare" to "&9",
        "epic" to "&5",
        "legendary" to "&6",
    )
        private set

    // Default title
    var defaultTitleEnabled: Boolean = false
        private set
    var defaultTitleId: String = ""
        private set
    var defaultTitleAutoEquip: Boolean = true
        private set

    // Expiry
    var expiryCheckTicks: Long = 6000L
        private set

    // Messages
    var msgActivated: String = "&a已装备称号: {title}"
        private set
    var msgDeactivated: String = "&e已卸下称号。"
        private set
    var msgNotOwned: String = "&c你尚未拥有该称号。"
        private set
    var msgExpired: String = "&c该称号已过期。"
        private set
    var msgNoPermission: String = "&c你没有使用该称号的权限。"
        private set
    var msgGiven: String = "&a已发放称号 &f{title} &a给 &f{player}&a。"
        private set
    var msgRemoved: String = "&a已移除玩家 &f{player} &a的称号 &f{title}&a。"
        private set
    var msgReload: String = "&a称号配置已重载。"
        private set
    var msgNoTitleActive: String = "&7无称号"
        private set
    var msgGuiNoTitles: String = "&7暂无可用称号"
        private set
    var msgGuiPageInfo: String = "&7第 {current}/{total} 页"
        private set
    var msgGuiCategoryAll: String = "全部"
        private set
    var msgGuiCategoryFilter: String = "&7点击筛选"
        private set
    var msgGuiCategoryCurrent: String = "&7当前筛选"
        private set
    var msgGuiPrevPage: String = "&e上一页"
        private set
    var msgGuiNextPage: String = "&e下一页"
        private set
    var msgGuiEquipped: String = "&a✔ 已装备"
        private set
    var msgGuiClickUnequip: String = "&7点击卸下称号"
        private set
    var msgGuiExpired: String = "&c✘ 已过期"
        private set
    var msgGuiAvailable: String = "&e可装备"
        private set
    var msgGuiClickEquip: String = "&7点击装备称号"
        private set
    var msgGuiPermanent: String = "&7有效期: &a永久"
        private set
    var msgGuiNotOwned: String = "&c✘ 未拥有"
        private set
    var msgGuiTitleSystem: String = "&6称号系统"
        private set
    var msgGuiOwned: String = "&7已拥有: &f{count}"
        private set
    var msgGuiTotal: String = "&7总称号: &f{count}"
        private set
    var msgGuiCategory: String = "&7分类: &f{category}"
        private set
    var msgGuiRarity: String = "&7稀有度: &f{rarity}"
        private set
    var msgGuiRemaining: String = "&7剩余时间: &f{time}"
        private set

    // Title definitions
    private var titleDefinitions: Map<String, TitleDefinition> = emptyMap()

    fun init() = reload()

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        papiEnabled = config.getBoolean("papi.enabled", true)

        guiTitle = config.getString("gui.title") ?: guiTitle
        guiRows = config.getInt("gui.rows", 6).coerceIn(1, 6)
        borderItem = resolveMaterial(config.getString("gui.border_item"), borderItem)
        categorySlots = config.getIntegerList("gui.category_slots").ifEmpty { listOf(7, 17, 26, 35, 44) }
        titleStartSlot = config.getInt("gui.title_start_slot", 10)
        pageSize = config.getInt("gui.page_size", 28).coerceIn(1, (guiRows - 2) * 7)
        prevPageSlot = config.getInt("gui.prev_page_slot", 48)
        infoSlot = config.getInt("gui.info_slot", 49)
        nextPageSlot = config.getInt("gui.next_page_slot", 50)
        activeIndicator = resolveMaterial(config.getString("gui.active_indicator"), activeIndicator)
        expiredIndicator = resolveMaterial(config.getString("gui.expired_indicator"), expiredIndicator)
        availableIndicator = resolveMaterial(config.getString("gui.available_indicator"), availableIndicator)
        lockedIndicator = resolveMaterial(config.getString("gui.locked_indicator"), lockedIndicator)
        borderAccent = resolveMaterial(config.getString("gui.border_accent"), borderAccent)
        val raritySection = config.getConfigurationSection("gui.rarity_colors")
        if (raritySection != null) {
            val map = mutableMapOf<String, String>()
            raritySection.getKeys(false).forEach { key ->
                map[key] = raritySection.getString(key) ?: ""
            }
            rarityColors = map
        }

        defaultTitleEnabled = config.getBoolean("default_title.enabled", false)
        defaultTitleId = config.getString("default_title.title_id") ?: ""
        defaultTitleAutoEquip = config.getBoolean("default_title.auto_equip", true)

        expiryCheckTicks = config.getLong("expiry.check_interval_ticks", 6000L).coerceAtLeast(200L)

        loadMessages()

        titleDefinitions = TitleDefinitionLoader.loadAll()
    }

    fun getTitle(id: String): TitleDefinition? = titleDefinitions[id]

    fun getAllTitles(): Collection<TitleDefinition> = titleDefinitions.values

    fun getAllTitleIds(): List<String> = titleDefinitions.keys.toList()

    fun getTitlesByCategory(category: String): List<TitleDefinition> =
        titleDefinitions.values.filter { it.category.equals(category, ignoreCase = true) }

    fun getCategories(): List<String> =
        titleDefinitions.values.map { it.category }.distinct().sorted()

    fun getTitleCount(): Int = titleDefinitions.size

    private fun loadMessages() {
        msgActivated = readMsg("messages.title_activated", msgActivated)
        msgDeactivated = readMsg("messages.title_deactivated", msgDeactivated)
        msgNotOwned = readMsg("messages.title_not_owned", msgNotOwned)
        msgExpired = readMsg("messages.title_expired", msgExpired)
        msgNoPermission = readMsg("messages.title_no_permission", msgNoPermission)
        msgGiven = readMsg("messages.title_given", msgGiven)
        msgRemoved = readMsg("messages.title_removed", msgRemoved)
        msgReload = readMsg("messages.reload_success", msgReload)
        msgNoTitleActive = readMsg("messages.no_title_active", msgNoTitleActive)
        msgGuiNoTitles = readMsg("messages.gui_no_titles", msgGuiNoTitles)
        msgGuiPageInfo = readMsg("messages.gui_page_info", msgGuiPageInfo)
        msgGuiCategoryAll = readMsg("messages.gui_category_all", msgGuiCategoryAll)
        msgGuiCategoryFilter = readMsg("messages.gui_category_filter", msgGuiCategoryFilter)
        msgGuiCategoryCurrent = readMsg("messages.gui_category_current", msgGuiCategoryCurrent)
        msgGuiPrevPage = readMsg("messages.gui_prev_page", msgGuiPrevPage)
        msgGuiNextPage = readMsg("messages.gui_next_page", msgGuiNextPage)
        msgGuiEquipped = readMsg("messages.gui_equipped", msgGuiEquipped)
        msgGuiClickUnequip = readMsg("messages.gui_click_unequip", msgGuiClickUnequip)
        msgGuiExpired = readMsg("messages.gui_expired", msgGuiExpired)
        msgGuiAvailable = readMsg("messages.gui_available", msgGuiAvailable)
        msgGuiClickEquip = readMsg("messages.gui_click_equip", msgGuiClickEquip)
        msgGuiPermanent = readMsg("messages.gui_permanent", msgGuiPermanent)
        msgGuiNotOwned = readMsg("messages.gui_not_owned", msgGuiNotOwned)
        msgGuiTitleSystem = readMsg("messages.gui_title_system", msgGuiTitleSystem)
        msgGuiOwned = readMsg("messages.gui_owned", msgGuiOwned)
        msgGuiTotal = readMsg("messages.gui_total", msgGuiTotal)
        msgGuiCategory = readMsg("messages.gui_category", msgGuiCategory)
        msgGuiRarity = readMsg("messages.gui_rarity", msgGuiRarity)
        msgGuiRemaining = readMsg("messages.gui_remaining", msgGuiRemaining)
    }

    private fun readMsg(path: String, fallback: String): String {
        return config.getString(path) ?: fallback
    }

    private fun resolveMaterial(name: String?, fallback: Material): Material {
        return ItemUtils.matchMaterial(name, fallback) ?: fallback
    }
}
