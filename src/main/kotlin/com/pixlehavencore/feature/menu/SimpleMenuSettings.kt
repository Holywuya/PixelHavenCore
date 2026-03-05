package com.pixlehavencore.feature.menu

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import taboolib.common.platform.function.getDataFolder
import java.io.File

object SimpleMenuSettings {

    @Config("feature/simple-menu.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    var menuFolder: String = "menus"
        private set

    val menus: MutableMap<String, Menu> = mutableMapOf()

    var defaultMenuId: String = "main"
        private set

    val menuIds: List<String>
        get() = menus.keys.toList()

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        menuFolder = config.getString("menuFolder") ?: "menus"
        defaultMenuId = config.getString("defaultMenu") ?: "example"
        loadMenus()
    }

    private fun loadMenus() {
        menus.clear()
        if (!enabled) {
            return
        }
        val folder = File(getDataFolder(), menuFolder)
        if (!folder.exists()) {
            folder.mkdirs()
        }
        releaseExampleMenu(folder)
        val files = folder.listFiles { file -> file.isFile && file.extension.equals("yml", true) } ?: return
        files.forEach { file ->
            val id = file.nameWithoutExtension
            val yaml = YamlConfiguration.loadConfiguration(file)
            val menu = parseMenu(id, yaml) ?: return@forEach
            menus[id] = menu
        }
    }

    private fun releaseExampleMenu(folder: File) {
        val target = File(folder, "example.yml")
        if (target.exists()) {
            return
        }
        val stream = javaClass.classLoader.getResourceAsStream("menus/example.yml") ?: return
        stream.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun parseMenu(id: String, yaml: YamlConfiguration): Menu? {
        val titles = when {
            yaml.isList("Title") -> yaml.getStringList("Title").ifEmpty { listOf("&8菜单") }
            yaml.getString("Title") != null -> listOf(yaml.getString("Title") ?: "&8菜单")
            else -> listOf("&8菜单")
        }
        val titleUpdate = yaml.getInt("Title-Update", yaml.getInt("TitleUpdate", 0)).coerceAtLeast(0)

        // Layout: 单一布局，列表中每个字符串对应一行（9个token）
        val layout = parseLayout(yaml.get("Layout"))

        val options = yaml.getConfigurationSection("Options")
        val enabled = options?.getBoolean("Enabled", true) ?: true
        val permission = options?.getString("Permission") ?: ""
        val noPermission = options?.getString("NoPermission") ?: "&c你没有权限打开该菜单。"
        val usePlaceholder = options?.getBoolean("UsePlaceholder", true) ?: true

        // 尺寸：优先 Options.Size，其次根据布局行数推算
        val sizeOption = if (options != null && options.contains("Size")) options.getInt("Size") else -1
        val size = if (sizeOption > 0) normalizeSize(sizeOption) else normalizeSize(layout.size * 9)

        val iconsSection = yaml.getConfigurationSection("Icons")
        val icons = loadIcons(iconsSection)
        val bindings = yaml.getConfigurationSection("Bindings")?.getStringList("Commands") ?: emptyList()
        val eventsSection = yaml.getConfigurationSection("Events")
        val openActions = eventsSection?.getStringList("Open") ?: emptyList()
        val closeActions = eventsSection?.getStringList("Close") ?: emptyList()

        return Menu(
            id = id,
            enabled = enabled,
            titles = titles,
            titleUpdate = titleUpdate,
            layout = layout,
            size = size,
            permission = permission,
            noPermissionMessage = noPermission,
            usePlaceholder = usePlaceholder,
            icons = icons,
            bindings = bindings.mapNotNull { runCatching { Regex(it) }.getOrNull() },
            openActions = openActions,
            closeActions = closeActions
        )
    }

    /**
     * 解析 Layout 节。接受纯字符串列表（每行9个token），例：
     *
     *   Layout:
     *     - '#########'
     *     - '#   P   #'
     *     - '########`Close`'
     *
     * 支持旧版嵌套格式（取第一页）以保持向后兼容。
     */
    private fun parseLayout(raw: Any?): List<String> {
        if (raw !is List<*> || raw.isEmpty()) return emptyList()
        val first = raw.first()
        return if (first is List<*>) {
            // 旧版多页格式：取第一页
            first.mapNotNull { it?.toString() }
        } else {
            raw.mapNotNull { it?.toString() }
        }
    }

    /**
     * 将布局行字符串解析为 slot → iconId 映射。
     *
     * 每行9个"token"，token 规则：
     *   - 普通字符（非空格）→ 单字符 iconId
     *   - `Name` （反引号包裹）→ 多字符 iconId，在行中占1个槽位
     *   - 空格 → 空槽位（跳过）
     */
    fun buildLayoutSlotMap(layout: List<String>, inventorySize: Int): Map<Int, String> {
        val map = mutableMapOf<Int, String>()
        layout.forEachIndexed { rowIndex, row ->
            val tokens = tokenizeRow(row)
            tokens.forEachIndexed { col, token ->
                val slot = rowIndex * 9 + col
                if (slot >= inventorySize) return@forEachIndexed
                if (token != " ") {
                    map[slot] = token
                }
            }
        }
        return map
    }

    /**
     * 将布局行字符串拆分为 token 列表（每个 token 占1个槽位）。
     * 反引号语法：`IconId` → 一个多字符 token。
     */
    fun tokenizeRow(row: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < row.length) {
            if (row[i] == '`') {
                val end = row.indexOf('`', i + 1)
                if (end > i) {
                    tokens.add(row.substring(i + 1, end))
                    i = end + 1
                } else {
                    // 未闭合的反引号，作为普通字符
                    tokens.add("`")
                    i++
                }
            } else {
                tokens.add(row[i].toString())
                i++
            }
        }
        return tokens
    }

