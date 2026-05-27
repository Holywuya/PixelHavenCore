package com.pixlehavencore.feature.realworld

import com.pixlehavencore.feature.realworld.foodcorrosion.FoodCorrosionEngine
import com.pixlehavencore.feature.realworld.temperature.TemperatureEngine
import com.pixlehavencore.feature.realworld.weather.WeatherEngine
import com.pixlehavencore.feature.realworld.foodcorrosion.FoodCorrosionService
import com.pixlehavencore.feature.realworld.foodcorrosion.FoodCorrosionSettings
import com.pixlehavencore.util.cancelTaskSafely
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.block.BlockBreakEvent
import taboolib.common.platform.event.EventPriority
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.potion.PotionType
import taboolib.platform.util.PlayerSessionMap
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
import org.bukkit.GameRules

object RealWorldService {

    @Volatile
    private var shuttingDown = false

    @Volatile
    private var globalState: GlobalEnvState? = null

    private val globalStateLock = Any()
    private val lifecycleGeneration = AtomicLong(0L)
    private val pendingQuitAt = ConcurrentHashMap<UUID, Long>()
    private val drinkerCooldownUntil = PlayerSessionMap<Long>({ 0L })

    private var tickTask: Any? = null
    private var autoSaveTask: Any? = null
    private var timeAdvanceTask: Any? = null

    fun init() {
        RealWorldSettings.init()
        StaminaEngine.init()
        stop()
        if (!RealWorldSettings.enabled) {
            return
        }

        lifecycleGeneration.incrementAndGet()
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
        FoodCorrosionSettings.init()
        FoodCorrosionService.init()
        WeatherEngine.init(Bukkit.getWorlds().first().seed.toInt())
        initTimeControl()
        info("[RealWorld] 模块已启动，在线玩家 ${onlinePlayers().size} 人。")
    }

    fun reload() {
        RealWorldSettings.reload()
        StaminaEngine.reload()
        stopInternal()
        if (!RealWorldSettings.enabled) {
            return
        }

        lifecycleGeneration.incrementAndGet()
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
        FoodCorrosionSettings.reload()
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
        lifecycleGeneration.incrementAndGet()
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
        val generation = lifecycleGeneration.get()

        timeAdvanceTask = submit(delay = 3, period = 3) {
            if (shuttingDown || generation != lifecycleGeneration.get() || !RealWorldSettings.enabled) {
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

    @SubscribeEvent
    fun onStaminaConsume(event: PlayerItemConsumeEvent) {
        if (!StaminaSettings.enabled) return
        val player = event.player
        val item = event.item
        val foodValues = getFoodValues(item.type)
        if (foodValues > 0) {
            RealWorldStorage.withPlayerState(player.uniqueId) { state ->
                StaminaEngine.onEat(player, state, foodValues)
            }
        }
        RealWorldStorage.withPlayerState(player.uniqueId) { state ->
            StaminaEngine.onSpecialItem(player, state, item.type)
        }
    }

    @SubscribeEvent
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        if (!StaminaSettings.enabled) return
        val player = event.damager as? Player ?: return
        RealWorldStorage.withPlayerState(player.uniqueId) { state ->
            StaminaEngine.onAttack(player, state)
        }
    }

    @SubscribeEvent
    fun onBlockBreak(event: BlockBreakEvent) {
        if (!StaminaSettings.enabled) return
        val player = event.player
        RealWorldStorage.withPlayerState(player.uniqueId) { state ->
            StaminaEngine.onMine(player, state)
        }
    }

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
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

        val player = event.player
        val uuid = player.uniqueId
        val generation = lifecycleGeneration.get()

        val heldItem = event.item
        if (heldItem != null && !heldItem.type.isAir) {
            val treatment = when (heldItem.type) {
                RealWorldSettings.fractureBandageMaterial -> FractureTreatment.BANDAGE
                RealWorldSettings.fractureCastMaterial -> FractureTreatment.CAST
                else -> null
            }
            if (treatment != null) {
                player.submitOnEntity {
                    if (shuttingDown || generation != lifecycleGeneration.get() || !RealWorldSettings.enabled) {
                        return@submitOnEntity
                    }
                    val changed = RealWorldStorage.withPlayerState(uuid) { state ->
                        FractureEngine.useTreatment(player, state, treatment)
                    } ?: return@submitOnEntity
                    if (!changed) {
                        return@submitOnEntity
                    }
                    heldItem.amount = heldItem.amount - 1
                    RealWorldStorage.markPlayerDirty(uuid)
                }
                return
            }
        }

        val block = event.clickedBlock ?: return
        if (!ThirstEngine.isDrinker(block) && !ThirstEngine.isNaturalWaterSource(block)) {
            return
        }

        player.submitOnEntity {
            if (shuttingDown || generation != lifecycleGeneration.get() || !RealWorldSettings.enabled) {
                return@submitOnEntity
            }
            val changed = RealWorldStorage.withPlayerState(uuid) { state ->
                when {
                    ThirstEngine.isDrinker(block) -> handleDrinkerInteract(uuid, state, block)
                    else -> ThirstEngine.onRightClickNaturalWaterSource(player, state, block)
                }
            } ?: return@submitOnEntity
            if (!changed) {
                return@submitOnEntity
            }
            RealWorldStorage.markPlayerDirty(uuid)
        }
    }

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageEvent) {
        if (!RealWorldSettings.enabled || !RealWorldSettings.fractureEnabled) {
            return
        }
        val player = event.entity as? Player ?: return
        val uuid = player.uniqueId
        val generation = lifecycleGeneration.get()
        player.submitOnEntity {
            if (shuttingDown || generation != lifecycleGeneration.get() || !RealWorldSettings.enabled) {
                return@submitOnEntity
            }
            RealWorldStorage.withPlayerState(uuid) { state ->
                FractureEngine.onFallDamage(player, state, event)
            } ?: return@submitOnEntity
            if (shuttingDown || generation != lifecycleGeneration.get() || !RealWorldSettings.enabled) {
                return@submitOnEntity
            }
            RealWorldStorage.markPlayerDirty(uuid)
        }
    }

