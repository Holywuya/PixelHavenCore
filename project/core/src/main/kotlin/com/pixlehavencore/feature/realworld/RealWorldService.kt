package com.pixlehavencore.feature.realworld

import com.pixlehavencore.feature.realworld.foodcorrosion.FoodCorrosionEngine
import com.pixlehavencore.feature.realworld.foodcorrosion.FoodCorrosionService
import com.pixlehavencore.feature.realworld.fracture.FractureEngine
import com.pixlehavencore.feature.realworld.season.SeasonEngine
import com.pixlehavencore.feature.realworld.stamina.StaminaEngine
import com.pixlehavencore.feature.realworld.temperature.TemperatureEngine
import com.pixlehavencore.feature.realworld.thirst.ThirstEngine
import com.pixlehavencore.feature.realworld.weather.WeatherEngine
import com.pixlehavencore.util.cancelTaskSafely
import org.bukkit.Bukkit
import org.bukkit.GameRules
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.WorldLoadEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.info
import taboolib.common.platform.function.onlinePlayers
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.platform.util.PlayerSessionMap
import taboolib.platform.util.submit as submitOnEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object RealWorldService {

    @Volatile
    private var shuttingDown = false

    @Volatile
    private var globalState: GlobalEnvState? = null

    private val globalStateLock = Any()
    private val _lifecycleGeneration = AtomicLong(0L)
    internal val lifecycleGeneration: Long get() = _lifecycleGeneration.get()
    private val pendingQuitAt = ConcurrentHashMap<UUID, Long>()
    internal val drinkerCooldownUntil = PlayerSessionMap<Long>({ 0L })

    private var tickTask: Any? = null
    private var autoSaveTask: Any? = null
    private var timeAdvanceTask: Any? = null

    fun init() {
        RealWorldSettings.init()
        stop()
        if (!RealWorldSettings.enabled) {
            return
        }

        _lifecycleGeneration.incrementAndGet()
        shuttingDown = false
        pendingQuitAt.clear()
        drinkerCooldownUntil.clear()
        RealWorldStorage.init()
        synchronized(globalStateLock) {
            globalState = RealWorldStorage.loadGlobal()
        }
        startTickTask()
        startAutoSaveTask()
        loadOnlinePlayersData()
        FoodCorrosionService.init()
        WeatherEngine.init(Bukkit.getWorlds().first().seed.toInt())
        initTimeControl()
        info("[RealWorld] 模块已启动，在线玩家 ${onlinePlayers().size} 人。")
    }

    fun reload() {
        RealWorldSettings.reload()
        stopInternal()
        if (!RealWorldSettings.enabled) {
            return
        }

        _lifecycleGeneration.incrementAndGet()
        shuttingDown = false
        pendingQuitAt.clear()
        drinkerCooldownUntil.clear()
        RealWorldStorage.reload()
        synchronized(globalStateLock) {
            globalState = RealWorldStorage.loadGlobal()
        }
        startTickTask()
        startAutoSaveTask()
        loadOnlinePlayersData()
        FoodCorrosionService.reload()
        WeatherEngine.init(Bukkit.getWorlds().first().seed.toInt())
        initTimeControl()
        info("[RealWorld] 模块已重载，在线玩家 ${onlinePlayers().size} 人。")
    }

    fun stop() {
        stopInternal()
    }

    private fun stopInternal() {
        shuttingDown = true
        _lifecycleGeneration.incrementAndGet()
        pendingQuitAt.clear()
        drinkerCooldownUntil.clear()
        stopTasks()
        stopTimeControl()
        saveOnlinePlayers()
        val globalSnapshot = synchronized(globalStateLock) {
            globalState?.copy()
        }
        globalSnapshot?.let { state ->
            RealWorldStorage.saveGlobal(state)
        }
        clearOnlineHud()
        FoodCorrosionService.stop()
        RealWorldStorage.stop()
        synchronized(globalStateLock) {
            globalState = null
        }
    }

    internal fun isActive(generation: Long): Boolean {
        return !shuttingDown && generation == _lifecycleGeneration.get() && RealWorldSettings.enabled
    }

    internal fun getGlobalStateSnapshot(): GlobalEnvState? {
        if (!RealWorldSettings.enabled) {
            return null
        }
        return synchronized(globalStateLock) {
            globalState?.copy()
        }
    }

    private fun initTimeControl() {
        if (!RealWorldSettings.timeControlEnabled) {
            return
        }

        Bukkit.getWorlds().forEach { world ->
            world.setGameRule(GameRules.ADVANCE_TIME, false)
        }

        startTimeAdvanceTask()

        info("[RealWorld] 时间控制已启用：现实 1 小时 = 游戏 1 天")
    }

    private fun stopTimeControl() {
        if (!RealWorldSettings.timeControlEnabled) {
            return
        }

        stopTimeAdvanceTask()

        Bukkit.getWorlds().forEach { world ->
            world.setGameRule(GameRules.ADVANCE_TIME, true)
        }

        info("[RealWorld] 时间控制已禁用，恢复原版时间流速")
    }

    private fun startTimeAdvanceTask() {
        val generation = _lifecycleGeneration.get()

        timeAdvanceTask = submit(delay = 3, period = 3) {
            if (shuttingDown || generation != _lifecycleGeneration.get() || !RealWorldSettings.enabled) {
                return@submit
            }

            Bukkit.getWorlds().forEach { world ->
                world.time += 1
            }
        }
    }

    private fun stopTimeAdvanceTask() {
        timeAdvanceTask.cancelTaskSafely()
        timeAdvanceTask = null
    }

    /**
     * 返回当前天气类型，只暴露不可变枚举值，不向外暴露内部全局状态对象。
     */
    fun getCurrentWeatherType(): WeatherType? {
        if (!RealWorldSettings.enabled) {
            return null
        }
        return synchronized(globalStateLock) {
            globalState?.weather
        }
    }

    /**
     * 返回当前是否处于极端天气；模块未启用或状态未就绪时返回 null。
     */
    fun isExtremeWeatherActive(): Boolean? {
        return getCurrentWeatherType()?.isExtreme
    }

    /**
     * 返回当前季节，只暴露不可变枚举值，不向外暴露内部全局状态对象。
     */
    fun getSeason(): Season? {
        if (!RealWorldSettings.enabled) {
            return null
        }
        return synchronized(globalStateLock) {
            globalState?.season
        }
    }

    /**
     * 返回最近一次实体线程更新后的天气遮蔽缓存结果。
     *
     * 这里不直接读取玩家当前位置或方块状态，而是读取 `PlayerEnvState` 快照，
     * 避免外部模块在错误线程上访问 Bukkit/Folia 实体与区块数据。
     */
    fun isPlayerWeatherSheltered(player: Player): Boolean? {
        if (!RealWorldSettings.enabled) {
            return null
        }
        return RealWorldStorage.getPlayerSnapshot(player.uniqueId)?.isWeatherSheltered
    }

    fun forceSeason(season: Season) {
        if (!RealWorldSettings.enabled) {
            return
        }
        synchronized(globalStateLock) {
            val state = globalState ?: return
            val previousSeason = state.season
            state.season = season
            state.seasonProgress = 0.0
            if (previousSeason != season) {
                Bukkit.getPluginManager().callEvent(
                    RealWorldSeasonChangedEvent(
                        previousSeason = previousSeason,
                        season = season,
                        seasonProgress = 0.0,
                    ),
                )
            }
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
    fun onWorldLoad(event: WorldLoadEvent) {
        if (!RealWorldSettings.enabled || !RealWorldSettings.timeControlEnabled) {
            return
        }
        event.world.setGameRule(GameRules.ADVANCE_TIME, false)
        info("[RealWorld] 世界 ${event.world.name} 已禁用自动时间流逝")
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
        val generation = _lifecycleGeneration.get()
        val quitMark = System.currentTimeMillis()
        pendingQuitAt[uuid] = quitMark
        SurvivalHud.onPlayerQuit(player)
        submitAsync {
            if (shuttingDown || generation != _lifecycleGeneration.get() || !RealWorldSettings.enabled) {
                return@submitAsync
            }
            RealWorldStorage.savePlayer(uuid)
            if (shuttingDown || generation != _lifecycleGeneration.get() || !RealWorldSettings.enabled) {
                return@submitAsync
            }
            if (pendingQuitAt[uuid] != quitMark) {
                return@submitAsync
            }
            RealWorldStorage.removePlayerFromCache(uuid)
            pendingQuitAt.remove(uuid, quitMark)
        }
    }

    private fun startTickTask() {
        val periodTicks = RealWorldSettings.tickIntervalSeconds.coerceAtLeast(1) * 20L
        val generation = _lifecycleGeneration.get()
        tickTask = submit(delay = periodTicks, period = periodTicks) {
            if (shuttingDown || generation != _lifecycleGeneration.get() || !RealWorldSettings.enabled) {
                return@submit
            }

            val tickSeconds = RealWorldSettings.tickIntervalSeconds
            val onlinePlayerList = onlinePlayers().mapNotNull { it.cast<Player>() }
            val globalSnapshot = synchronized(globalStateLock) {
                val state = globalState ?: return@submit
                SeasonEngine.tick(state, tickSeconds)
                WeatherEngine.tick(state, tickSeconds, onlinePlayerList)
                state.dayPhase = SeasonEngine.computeDayPhase(Bukkit.getWorlds().firstOrNull()?.time ?: 6000L)
                syncVanillaWeather(state)
                RealWorldStorage.markGlobalDirty(state)
                state.copy()
            }
            onlinePlayers().forEach { proxy ->
                val player = proxy.cast<Player>() ?: return@forEach
                player.submitOnEntity {
                    if (shuttingDown || generation != _lifecycleGeneration.get() || !RealWorldSettings.enabled) {
                        return@submitOnEntity
                    }
                    val shouldMarkDirty = RealWorldStorage.withPlayerState(player.uniqueId) { playerState ->
                        val previousTemperature = playerState.temperature
                        val previousHydration = playerState.hydration
                        val previousFracture = playerState.fracture
                        val previousStamina = playerState.stamina

                        TemperatureEngine.compute(player, playerState, globalSnapshot, tickSeconds)
                        ThirstEngine.compute(player, playerState, globalSnapshot, tickSeconds)
                        FractureEngine.applyEffects(player, playerState, tickSeconds)
                        FoodCorrosionEngine.tickPlayer(player)
                        SurvivalEffectApplier.apply(player, playerState, globalSnapshot, tickSeconds)
                        StaminaEngine.checkIdle(player, playerState, tickSeconds.toDouble())
                        StaminaEngine.tick(player, playerState, globalSnapshot, tickSeconds)
                        playerState.hudRefreshTimer -= tickSeconds.coerceAtLeast(0).toDouble()
                        if (playerState.hudRefreshTimer <= 0.0) {
                            SurvivalHud.renderCurrentThread(player, playerState, globalSnapshot)
                            val refreshInterval = RealWorldSettings.hudRefreshIntervalSeconds.toDouble()
                            while (playerState.hudRefreshTimer <= 0.0) {
                                playerState.hudRefreshTimer += refreshInterval
                            }
                        }
                        hasPersistedPlayerStateChanged(
                            playerState = playerState,
                            previousTemperature = previousTemperature,
                            previousHydration = previousHydration,
                            previousFracture = previousFracture,
                            previousStamina = previousStamina,
                        )
                    } ?: return@submitOnEntity
                    if (shuttingDown || generation != _lifecycleGeneration.get() || !RealWorldSettings.enabled) {
                        return@submitOnEntity
                    }
                    if (shouldMarkDirty) {
                        RealWorldStorage.markPlayerDirty(player.uniqueId)
                    }
                }
            }
        }
    }

    private fun hasPersistedPlayerStateChanged(
        playerState: PlayerEnvState,
        previousTemperature: Double,
        previousHydration: Double,
        previousFracture: Double,
        previousStamina: Double,
    ): Boolean {
        return playerState.temperature != previousTemperature ||
            playerState.hydration != previousHydration ||
            playerState.fracture != previousFracture ||
            playerState.stamina != previousStamina
    }

    private fun startAutoSaveTask() {
        val periodTicks = RealWorldSettings.autoSaveIntervalMinutes.coerceAtLeast(1) * 60L * 20L
        val generation = _lifecycleGeneration.get()
        autoSaveTask = submitAsync(delay = periodTicks, period = periodTicks) {
            if (shuttingDown || generation != _lifecycleGeneration.get() || !RealWorldSettings.enabled) {
                return@submitAsync
            }
            val state = synchronized(globalStateLock) {
                globalState?.copy()
            } ?: return@submitAsync
            if (shuttingDown || generation != _lifecycleGeneration.get() || !RealWorldSettings.enabled) {
                return@submitAsync
            }
            RealWorldStorage.flushDirty(state)
        }
    }

    private fun stopTasks() {
        tickTask.cancelTaskSafely()
        autoSaveTask.cancelTaskSafely()
        timeAdvanceTask.cancelTaskSafely()
        tickTask = null
        autoSaveTask = null
        timeAdvanceTask = null
    }

    private fun loadOnlinePlayersData() {
        val onlinePlayerIds = onlinePlayers().mapNotNull { proxy ->
            proxy.cast<Player>()?.uniqueId
        }
        if (onlinePlayerIds.isEmpty()) {
            return
        }

        val generation = _lifecycleGeneration.get()
        submitAsync {
            if (shuttingDown || generation != _lifecycleGeneration.get() || !RealWorldSettings.enabled) {
                return@submitAsync
            }
            onlinePlayerIds.forEach { uuid ->
                if (shuttingDown || generation != _lifecycleGeneration.get() || !RealWorldSettings.enabled) {
                    return@submitAsync
                }
                if (pendingQuitAt.containsKey(uuid)) {
                    return@forEach
                }
                RealWorldStorage.loadPlayer(uuid)
                if (shuttingDown || generation != _lifecycleGeneration.get() || !RealWorldSettings.enabled) {
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
        val generation = _lifecycleGeneration.get()
        submitAsync {
            if (shuttingDown || generation != _lifecycleGeneration.get() || !RealWorldSettings.enabled) {
                return@submitAsync
            }
            if (pendingQuitAt.containsKey(uuid)) {
                return@submitAsync
            }
            RealWorldStorage.loadPlayer(uuid)
            if (shuttingDown || generation != _lifecycleGeneration.get() || !RealWorldSettings.enabled) {
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
        val generation = _lifecycleGeneration.get()
        onlinePlayers().forEach { proxy ->
            val player = proxy.cast<Player>() ?: return@forEach
            player.submitOnEntity {
                if (generation != _lifecycleGeneration.get()) {
                    return@submitOnEntity
                }
                SurvivalHud.onPlayerQuit(player)
            }
        }
    }

    private fun syncVanillaWeather(state: GlobalEnvState) {
        val hasStorm = state.weather != WeatherType.CLEAR && state.weather != WeatherType.FOG
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
}
