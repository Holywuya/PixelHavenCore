package com.pixlehavencore.feature.optimization.viewdistance

import com.pixlehavencore.PixleHavenSettings
import com.pixlehavencore.util.ensureDataContainer
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.entity.Player
import taboolib.common.platform.ProxyPlayer
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.info
import taboolib.common.platform.function.onlinePlayers
import taboolib.common.platform.function.submit
import taboolib.expansion.getDataContainer
import taboolib.expansion.setupPlayerDatabase
import taboolib.module.chat.colored
import java.io.File
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

object ViewDistanceService {

    private const val KEY_DISTANCE = "vdc_distance"
    private const val KEY_PING_MODE = "vdc_ping_mode"

    // 内存缓存：加快运行时读取，避免重复访问数据库
    private val playerDistances = ConcurrentHashMap<UUID, Int>()
    private val pingModeEnabled = ConcurrentHashMap<UUID, Boolean>()

    private val lastMoved = ConcurrentHashMap<UUID, Long>()
    private var afkTask: Any? = null
    private var dynamicTask: Any? = null
    private var pingTask: Any? = null

    fun init() {
        stopTasks()
        if (!ViewDistanceSettings.enabled) {
            return
        }
        initDatabase()
        scheduleAfkCheck()
        scheduleDynamicMode()
        schedulePingMode()
        applyCurrentDistanceToOnlinePlayers()
    }

    fun reload() {
        ViewDistanceSettings.reload()
        init()
    }

    private fun stopTasks() {
        afkTask = null
        dynamicTask = null
        pingTask = null
    }

    private fun applyCurrentDistanceToOnlinePlayers() {
        onlinePlayers().forEach { proxy ->
            val player = Bukkit.getPlayer(proxy.uniqueId) ?: return@forEach
            val target = resolvePlayerDistance(proxy)
            applyDistance(player, target)
            lastMoved[player.uniqueId] = System.currentTimeMillis()
        }
    }

    fun onJoin(player: Player) {
        if (!ViewDistanceSettings.enabled) {
            return
        }
        val proxy = player.proxy
        // 从数据库加载偏好到内存缓存
        if (ViewDistanceSettings.savePlayerData) {
            loadFromDatabase(proxy)
        }
        val base = resolvePlayerDistance(proxy)
        val target = if (ViewDistanceSettings.afkOnJoin && !player.hasPermission(ViewDistanceSettings.bypassAfkPermission)) {
            ViewDistanceSettings.afkDistance
        } else {
            base
        }
        applyDistance(player, target)
        lastMoved[player.uniqueId] = System.currentTimeMillis()
        if (ViewDistanceSettings.displayOnJoin && !ViewDistanceSettings.afkOnJoin) {
            player.sendMessage(
                ViewDistanceSettings.displayJoinMessage
                    .replace("{distance}", target.toString())
                    .colored()
            )
        }
    }

    fun onQuit(player: Player) {
        lastMoved.remove(player.uniqueId)
        playerDistances.remove(player.uniqueId)
        pingModeEnabled.remove(player.uniqueId)
    }

    fun markMoved(player: Player) {
        lastMoved[player.uniqueId] = System.currentTimeMillis()
        if (ViewDistanceSettings.afkOnJoin) {
            val proxy = player.proxy
            val target = resolvePlayerDistance(proxy)
            applyDistance(player, target)
        }
    }

    fun setPlayerDistance(proxy: ProxyPlayer, distance: Int) {
        val clamped = ViewDistanceSettings.clampDistance(distance)
        playerDistances[proxy.uniqueId] = clamped
        if (ViewDistanceSettings.savePlayerData) {
            ensureContainer(proxy)
            proxy.getDataContainer()[KEY_DISTANCE] = clamped
        }
    }

    fun clearPlayerDistance(proxy: ProxyPlayer) {
        playerDistances.remove(proxy.uniqueId)
        if (ViewDistanceSettings.savePlayerData) {
            ensureContainer(proxy)
            // 写空字符串表示无偏好；toIntOrNull() 读取时自然返回 null
            proxy.getDataContainer()[KEY_DISTANCE] = ""
        }
    }

    fun getPlayerDistance(proxy: ProxyPlayer): Int? {
        return playerDistances[proxy.uniqueId]
    }

