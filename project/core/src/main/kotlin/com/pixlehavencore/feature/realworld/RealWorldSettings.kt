package com.pixlehavencore.feature.realworld

import com.pixlehavencore.feature.realworld.fracture.FractureSettings
import com.pixlehavencore.feature.realworld.season.SeasonSettings
import com.pixlehavencore.feature.realworld.stamina.StaminaSettings
import com.pixlehavencore.feature.realworld.temperature.TemperatureSettings
import com.pixlehavencore.feature.realworld.thirst.ThirstSettings
import com.pixlehavencore.feature.realworld.weather.WeatherSettings
import org.bukkit.Material
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object RealWorldSettings {

    @Config("feature/realworld/realworld.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = false
        private set

    var timeControlEnabled: Boolean = false
        private set

    var tickIntervalSeconds: Int = 2
        private set

    private const val TIME_CONTROL_MULTIPLIER = 3.0

    var hudActionBarFormat: String = "&c{temp}°C  &b{hydration}/100  &9{wetness}%  {sheltered}  &f{weather}  &a{season}"
        private set
    var hudShelteredIndicator: String = "&a🏠"
        private set
    var hudUnshelteredIndicator: String = "&7☁"
        private set
    var hudRefreshIntervalSeconds: Int = 2
        private set
    var hudBossBarEnabled: Boolean = true
        private set
    var hudBossBarTitleHeat: String = "&c严重过热！寻找阴凉处！"
        private set
    var hudBossBarTitleCold: String = "&b严重过冷！寻找热源！"
        private set
    var hudBossBarTitleThirst: String = "&e严重脱水！寻找水源！"
        private set

    var autoSaveIntervalMinutes: Int = 5
        private set

    // 向后兼容：委托到 SeasonSettings
    val seasonDurationDays: Int get() = SeasonSettings.durationDays
    val seasonDurationTicks: Long get() = SeasonSettings.seasonDurationTicks
    val seasonTransitionProgress: Double get() = SeasonSettings.transitionProgress

    // 向后兼容：委托到 WeatherSettings
    val weatherDecisionIntervalSeconds: Int get() = WeatherSettings.decisionIntervalSeconds
    val weatherPersistenceChance: Double get() = WeatherSettings.persistenceChance
    val extremeWarningSeconds: Int get() = WeatherSettings.extremeWarningSeconds
    val extremeGracePeriodSeconds: Int get() = WeatherSettings.extremeGracePeriodSeconds
    val extremeDamageIntervalSeconds: Int get() = WeatherSettings.extremeDamageIntervalSeconds
    val extremeBaseDamageHearts: Double get() = WeatherSettings.extremeBaseDamageHearts
    val visibilityEffectDurationSeconds: Int get() = WeatherSettings.visibilityEffectDurationSeconds
    val fogBlindnessAmplifier: Int get() = WeatherSettings.fogBlindnessAmplifier
    val blizzardBlindnessAmplifier: Int get() = WeatherSettings.blizzardBlindnessAmplifier
    val sandstormBlindnessAmplifier: Int get() = WeatherSettings.sandstormBlindnessAmplifier
    val localWeatherEnabled: Boolean get() = WeatherSettings.localEnabled
    val localWeatherNoiseFrequency: Float get() = WeatherSettings.localNoiseFrequency
    val localWeatherChangeSpeed: Float get() = WeatherSettings.localChangeSpeed
    val localWeatherCacheEnabled: Boolean get() = WeatherSettings.localCacheEnabled
    val localWeatherCacheMaxSize: Int get() = WeatherSettings.localCacheMaxSize

    // 向后兼容：委托到 TemperatureSettings
    val comfortMin: Double get() = TemperatureSettings.comfortMin
    val comfortMax: Double get() = TemperatureSettings.comfortMax
    val altitudeThresholdY: Int get() = TemperatureSettings.altitudeThresholdY
    val altitudeDropPerBlock: Double get() = TemperatureSettings.altitudeDropPerBlock
    val heatSourceScanIntervalSeconds: Int get() = TemperatureSettings.heatSourceScanIntervalSeconds
    val maxChangePerTick: Double get() = TemperatureSettings.maxChangePerTick
    val shelterGlassCountsAsShelter: Boolean get() = TemperatureSettings.shelterGlassCountsAsShelter
    val shelterLeavesCountAsShelter: Boolean get() = TemperatureSettings.shelterLeavesCountAsShelter
    val shelterHorizontalRadius: Int get() = TemperatureSettings.shelterHorizontalRadius
    val armorBonusLeather: Double get() = TemperatureSettings.armorBonusLeather
    val armorBonusNetherite: Double get() = TemperatureSettings.armorBonusNetherite
    val severeHeatThreshold: Double get() = TemperatureSettings.severeHeatThreshold
    val heatThreshold: Double get() = TemperatureSettings.heatThreshold
    val coldMildThreshold: Double get() = TemperatureSettings.coldMildThreshold
    val coldThreshold: Double get() = TemperatureSettings.coldThreshold
    val severeColdThreshold: Double get() = TemperatureSettings.severeColdThreshold
    val frostOverlayColdIntensity: Int get() = TemperatureSettings.frostOverlayColdIntensity
    val frostOverlaySevereColdIntensity: Int get() = TemperatureSettings.frostOverlaySevereColdIntensity
    val heatOverlayHeatIntensity: Double get() = TemperatureSettings.heatOverlayHeatIntensity
    val heatOverlaySevereIntensity: Double get() = TemperatureSettings.heatOverlaySevereIntensity
    val wetnessRateSubmerge: Double get() = TemperatureSettings.wetnessRateSubmerge
    val wetnessRateRain: Double get() = TemperatureSettings.wetnessRateRain
    val wetnessDryRate: Double get() = TemperatureSettings.wetnessDryRate
    val temperatureBlocks: Map<Material, Double> get() = TemperatureSettings.temperatureBlocks

    // 向后兼容：委托到 ThirstSettings
    val baseThirstRatePerMinute: Double get() = ThirstSettings.baseThirstRatePerMinute
    val sprintMultiplier: Double get() = ThirstSettings.sprintMultiplier
    val submergeMultiplier: Double get() = ThirstSettings.submergeMultiplier
    val thirstAltitudeThresholdY: Int get() = ThirstSettings.altitudeThresholdY
    val thirstAltitudeMultiplier: Double get() = ThirstSettings.altitudeMultiplier
    val tempDeviationPercentPerDegree: Double get() = ThirstSettings.tempDeviationPercentPerDegree
    val thirstFull: Double get() = ThirstSettings.thirstFull
    val thirstThirsty: Double get() = ThirstSettings.thirstThirsty
    val thirstSevere: Double get() = ThirstSettings.thirstSevere
    val waterBottleRestore: Double get() = ThirstSettings.waterBottleRestore
    val waterSourceRestore: Double get() = ThirstSettings.waterSourceRestore
    val drinkerRestore: Double get() = ThirstSettings.drinkerRestore
    val rainRestorePerMinute: Double get() = ThirstSettings.rainRestorePerMinute
    val seaWaterRestore: Double get() = ThirstSettings.seaWaterRestore
    val drinkerCooldownSeconds: Int get() = ThirstSettings.drinkerCooldownSeconds
    val seaWaterNauseaChance: Double get() = ThirstSettings.seaWaterNauseaChance
    val riverNauseaChance: Double get() = ThirstSettings.riverNauseaChance

    // 向后兼容：委托到 FractureSettings
    val fractureEnabled: Boolean get() = FractureSettings.enabled
    val fractureMinFallDamage: Double get() = FractureSettings.minFallDamage
    val fractureDamageMultiplier: Double get() = FractureSettings.damageMultiplier
    val fractureRecoveryRate: Double get() = FractureSettings.recoveryRate
    val fractureBandageHealAmount: Double get() = FractureSettings.bandageHealAmount
    val fractureBandageMaterial: Material get() = FractureSettings.bandageMaterial
    val fractureCastMaterial: Material get() = FractureSettings.castMaterial
    val fractureMildThreshold: Double get() = FractureSettings.mildThreshold
    val fractureModerateThreshold: Double get() = FractureSettings.moderateThreshold
    val fractureSevereThreshold: Double get() = FractureSettings.severeThreshold

    fun init() {
        SeasonSettings.init()
        WeatherSettings.init()
        TemperatureSettings.init()
        ThirstSettings.init()
        FractureSettings.init()
        StaminaSettings.init()
        reload()
    }

    private fun adjustForTimeControl(baseValue: Int): Int {
        if (!timeControlEnabled) return baseValue
        return (baseValue * TIME_CONTROL_MULTIPLIER).toInt()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", false)
        timeControlEnabled = config.getBoolean("time-control.enabled", false)
        tickIntervalSeconds = config.getInt("tick-interval-seconds", 2).coerceAtLeast(1)

        hudActionBarFormat = config.getString("hud.actionbar-format") ?: hudActionBarFormat
        hudShelteredIndicator = config.getString("hud.sheltered-indicator") ?: hudShelteredIndicator
        hudUnshelteredIndicator = config.getString("hud.unsheltered-indicator") ?: hudUnshelteredIndicator
        hudRefreshIntervalSeconds = config.getInt("hud.refresh-interval-seconds", 2).coerceAtLeast(1)
        hudBossBarEnabled = config.getBoolean("hud.bossbar-enabled", true)
        hudBossBarTitleHeat = config.getString("hud.bossbar-title-heat") ?: hudBossBarTitleHeat
        hudBossBarTitleCold = config.getString("hud.bossbar-title-cold") ?: hudBossBarTitleCold
        hudBossBarTitleThirst = config.getString("hud.bossbar-title-thirst") ?: hudBossBarTitleThirst

        autoSaveIntervalMinutes = config.getInt("storage.auto-save-interval-minutes", 5).coerceAtLeast(1)

        // 重载子系统
        SeasonSettings.reload()
        WeatherSettings.reload()
        TemperatureSettings.reload()
        ThirstSettings.reload()
        FractureSettings.reload()
        StaminaSettings.reload()

        // 天气决策间隔需要应用时间控制倍率
        // 这个逻辑在 WeatherSettings 中无法访问 timeControlEnabled，所以在这里处理
    }
}
