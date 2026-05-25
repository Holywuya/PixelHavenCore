package com.pixlehavencore.feature.realworld

import com.pixlehavencore.util.cancelTaskSafely
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerInteractEvent
import taboolib.common.platform.event.EventPriority
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.potion.PotionType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.info
import taboolib.common.platform.function.onlinePlayers
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.platform.util.isRightClick
import taboolib.platform.util.submit as submitOnEntity

object RealWorldService {

    @Volatile
    private var shuttingDown = false

    @Volatile
    private var globalState: GlobalEnvState? = null

    private val globalStateLock = Any()
    private val lifecycleGeneration = AtomicLong(0L)
    private val pendingQuitAt = ConcurrentHashMap<UUID, Long>()

    private var tickTask: Any? = null
    private var autoSaveTask: Any? = null

    fun init() {
        RealWorldSettings.init()
        stop()
        if (!RealWorldSettings.enabled) {
            return
        }

        lifecycleGeneration.incrementAndGet()
        shuttingDown = false
        pendingQuitAt.clear()
        RealWorldStorage.init()
        synchronized(globalStateLock) {
            globalState = RealWorldStorage.loadGlobal()
        }
        startTickTask()
        startAutoSaveTask()
        loadOnlinePlayersData()
        info("[RealWorld] 模块已启动，在线玩家 ${onlinePlayers().size} 人。")
    }

    fun reload() {
        RealWorldSettings.reload()
        stopInternal()
        if (!RealWorldSettings.enabled) {
            return
        }

        lifecycleGeneration.incrementAndGet()
        shuttingDown = false
        pendingQuitAt.clear()
        RealWorldStorage.reload()
        synchronized(globalStateLock) {
            globalState = RealWorldStorage.loadGlobal()
        }
        startTickTask()
        startAutoSaveTask()
        loadOnlinePlayersData()
        info("[RealWorld] 模块已重载，在线玩家 ${onlinePlayers().size} 人。")
    }

    fun stop() {
        stopInternal()
    }

    private fun stopInternal() {
        shuttingDown = true
        lifecycleGeneration.incrementAndGet()
        pendingQuitAt.clear()
        stopTasks()
        saveOnlinePlayers()
        val globalSnapshot = synchronized(globalStateLock) {
            globalState?.copy()
        }
        globalSnapshot?.let { state ->
            RealWorldStorage.saveGlobal(state)
        }
        clearOnlineHud()
        RealWorldStorage.stop()
        synchronized(globalStateLock) {
            globalState = null
        }
    }

    fun getGlobalState(): GlobalEnvState? {
        if (!RealWorldSettings.enabled) {
            return null
        }
        return synchronized(globalStateLock) {
            globalState?.copy()
        }
    }

    fun forceSeason(season: Season) {
        if (!RealWorldSettings.enabled) {
            return
        }
        synchronized(globalStateLock) {
            val state = globalState ?: return
            state.season = season
            state.seasonProgress = 0.0
            RealWorldStorage.markGlobalDirty(state)
        }
    }

    fun forceWeather(weather: WeatherType) {
        if (!RealWorldSettings.enabled) {
            return
        }
        synchronized(globalStateLock) {
            val state = globalState ?: return
            WeatherEngine.setWeather(state, weather)
            syncVanillaWeather(state)
            RealWorldStorage.markGlobalDirty(state)
        }
    }