    fun setPingMode(proxy: ProxyPlayer, enabled: Boolean) {
        pingModeEnabled[proxy.uniqueId] = enabled
        if (ViewDistanceSettings.savePlayerData) {
            ensureContainer(proxy)
            proxy.getDataContainer()[KEY_PING_MODE] = enabled
        }
    }

    fun isPingModeEnabled(proxy: ProxyPlayer): Boolean {
        return pingModeEnabled[proxy.uniqueId] ?: false
    }

    fun resolvePlayerDistance(proxy: ProxyPlayer): Int {
        val stored = getPlayerDistance(proxy)
        if (stored != null) {
            return clampByLimits(stored)
        }
        return clampByLimits(ViewDistanceSettings.defaultDistance)
    }

    fun clampByLimits(distance: Int): Int {
        return distance.coerceIn(ViewDistanceSettings.minDistance, ViewDistanceSettings.maxDistance)
    }

    fun applyDistance(player: Player, distance: Int) {
        val clamped = clampByLimits(distance)
        if (!ViewDistanceAdapter.applyViewDistance(player, clamped)) {
            return
        }
        if (ViewDistanceSettings.syncSimulationDistance) {
            ViewDistanceAdapter.applySimulationDistance(player, clamped)
        }
    }

    private fun scheduleAfkCheck() {
        if (!ViewDistanceSettings.afkEnabled) {
            return
        }
        afkTask = submit(period = 20L) {
            if (!ViewDistanceSettings.enabled || !ViewDistanceSettings.afkEnabled) {
                return@submit
            }
            val now = System.currentTimeMillis()
            Bukkit.getOnlinePlayers().forEach { player ->
                if (ViewDistanceSettings.spectatorsCanAfk && player.gameMode == GameMode.SPECTATOR) {
                    return@forEach
                }
                if (player.hasPermission(ViewDistanceSettings.bypassAfkPermission)) {
                    return@forEach
                }
                val last = lastMoved[player.uniqueId] ?: now
                if (now - last >= ViewDistanceSettings.afkSeconds * 1000L) {
                    applyDistance(player, ViewDistanceSettings.afkDistance)
                }
            }
        }
    }

    private fun scheduleDynamicMode() {
        if (!ViewDistanceSettings.dynamicEnabled) {
            return
        }
        dynamicTask = submit(period = ViewDistanceSettings.dynamicIntervalTicks) {
            if (!ViewDistanceSettings.enabled || !ViewDistanceSettings.dynamicEnabled) {
                return@submit
            }
            val mspt = MsptAdapter.getMspt()
            if (mspt < 0) {
                return@submit
            }
            val reduction = resolveReduction(mspt, ViewDistanceSettings.dynamicMsptMap)
            onlinePlayers().forEach { proxy ->
                if (proxy.hasPermission(ViewDistanceSettings.dynamicBypassPermission)) {
                    return@forEach
                }
                val player = Bukkit.getPlayer(proxy.uniqueId) ?: return@forEach
                val base = resolvePlayerDistance(proxy)
                val target = (base - reduction).coerceAtLeast(ViewDistanceSettings.dynamicMin)
                applyDistance(player, target.coerceAtMost(ViewDistanceSettings.dynamicMax))
            }
        }
    }

    private fun schedulePingMode() {
        if (!ViewDistanceSettings.pingEnabled) {
            return
        }
        pingTask = submit(period = ViewDistanceSettings.pingIntervalTicks) {
            if (!ViewDistanceSettings.enabled || !ViewDistanceSettings.pingEnabled) {
                return@submit
            }
            onlinePlayers().forEach { proxy ->
                if (!isPingModeEnabled(proxy)) {
                    return@forEach
                }
                val player = Bukkit.getPlayer(proxy.uniqueId) ?: return@forEach
                val ping = PingAdapter.getPing(player)
                if (ping < 0) {
                    return@forEach
                }
                val reduction = resolveReduction(ping.toDouble(), ViewDistanceSettings.pingMap)
                val base = resolvePlayerDistance(proxy)
                val target = (base - reduction).coerceAtLeast(ViewDistanceSettings.pingMin)
                applyDistance(player, target.coerceAtMost(ViewDistanceSettings.pingMax))
            }
        }
    }

    private fun resolveReduction(value: Double, map: Map<Int, Int>): Int {
        if (map.isEmpty()) {
            return 0
        }
        var chosen = 0
        map.forEach { (threshold, reduce) ->
            if (value >= threshold) {
                chosen = max(chosen, reduce)
            }
        }
        return chosen
    }

