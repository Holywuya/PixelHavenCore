package com.pixlehavencore.feature.optimization.viewdistance

import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.entity.Player
import taboolib.common.platform.ProxyPlayer
import taboolib.common.platform.function.info
import taboolib.common.platform.function.onlinePlayers
import taboolib.common.platform.function.submit
import taboolib.module.chat.colored
import taboolib.platform.util.submit as submitOnEntity
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ViewDistanceService {

    private val lastMoved = ConcurrentHashMap<UUID, Long>()
    private val afkPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private var afkTask: Any? = null
    private var dynamicTask: Any? = null
    private var pingTask: Any? = null

    fun init() {
        ViewDistanceSettings.init()
        stopTasks()
        if (!ViewDistanceSettings.enabled) {
            return
        }
        scheduleAfkCheck()
        scheduleDynamicMode()
        schedulePingMode()
        applyCurrentDistanceToOnlinePlayers()
    }

    fun reload() {
        init()
    }

    fun stop() {
        stopTasks()
        lastMoved.clear()
        afkPlayers.clear()
    }

    private fun stopTasks() {
        afkTask?.let(::invokeCancel)
        dynamicTask?.let(::invokeCancel)
        pingTask?.let(::invokeCancel)
        afkTask = null
        dynamicTask = null
        pingTask = null
    }

    private fun applyCurrentDistanceToOnlinePlayers() {
        // Folia: 使用 onlinePlayers() 快照 + submitOnEntity 避免在全局区域线程上直接调用 Bukkit.getPlayer
        onlinePlayers().forEach { proxy ->
            val player = proxy.cast<Player>() ?: return@forEach
            player.submitOnEntity {
                val target = resolveTargetDistance(player, proxy)
                applyDistance(player, target)
                lastMoved[player.uniqueId] = System.currentTimeMillis()
            }
        }
    }

    fun onJoin(player: Player) {
        if (!ViewDistanceSettings.enabled) {
            return
        }
        applyJoinDistance(player, player.proxy)
    }

    private fun applyJoinDistance(player: Player, proxy: ProxyPlayer) {
        val target = if (ViewDistanceSettings.afkOnJoin && !player.hasPermission(ViewDistanceSettings.bypassAfkPermission)) {
            afkPlayers.add(player.uniqueId)
            if (ViewDistanceSettings.afkEnterMessage.isNotBlank()) {
                player.sendMessage(ViewDistanceSettings.afkEnterMessage.colored())
            }
            ViewDistanceSettings.afkDistance
        } else {
            afkPlayers.remove(player.uniqueId)
            clampByLimits(ViewDistanceSettings.defaultDistance)
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
        afkPlayers.remove(player.uniqueId)
    }

    fun markMoved(player: Player) {
        lastMoved[player.uniqueId] = System.currentTimeMillis()
        if (afkPlayers.remove(player.uniqueId)) {
            val proxy = player.proxy
            val target = resolveTargetDistance(player, proxy)
            applyDistance(player, target)
            if (ViewDistanceSettings.afkExitMessage.isNotBlank()) {
                player.sendMessage(ViewDistanceSettings.afkExitMessage.colored())
            }
        }
    }

    fun setPlayerDistance(proxy: ProxyPlayer, distance: Int) {
        // 保留命令入口，但不再持久化到数据库或玩家偏好。
        // Folia: 使用 proxy.cast 替代 Bukkit.getPlayer，避免跨线程调用
        val player = proxy.cast<Player>() ?: return
        player.submitOnEntity {
            applyDistance(player, distance)
        }
    }

    fun clearPlayerDistance(proxy: ProxyPlayer) {
        // Folia: 使用 proxy.cast 替代 Bukkit.getPlayer
        val player = proxy.cast<Player>() ?: return
        player.submitOnEntity {
            applyDistance(player, ViewDistanceSettings.defaultDistance)
        }
    }

    fun getPlayerDistance(proxy: ProxyPlayer): Int? {
        return clampByLimits(ViewDistanceSettings.defaultDistance)
    }

    fun setPingMode(proxy: ProxyPlayer, enabled: Boolean) {
        // 保留命令入口，但不再持久化。
    }

    fun isPingModeEnabled(proxy: ProxyPlayer): Boolean {
        return false
    }

    fun resolvePlayerDistance(proxy: ProxyPlayer): Int {
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
        afkTask = submit(period = 100L) {
            if (!ViewDistanceSettings.enabled || !ViewDistanceSettings.afkEnabled) {
                return@submit
            }
            val now = System.currentTimeMillis()
            // Folia: 使用 onlinePlayers() 快照 + proxy.cast + submitOnEntity
            onlinePlayers().forEach { proxy ->
                val player = proxy.cast<Player>() ?: return@forEach
                player.submitOnEntity {
            if (ViewDistanceSettings.spectatorsCanAfk && player.gameMode == GameMode.SPECTATOR) {
                return@submitOnEntity
            }
            if (player.hasPermission(ViewDistanceSettings.bypassAfkPermission)) {
                        return@submitOnEntity
                    }
                    val last = lastMoved[player.uniqueId] ?: now
                    if (now - last >= ViewDistanceSettings.afkSeconds * 1000L) {
                    if (afkPlayers.add(player.uniqueId) && ViewDistanceSettings.afkEnterMessage.isNotBlank()) {
                        player.sendMessage(ViewDistanceSettings.afkEnterMessage.colored())
                    }
                    applyDistance(player, ViewDistanceSettings.afkDistance)
                } else if (afkPlayers.remove(player.uniqueId)) {
                        val target = resolveTargetDistance(player, proxy)
                        applyDistance(player, target)
                        if (ViewDistanceSettings.afkExitMessage.isNotBlank()) {
                            player.sendMessage(ViewDistanceSettings.afkExitMessage.colored())
                        }
                    }
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
                val player = proxy.cast<Player>() ?: return@forEach
                player.submitOnEntity {
                    if (player.hasPermission(ViewDistanceSettings.dynamicBypassPermission)) {
                        return@submitOnEntity
                    }
                    val base = resolveTargetDistance(player, proxy)
                    val target = (base - reduction).coerceAtLeast(ViewDistanceSettings.dynamicMin)
                    applyDistance(player, target.coerceAtMost(ViewDistanceSettings.dynamicMax))
                }
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
            // Folia: 使用 onlinePlayers() 快照 + proxy.cast + submitOnEntity
            onlinePlayers().forEach { proxy ->
                val player = proxy.cast<Player>() ?: return@forEach
                player.submitOnEntity {
                    if (!isPingModeEnabled(proxy)) {
                        return@submitOnEntity
                    }
                    val ping = PingAdapter.getPing(player)
                    if (ping < 0) {
                        return@submitOnEntity
                    }
                    val reduction = resolveReduction(ping.toDouble(), ViewDistanceSettings.pingMap)
                    val base = resolveTargetDistance(player, proxy)
                    val target = (base - reduction).coerceAtLeast(ViewDistanceSettings.pingMin)
                    applyDistance(player, target.coerceAtMost(ViewDistanceSettings.pingMax))
                }
            }
        }
    }

    private fun invokeCancel(task: Any) {
        runCatching {
            task.javaClass.methods.firstOrNull { it.name == "cancel" && it.parameterTypes.isEmpty() }?.invoke(task)
        }
    }

    private fun resolveTargetDistance(player: Player, proxy: ProxyPlayer): Int {
        if (afkPlayers.contains(player.uniqueId) && !player.hasPermission(ViewDistanceSettings.bypassAfkPermission)) {
            return ViewDistanceSettings.afkDistance
        }
        return resolvePlayerDistance(proxy)
    }

    private fun resolveReduction(value: Double, map: Map<Int, Int>): Int {
        if (map.isEmpty()) {
            return 0
        }
        var chosen = 0
        map.forEach { (threshold, reduce) ->
            if (value >= threshold && reduce > chosen) {
                chosen = reduce
            }
        }
        return chosen
    }

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