    @SubscribeEvent
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!RealWorldSettings.enabled) {
            return
        }
        pendingQuitAt.remove(event.player.uniqueId)
        preloadPlayer(event.player)
    }

    @SubscribeEvent
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (!RealWorldSettings.enabled) {
            return
        }

        val player = event.player
        val uuid = player.uniqueId
        val generation = lifecycleGeneration.get()
        val quitMark = System.currentTimeMillis()
        pendingQuitAt[uuid] = quitMark
        SurvivalHud.onPlayerQuit(player)
        submitAsync {
            if (shuttingDown || generation != lifecycleGeneration.get() || !RealWorldSettings.enabled) {
                return@submitAsync
            }
            RealWorldStorage.savePlayer(uuid)
            if (shuttingDown || generation != lifecycleGeneration.get() || !RealWorldSettings.enabled) {
                return@submitAsync
            }
            if (pendingQuitAt[uuid] != quitMark) {
                return@submitAsync
            }
            RealWorldStorage.removePlayerFromCache(uuid)
            pendingQuitAt.remove(uuid, quitMark)
        }
    }

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerItemConsume(event: PlayerItemConsumeEvent) {
        if (!RealWorldSettings.enabled) {
            return
        }
        if (!isWaterBottle(event.item.type, event.item.itemMeta as? PotionMeta)) {
            return
        }

        val player = event.player
        val uuid = player.uniqueId
        val generation = lifecycleGeneration.get()
        player.submitOnEntity {
            if (shuttingDown || generation != lifecycleGeneration.get() || !RealWorldSettings.enabled) {
                return@submitOnEntity
            }
            RealWorldStorage.withPlayerState(uuid) { state ->
                ThirstEngine.onWaterBottleConsume(state)
            } ?: return@submitOnEntity
            if (shuttingDown || generation != lifecycleGeneration.get() || !RealWorldSettings.enabled) {
                return@submitOnEntity
            }
            RealWorldStorage.markPlayerDirty(uuid)
        }
    }

    @SubscribeEvent(ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (!RealWorldSettings.enabled) {
            return
        }
        if (!event.isRightClick()) {
            return
        }
        if (event.hand != null && event.hand != EquipmentSlot.HAND) {
            return
        }

        val block = event.clickedBlock ?: return
        if (block.type != Material.WATER) {
            return
        }

        val player = event.player
        val uuid = player.uniqueId
        val generation = lifecycleGeneration.get()
        player.submitOnEntity {
            if (shuttingDown || generation != lifecycleGeneration.get() || !RealWorldSettings.enabled) {
                return@submitOnEntity
            }
            RealWorldStorage.withPlayerState(uuid) { state ->
                ThirstEngine.onRightClickWaterSource(player, state, block)
            } ?: return@submitOnEntity
            RealWorldStorage.markPlayerDirty(uuid)
        }
    }

    private fun startTickTask() {
        val periodTicks = RealWorldSettings.tickIntervalSeconds.coerceAtLeast(1) * 20L
        val generation = lifecycleGeneration.get()
        tickTask = submit(delay = periodTicks, period = periodTicks) {
            if (shuttingDown || generation != lifecycleGeneration.get() || !RealWorldSettings.enabled) {
                return@submit
            }

            val tickSeconds = RealWorldSettings.tickIntervalSeconds
            val globalSnapshot = synchronized(globalStateLock) {
                val state = globalState ?: return@submit
                SeasonEngine.tick(state, tickSeconds)
                WeatherEngine.tick(state, tickSeconds)
                state.dayPhase = SeasonEngine.computeDayPhase(Bukkit.getWorlds().firstOrNull()?.time ?: 6000L)
                syncVanillaWeather(state)
                RealWorldStorage.markGlobalDirty(state)
                state.copy()
            }
            onlinePlayers().forEach { proxy ->
                val player = proxy.cast<Player>() ?: return@forEach
                player.submitOnEntity {
                    if (shuttingDown || generation != lifecycleGeneration.get() || !RealWorldSettings.enabled) {
                        return@submitOnEntity
                    }
                    RealWorldStorage.withPlayerState(player.uniqueId) { playerState ->
                        TemperatureEngine.compute(player, playerState, globalSnapshot, tickSeconds)
                        ThirstEngine.compute(player, playerState, globalSnapshot, tickSeconds)
                        SurvivalEffectApplier.apply(player, playerState, tickSeconds)
                        playerState.hudRefreshTimer -= tickSeconds.coerceAtLeast(0).toDouble()
                        if (playerState.hudRefreshTimer <= 0.0) {
                            SurvivalHud.renderCurrentThread(player, playerState, globalSnapshot)
                            val refreshInterval = RealWorldSettings.hudRefreshIntervalSeconds.toDouble()
                            while (playerState.hudRefreshTimer <= 0.0) {
                                playerState.hudRefreshTimer += refreshInterval
                            }
                        }
                    } ?: return@submitOnEntity
                    if (shuttingDown || generation != lifecycleGeneration.get() || !RealWorldSettings.enabled) {
                        return@submitOnEntity
                    }
                    RealWorldStorage.markPlayerDirty(player.uniqueId)
                }
            }
        }
    }

    private fun startAutoSaveTask() {
        val periodTicks = RealWorldSettings.autoSaveIntervalMinutes.coerceAtLeast(1) * 60L * 20L
        val generation = lifecycleGeneration.get()
        autoSaveTask = submitAsync(delay = periodTicks, period = periodTicks) {
            if (shuttingDown || generation != lifecycleGeneration.get() || !RealWorldSettings.enabled) {
                return@submitAsync
            }
            val state = synchronized(globalStateLock) {
                globalState?.copy()
            } ?: return@submitAsync
            if (shuttingDown || generation != lifecycleGeneration.get() || !RealWorldSettings.enabled) {
                return@submitAsync
            }
            RealWorldStorage.flushDirty(state)
        }
    }

    private fun stopTasks() {
        tickTask.cancelTaskSafely()
        autoSaveTask.cancelTaskSafely()
        tickTask = null
        autoSaveTask = null
    }

    private fun loadOnlinePlayersData() {
        val onlinePlayerIds = onlinePlayers().mapNotNull { proxy ->
            proxy.cast<Player>()?.uniqueId
        }
        if (onlinePlayerIds.isEmpty()) {
            return
        }

        val generation = lifecycleGeneration.get()
        submitAsync {
            if (shuttingDown || generation != lifecycleGeneration.get() || !RealWorldSettings.enabled) {
                return@submitAsync
            }
            onlinePlayerIds.forEach { uuid ->
                if (shuttingDown || generation != lifecycleGeneration.get() || !RealWorldSettings.enabled) {
                    return@submitAsync
                }
                if (pendingQuitAt.containsKey(uuid)) {
                    return@forEach
                }
                RealWorldStorage.loadPlayer(uuid)
                if (shuttingDown || generation != lifecycleGeneration.get() || !RealWorldSettings.enabled) {
                    return@submitAsync
                }
                if (pendingQuitAt.containsKey(uuid)) {
                    RealWorldStorage.removePlayerFromCache(uuid)
                    return@forEach
                }
            }
        }
    }

    private fun preloadPlayer(player: Player) {
        val uuid = player.uniqueId
        pendingQuitAt.remove(uuid)
        if (RealWorldStorage.getPlayerCache().containsKey(uuid)) {
            return
        }
        val generation = lifecycleGeneration.get()
        submitAsync {
            if (shuttingDown || generation != lifecycleGeneration.get() || !RealWorldSettings.enabled) {
                return@submitAsync
            }
            if (pendingQuitAt.containsKey(uuid)) {
                return@submitAsync
            }
            RealWorldStorage.loadPlayer(uuid)
            if (shuttingDown || generation != lifecycleGeneration.get() || !RealWorldSettings.enabled) {
                return@submitAsync
            }
            if (pendingQuitAt.containsKey(uuid)) {
                RealWorldStorage.removePlayerFromCache(uuid)
                return@submitAsync
            }
        }
    }

    private fun saveOnlinePlayers() {
        val onlinePlayerIds = onlinePlayers().mapNotNull { proxy ->
            proxy.cast<Player>()?.uniqueId
        }
        onlinePlayerIds.forEach { uuid ->
            RealWorldStorage.savePlayer(uuid)
        }
    }

    private fun clearOnlineHud() {
        val generation = lifecycleGeneration.get()
        onlinePlayers().forEach { proxy ->
            val player = proxy.cast<Player>() ?: return@forEach
            player.submitOnEntity {
                if (generation != lifecycleGeneration.get()) {
                    return@submitOnEntity
                }
                SurvivalHud.onPlayerQuit(player)
            }
        }
    }

    private fun runOnGlobalRegion(action: () -> Unit) {
        val generation = lifecycleGeneration.get()
        val wrapped = {
            if (!shuttingDown && generation == lifecycleGeneration.get() && RealWorldSettings.enabled) {
                action()
            }
        }
        val plugin = Bukkit.getPluginManager().getPlugin("phcore")
        if (plugin != null) {
            Bukkit.getGlobalRegionScheduler().run(plugin) { _ -> wrapped() }
        } else {
            submit { wrapped() }
        }
    }

    private fun syncVanillaWeather(state: GlobalEnvState) {
        val hasStorm = state.weather != WeatherType.CLEAR
        val hasThunder = state.weather == WeatherType.THUNDER
        Bukkit.getWorlds().forEach { world ->
            if (world.hasStorm() != hasStorm) {
                world.setStorm(hasStorm)
            }
            if (world.isThundering != hasThunder) {
                world.isThundering = hasThunder
            }
        }
    }

    fun resetPlayer(uuid: UUID) {
        if (!RealWorldSettings.enabled) {
            return
        }
        RealWorldStorage.resetPlayer(uuid)
    }

    private fun isWaterBottle(type: Material, meta: PotionMeta?): Boolean {
        return type == Material.POTION && meta?.basePotionType == PotionType.WATER
    }
}