    private fun initDatabase() {
        if (PixleHavenSettings.databaseType == "mysql") {
            setupPlayerDatabase(
                host = PixleHavenSettings.mysqlHost,
                port = PixleHavenSettings.mysqlPort.toIntOrNull() ?: 3306,
                user = PixleHavenSettings.mysqlUser,
                password = PixleHavenSettings.mysqlPassword,
                database = PixleHavenSettings.mysqlDatabase,
                table = "vdc_data"
            )
        } else {
            setupPlayerDatabase(File(getDataFolder(), PixleHavenSettings.sqliteFile), "vdc_data")
        }
    }

    // 玩家进服时从数据库加载偏好到内存缓存
    private fun loadFromDatabase(proxy: ProxyPlayer) {
        ensureContainer(proxy)
        val container = proxy.getDataContainer()
        container[KEY_DISTANCE]?.toIntOrNull()?.let { dist ->
            playerDistances[proxy.uniqueId] = ViewDistanceSettings.clampDistance(dist)
        }
        container[KEY_PING_MODE]?.toBoolean()?.let { mode ->
            pingModeEnabled[proxy.uniqueId] = mode
        }
    }

    private fun ensureContainer(proxy: ProxyPlayer) = proxy.ensureDataContainer()

    private val Player.proxy: ProxyPlayer
        get() = taboolib.common.platform.function.adaptPlayer(this)

    private object ViewDistanceAdapter {
        private val viewDistanceMethods = ConcurrentHashMap<Class<*>, Method?>()
        private val simulationDistanceMethods = ConcurrentHashMap<Class<*>, Method?>()
        private var warnedUnsupported = false
        private var warnedInvokeFailed = false

        fun applyViewDistance(player: Player, value: Int): Boolean {
            val method = findMethod(player, "setViewDistance", viewDistanceMethods)
            if (method == null) {
                warnUnsupported()
                return false
            }
            return invoke(method, player, value)
        }

        fun applySimulationDistance(player: Player, value: Int) {
            val method = findMethod(player, "setSimulationDistance", simulationDistanceMethods) ?: return
            invoke(method, player, value)
        }

        private fun findMethod(player: Player, name: String, cache: ConcurrentHashMap<Class<*>, Method?>): Method? {
            val clazz = player.javaClass
            return cache[clazz] ?: run {
                val method = clazz.methods.firstOrNull { candidate ->
                    if (candidate.name != name || candidate.parameterTypes.size != 1) return@firstOrNull false
                    val param = candidate.parameterTypes[0]
                    param == Int::class.javaPrimitiveType || param == Int::class.javaObjectType
                }
                cache[clazz] = method
                method
            }
        }

        private fun invoke(method: Method, player: Player, value: Int): Boolean {
            val success = runCatching { method.invoke(player, value) }.isSuccess
            if (!success) {
                warnInvokeFailed()
            }
            return success
        }

        private fun warnUnsupported() {
            if (warnedUnsupported) {
                return
            }
            warnedUnsupported = true
            info("ViewDistanceController: server does not support player view distance API.")
        }

        private fun warnInvokeFailed() {
            if (warnedInvokeFailed) {
                return
            }
            warnedInvokeFailed = true
            info("ViewDistanceController: failed to apply player view distance; check server compatibility.")
        }
    }

    private object MsptAdapter {
        private val method = runCatching { Bukkit::class.java.getMethod("getAverageTickTime") }.getOrNull()
        private var warned = false

        fun getMspt(): Double {
            val method = method ?: return warnUnsupported()
            return runCatching { method.invoke(null) as? Double ?: -1.0 }.getOrDefault(-1.0)
        }

        private fun warnUnsupported(): Double {
            if (!warned) {
                warned = true
                info("ViewDistanceController: MSPT API not available; dynamic mode disabled.")
            }
            return -1.0
        }
    }

    private object PingAdapter {
        private val method = runCatching { Player::class.java.getMethod("getPing") }.getOrNull()
        private var warned = false

        fun getPing(player: Player): Int {
            val method = method ?: return warnUnsupported()
            return runCatching { method.invoke(player) as? Int ?: -1 }.getOrDefault(-1)
        }

        private fun warnUnsupported(): Int {
            if (!warned) {
                warned = true
                info("ViewDistanceController: ping API not available; ping mode disabled.")
            }
            return -1
        }
    }
}