    private fun loadIcons(section: ConfigurationSection?): Map<String, MenuIcon> {
        if (section == null) return emptyMap()
        val result = mutableMapOf<String, MenuIcon>()
        section.getKeys(false).forEach { key ->
            val iconSection = section.getConfigurationSection(key) ?: return@forEach
            val display = iconSection.getConfigurationSection("display")
                ?: iconSection.getConfigurationSection("Display")
                ?: iconSection

            val material = getString(display, "material", "Material", "STONE")
            val name = getStringListOrSingle(display, "name", "Name", "&fItem")
            val lore = getLore(display)
            val amount = getInt(display, "amount", "Amount", 1).coerceAtLeast(1)
            // slots 可额外指定，优先在 display 下，其次在图标根级别
            val rawSlots = display.get("slots") ?: display.get("Slots")
                ?: iconSection.get("slots") ?: iconSection.get("Slots")
            val slots = parseSlots(rawSlots)

            val actions = parseActions(
                iconSection.get("actions") ?: iconSection.get("Actions")
            )

            result[key] = MenuIcon(
                id = key,
                material = material,
                name = name,
                lore = lore,
                amount = amount,
                slots = slots,
                actions = actions
            )
        }
        return result
    }

    private fun parseActions(raw: Any?): Map<String, List<String>> {
        if (raw == null) return emptyMap()
        return when (raw) {
            is String -> mapOf("all" to listOf(raw))
            is List<*> -> mapOf("all" to raw.mapNotNull { it?.toString() })
            is ConfigurationSection -> {
                val map = mutableMapOf<String, List<String>>()
                raw.getKeys(false).forEach { key ->
                    val value = raw.get(key)
                    val list = when (value) {
                        is String -> listOf(value)
                        is List<*> -> value.mapNotNull { it?.toString() }
                        else -> emptyList()
                    }
                    map[key.lowercase()] = list
                }
                map
            }
            else -> emptyMap()
        }
    }

    /**
     * 解析槽位列表，支持：
     *   整数、字符串整数、范围字符串 "start-end"，以及以上元素的列表。
     */
    private fun parseSlots(raw: Any?): List<Int> {
        if (raw == null) return emptyList()
        val result = mutableListOf<Int>()

        fun parseEntry(entry: Any?) {
            when (entry) {
                is Number -> result.add(entry.toInt())
                is String -> {
                    val s = entry.trim()
                    val dashIdx = s.indexOf('-')
                    if (dashIdx > 0) {
                        val start = s.substring(0, dashIdx).toIntOrNull()
                        val end = s.substring(dashIdx + 1).toIntOrNull()
                        if (start != null && end != null && start <= end) {
                            result.addAll(start..end)
                            return
                        }
                    }
                    s.toIntOrNull()?.let { result.add(it) }
                }
            }
        }

        when (raw) {
            is List<*> -> raw.forEach { entry ->
                if (entry is List<*>) entry.forEach { parseEntry(it) }
                else parseEntry(entry)
            }
            else -> parseEntry(raw)
        }
        return result
    }

    private fun getString(section: ConfigurationSection?, lower: String, upper: String, def: String): String {
        if (section == null) return def
        return section.getString(lower) ?: section.getString(upper) ?: def
    }

    private fun getInt(section: ConfigurationSection?, lower: String, upper: String, def: Int): Int {
        if (section == null) return def
        return if (section.contains(lower)) section.getInt(lower, def) else section.getInt(upper, def)
    }

    private fun getStringListOrSingle(section: ConfigurationSection?, lower: String, upper: String, def: String): List<String> {
        if (section == null) return listOf(def)
        if (section.isList(lower)) return section.getStringList(lower)
        if (section.isList(upper)) return section.getStringList(upper)
        return listOf(section.getString(lower) ?: section.getString(upper) ?: def)
    }

    private fun getLore(section: ConfigurationSection?): List<String> {
        if (section == null) return emptyList()
        val raw = section.get("lore") ?: section.get("Lore") ?: return emptyList()
        return when (raw) {
            is List<*> -> {
                val first = raw.firstOrNull()
                if (first is List<*>) {
                    (first as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                } else {
                    raw.mapNotNull { it?.toString() }
                }
            }
            is String -> listOf(raw)
            else -> emptyList()
        }
    }

    private fun normalizeSize(raw: Int): Int {
        if (raw <= 0) return 54
        val clamped = raw.coerceIn(9, 54)
        return clamped - (clamped % 9)
    }

    data class Menu(
        val id: String,
        val enabled: Boolean,
        val titles: List<String>,
        val titleUpdate: Int,
        /** 布局行列表，每个字符串代表一行（9 个 token）。空列表表示不使用字符布局。 */
        val layout: List<String>,
        val size: Int,
        val permission: String,
        val noPermissionMessage: String,
        val usePlaceholder: Boolean,
        val icons: Map<String, MenuIcon>,
        val bindings: List<Regex>,
        val openActions: List<String>,
        val closeActions: List<String>
    )

    data class MenuIcon(
        val id: String,
        val material: String,
        val name: List<String>,
        val lore: List<String>,
        val amount: Int,
        /** 额外的直接槽位（可与布局并存；不在布局中的图标可通过此字段显式放置）。 */
        val slots: List<Int>,
        val actions: Map<String, List<String>>
    )
}
