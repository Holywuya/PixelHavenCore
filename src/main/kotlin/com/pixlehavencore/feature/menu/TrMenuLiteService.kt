package com.pixlehavencore.feature.menu

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.submit
import com.pixlehavencore.util.KetherUtil
import taboolib.module.chat.colored
import taboolib.platform.compat.replacePlaceholder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object TrMenuLiteService {

    private data class Session(
        val menuId: String,
        var titleIndex: Int = 0,
        var titleTask: Any? = null
    )

    private val sessions = ConcurrentHashMap<UUID, Session>()

    fun init() {
        if (!SimpleMenuSettings.enabled) {
            return
        }
        if (SimpleMenuSettings.menuIds.isEmpty()) {
            return
        }
    }

    fun openMenu(player: Player, menuId: String) {
        if (!SimpleMenuSettings.enabled) {
            return
        }
        val menu = SimpleMenuSettings.menus[menuId] ?: return
        if (!menu.enabled) {
            return
        }
        if (menu.permission.isNotBlank() && !player.hasPermission(menu.permission)) {
            if (menu.noPermissionMessage.isNotBlank()) {
                player.sendMessage(menu.noPermissionMessage.colored())
            }
            return
        }
        val size = normalizeSize(menu.size)
        val title = resolveTitle(player, menu, 0)
        val inventory = Bukkit.createInventory(null, size, title.colored())
        renderMenu(menu, inventory, player)
        val session = Session(menuId = menuId)
        sessions[player.uniqueId] = session
        if (menu.titleUpdate > 0 && menu.titles.size > 1) {
            scheduleTitleUpdate(player, menu, session)
        }
        player.openInventory(inventory)
        if (menu.openActions.isNotEmpty()) {
            executeActions(player, menu.openActions, menu.usePlaceholder)
        }
    }

    fun handleClick(player: Player, inventory: Inventory, slot: Int, clickType: ClickType): Boolean {
        val session = sessions[player.uniqueId] ?: return false
        val menu = SimpleMenuSettings.menus[session.menuId] ?: return false
        if (inventory.size != normalizeSize(menu.size)) {
            return false
        }
        if (inventory.viewers.none { it.uniqueId == player.uniqueId }) {
            return false
        }
        if (slot < 0 || slot >= inventory.size) {
            return true
        }
        // 重建 slot → iconId 映射以确定点击的图标
        val slotMap = buildSlotMap(menu, inventory.size)
        val iconId = slotMap[slot] ?: return true
        val icon = menu.icons[iconId] ?: return true
        val actions = resolveActions(icon, clickType)
        executeActions(player, actions, menu.usePlaceholder, session)
        return true
    }

    fun handleClose(player: Player) {
        val session = sessions.remove(player.uniqueId) ?: return
        val menu = SimpleMenuSettings.menus[session.menuId]
        if (menu != null && menu.closeActions.isNotEmpty()) {
            executeActions(player, menu.closeActions, menu.usePlaceholder, session)
        }
        session.titleTask = null
    }

    /**
     * 渲染菜单：先按 Layout 字符布局放置图标，再放置带有显式 slots 的图标（覆盖）。
     */
    private fun renderMenu(menu: SimpleMenuSettings.Menu, inventory: Inventory, player: Player) {
        val slotMap = buildSlotMap(menu, inventory.size)
        slotMap.forEach { (slot, iconId) ->
            val icon = menu.icons[iconId] ?: return@forEach
            inventory.setItem(slot, buildIcon(icon, player, menu.usePlaceholder))
        }
    }

    /**
     * 构建完整的 slot → iconId 映射：
     *   1. 先解析 Layout 字符布局（每行9个token，支持反引号多字符ID）
     *   2. 再用各图标的显式 slots 覆盖
     */
    private fun buildSlotMap(menu: SimpleMenuSettings.Menu, inventorySize: Int): Map<Int, String> {
        val map = mutableMapOf<Int, String>()

        // 1. 字符布局
        if (menu.layout.isNotEmpty()) {
            map.putAll(SimpleMenuSettings.buildLayoutSlotMap(menu.layout, inventorySize))
        }

        // 2. 显式 slots 覆盖
        menu.icons.values.forEach { icon ->
            if (icon.slots.isNotEmpty()) {
                icon.slots.forEach { slot ->
                    if (slot in 0 until inventorySize) {
                        map[slot] = icon.id
                    }
                }
            }
        }
        return map
    }

    /**
     * 执行动作列表。使用 for 循环以支持 return 提前终止。
     *
     * 支持的动作：
     *   close              — 关闭菜单，停止后续动作
     *   return             — 停止后续动作（不关闭菜单）
     *   open: <menuId>     — 延迟1tick打开另一菜单
     *   tell: <message>    — 发送带颜色的消息
     *   sound: NAME-vol-pitch — 播放音效（例：BLOCK_CHEST_OPEN-1-1）
     *   cmd: <command>     — 玩家执行命令
     *   console: <command> — 控制台执行命令
     *   kether: <script>   — 执行 Kether 脚本（单行）
     */
    private fun executeActions(
        player: Player,
        actions: List<String>,
        usePlaceholder: Boolean,
        session: Session? = null
    ) {
        if (actions.isEmpty()) return
        for (raw in actions) {
            val action = raw.trim()
            when {
                action.equals("close", ignoreCase = true) -> {
                    player.closeInventory()
                    return
                }
                action.equals("return", ignoreCase = true) -> {
                    return
                }
                action.startsWith("open:", ignoreCase = true) -> {
                    val target = action.substringAfter(":").trim()
                    if (target.isNotBlank()) {
                        submit(delay = 1L) { openMenu(player, target) }
                    }
                }
                action.startsWith("tell:", ignoreCase = true) -> {
                    val message = applyPlaceholders(player, action.substringAfter(":").trim(), usePlaceholder)
                    if (message.isNotBlank()) {
                        player.sendMessage(message.colored())
                    }
                }
                action.startsWith("sound:", ignoreCase = true) -> {
                    val soundStr = action.substringAfter(":").trim()
                    if (soundStr.isNotBlank()) {
                        playSound(player, soundStr)
                    }
                }
                action.startsWith("cmd:", ignoreCase = true) -> {
                    val command = applyPlaceholders(player, action.substringAfter(":").trim(), usePlaceholder)
                    if (command.isNotBlank()) {
                        player.performCommand(command)
                    }
                }
                 action.startsWith("console:", ignoreCase = true) -> {
                    val command = applyPlaceholders(player, action.substringAfter(":").trim(), usePlaceholder)
                    if (command.isNotBlank()) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
                    }
                }
                action.startsWith("kether:", ignoreCase = true) -> {
                    val script = applyPlaceholders(player, action.substringAfter(":").trim(), usePlaceholder)
                    if (script.isNotBlank()) {
                        KetherUtil.eval(player, script)
                    }
                }
            }
        }
    }

    /**
     * 解析并播放音效，格式：SOUND_NAME-volume-pitch
     * 例：BLOCK_CHEST_OPEN-1-1 或 ENTITY_PLAYER_LEVELUP-1-0.5
     * MC 原版音效名仅含下划线，因此用最后两个 "-" 分隔 volume/pitch。
     */
    private fun playSound(player: Player, soundStr: String) {
        val parts = soundStr.split("-")
        val pitch = if (parts.size >= 3) parts.last().toFloatOrNull() ?: 1f else 1f
        val volume = if (parts.size >= 2) parts[parts.size - 2].toFloatOrNull() ?: 1f else 1f
        val soundName = when {
            parts.size >= 3 -> parts.dropLast(2).joinToString("-")
            parts.size == 2 -> parts.dropLast(1).joinToString("-")
            else -> soundStr
        }
        val sound = runCatching { Sound.valueOf(soundName.uppercase()) }.getOrNull() ?: return
        player.playSound(player.location, sound, volume, pitch)
    }

    private fun applyPlaceholders(player: Player, text: String, usePlaceholder: Boolean): String {
        var result = text
            .replace("{player}", player.name)
            .replace("{world}", player.world.name)
            .replace("{online}", Bukkit.getOnlinePlayers().size.toString())
        if (usePlaceholder) {
            result = result.replacePlaceholder(player)
        }
        return result
    }

    private fun resolveTitle(player: Player, menu: SimpleMenuSettings.Menu, index: Int): String {
        val title = menu.titles.getOrNull(index) ?: menu.titles.firstOrNull() ?: "&8菜单"
        return applyPlaceholders(player, title, menu.usePlaceholder)
    }

    private fun scheduleTitleUpdate(player: Player, menu: SimpleMenuSettings.Menu, session: Session) {
        val period = menu.titleUpdate.coerceAtLeast(1)
        session.titleTask = submit(period = period.toLong()) {
            val current = sessions[player.uniqueId] ?: return@submit
            if (current.menuId != menu.id) return@submit
            current.titleIndex = (current.titleIndex + 1) % menu.titles.size
            val title = resolveTitle(player, menu, current.titleIndex).colored()
            runCatching {
                val method = player.openInventory.javaClass.getMethod("setTitle", String::class.java)
                method.invoke(player.openInventory, title)
            }
        }
    }

    private fun buildIcon(icon: SimpleMenuSettings.MenuIcon, player: Player, usePlaceholder: Boolean): ItemStack {
        val material = resolveMaterial(icon.material)
        val stack = ItemStack(material, icon.amount.coerceAtLeast(1))
        val meta = stack.itemMeta
        if (meta != null) {
            val name = icon.name.firstOrNull() ?: "&fItem"
            meta.setDisplayName(applyPlaceholders(player, name, usePlaceholder).colored())
            if (icon.lore.isNotEmpty()) {
                meta.lore = icon.lore.map { applyPlaceholders(player, it, usePlaceholder).colored() }
            }
            stack.itemMeta = meta
        }
        return stack
    }

    private fun resolveMaterial(value: String): Material {
        val raw = value.trim()
        val clean = raw.substringAfter(":", raw).substringAfter(" ", raw)
        return runCatching { Material.valueOf(clean.uppercase()) }.getOrDefault(Material.STONE)
    }

    private fun resolveActions(icon: SimpleMenuSettings.MenuIcon, clickType: ClickType): List<String> {
        val key = when (clickType) {
            ClickType.LEFT -> "left"
            ClickType.RIGHT -> "right"
            ClickType.SHIFT_LEFT -> "shift_left"
            ClickType.SHIFT_RIGHT -> "shift_right"
            ClickType.MIDDLE -> "middle"
            ClickType.NUMBER_KEY -> "number_key"
            else -> "all"
        }
        return icon.actions[key] ?: icon.actions["all"] ?: emptyList()
    }

    private fun normalizeSize(raw: Int): Int {
        val clamped = raw.coerceIn(9, 54)
        return clamped - (clamped % 9)
    }
}
