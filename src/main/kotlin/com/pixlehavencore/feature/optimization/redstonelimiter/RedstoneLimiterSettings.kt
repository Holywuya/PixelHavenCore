package com.pixlehavencore.feature.optimization.redstonelimiter

import org.bukkit.Material
import taboolib.common.platform.function.warning
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object RedstoneLimiterSettings {

    @Config("feature/optimization/redstone-limiter.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set
    var enabledWorlds: Set<String> = emptySet()
        private set
    var thresholdActivationsPerSecond: Int = 20
        private set
    var windowSeconds: Int = 5
        private set
    var maxTrackedPoints: Int = 1500
        private set
    var cleanupIntervalSeconds: Int = 60
        private set
    var notifyEnabled: Boolean = true
        private set
    var notifyCooldownSeconds: Int = 10
        private set
    var notifyMessage: String = "&c[红石限制] &7检测到高频红石: &f{block} &7@ &e{world} {x},{y},{z} &7频率: &c{frequency}/s"
        private set
    var additionalBlockTypes: Set<Material> = emptySet()
        private set

    // 默认红石方块类型 + additionalBlockTypes 扩展后的完整集合
    var redstoneBlockTypes: Set<Material> = emptySet()
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        enabledWorlds = config.getStringList("enabled-worlds").toSet()
        thresholdActivationsPerSecond = loadPositiveInt("threshold-activations-per-second", 20)
        windowSeconds = loadPositiveInt("window-seconds", 5)
        maxTrackedPoints = loadPositiveInt("max-tracked-points", 1500)
        cleanupIntervalSeconds = loadPositiveInt("cleanup-interval-seconds", 60)
        notifyEnabled = config.getBoolean("notify.enabled", true)
        notifyCooldownSeconds = loadPositiveInt("notify.cooldown-seconds", 10)
        notifyMessage = config.getString("notify.message") ?: notifyMessage
        additionalBlockTypes = loadAdditionalBlockTypes()
        redstoneBlockTypes = DEFAULT_REDSTONE_MATERIALS + additionalBlockTypes
    }

    private fun loadPositiveInt(path: String, default: Int): Int {
        val value = config.getInt(path, default)
        if (value <= 0) {
            warning("[RedstoneLimiter] 配置项 $path 值非法($value)，回退默认值 $default")
            return default
        }
        return value
    }

    private fun loadAdditionalBlockTypes(): Set<Material> {
        val names = config.getStringList("additional-block-types")
        val result = mutableSetOf<Material>()
        for (name in names) {
            val material = Material.getMaterial(name.uppercase())
            if (material != null) {
                result.add(material)
            } else {
                warning("[RedstoneLimiter] 配置项 additional-block-types 中无效的 Material: $name，已跳过")
            }
        }
        return result
    }

    // 硬编码默认红石方块类型集合
    private val DEFAULT_REDSTONE_MATERIALS: Set<Material> = setOf(
        Material.REDSTONE_WIRE,
        Material.REPEATER,
        Material.COMPARATOR,
        Material.REDSTONE_TORCH,
        Material.REDSTONE_WALL_TORCH,
        Material.TRIPWIRE_HOOK,
        Material.TRIPWIRE,
        Material.DAYLIGHT_DETECTOR,
        Material.LEVER,
        Material.OBSERVER,
        Material.PISTON,
        Material.STICKY_PISTON,
        Material.STONE_BUTTON,
        Material.OAK_BUTTON,
        Material.SPRUCE_BUTTON,
        Material.BIRCH_BUTTON,
        Material.JUNGLE_BUTTON,
        Material.ACACIA_BUTTON,
        Material.DARK_OAK_BUTTON,
        Material.MANGROVE_BUTTON,
        Material.CHERRY_BUTTON,
        Material.BAMBOO_BUTTON,
        Material.POLISHED_BLACKSTONE_BUTTON,
        Material.CRIMSON_BUTTON,
        Material.WARPED_BUTTON,
        Material.STONE_PRESSURE_PLATE,
        Material.OAK_PRESSURE_PLATE,
        Material.SPRUCE_PRESSURE_PLATE,
        Material.BIRCH_PRESSURE_PLATE,
        Material.JUNGLE_PRESSURE_PLATE,
        Material.ACACIA_PRESSURE_PLATE,
        Material.DARK_OAK_PRESSURE_PLATE,
        Material.MANGROVE_PRESSURE_PLATE,
        Material.CHERRY_PRESSURE_PLATE,
        Material.BAMBOO_PRESSURE_PLATE,
        Material.POLISHED_BLACKSTONE_PRESSURE_PLATE,
        Material.CRIMSON_PRESSURE_PLATE,
        Material.WARPED_PRESSURE_PLATE,
        Material.LIGHT_WEIGHTED_PRESSURE_PLATE,
        Material.HEAVY_WEIGHTED_PRESSURE_PLATE,
        Material.SCULK_SENSOR,
        Material.CALIBRATED_SCULK_SENSOR,
    )
}