    private fun getFoodValues(material: Material): Int {
        return when (material) {
            Material.BREAD -> 5
            Material.COOKED_BEEF, Material.COOKED_PORKCHOP, Material.COOKED_MUTTON -> 8
            Material.COOKED_CHICKEN, Material.COOKED_COD, Material.COOKED_SALMON -> 6
            Material.BAKED_POTATO -> 5
            Material.MUSHROOM_STEW, Material.RABBIT_STEW, Material.BEETROOT_SOUP -> 7
            Material.GOLDEN_APPLE -> 4
            Material.ENCHANTED_GOLDEN_APPLE -> 4
            Material.COOKED_RABBIT -> 5
            Material.APPLE, Material.BEETROOT, Material.CARROT, Material.POTATO, Material.SWEET_BERRIES, Material.GLOW_BERRIES -> 3
            Material.MELON_SLICE, Material.CHORUS_FRUIT -> 2
            Material.COOKIE -> 2
            Material.DRIED_KELP -> 1
            else -> 0
        }
    }

    private fun handleDrinkerInteract(uuid: UUID, state: PlayerEnvState, block: org.bukkit.block.Block): Boolean {
        if (!ThirstEngine.isDrinker(block)) {
            return false
        }

        val now = System.currentTimeMillis()
        val cooldownUntil = drinkerCooldownUntil[uuid] ?: 0L
        if (now < cooldownUntil) {
            return false
        }

        val changed = ThirstEngine.onRightClickDrinker(state, block)
        if (!changed) {
            return false
        }

        val cooldownMillis = RealWorldSettings.drinkerCooldownSeconds.coerceAtLeast(0) * 1000L
        if (cooldownMillis > 0L) {
            drinkerCooldownUntil[uuid] = now + cooldownMillis
        }
        return true
    }

    private fun startTickTask() {
        val periodTicks = RealWorldSettings.tickIntervalSeconds.coerceAtLeast(1) * 20L
        val generation = lifecycleGeneration.get()
        tickTask = submit(delay = periodTicks, period = periodTicks) {
            if (shuttingDown || generation != lifecycleGeneration.get() || !RealWorldSettings.enabled) {
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
                    if (shuttingDown || generation != lifecycleGeneration.get() || !RealWorldSettings.enabled) {
                        return@submitOnEntity
                    }
                    RealWorldStorage.withPlayerState(player.uniqueId) { playerState ->
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
