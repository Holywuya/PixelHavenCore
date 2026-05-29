package com.pixlehavencore.feature.realworld

import com.pixlehavencore.feature.realworld.foodcorrosion.FoodCorrosionSettings
import com.pixlehavencore.feature.realworld.fracture.FractureSettings
import com.pixlehavencore.feature.realworld.season.SeasonSettings
import com.pixlehavencore.feature.realworld.stamina.StaminaSettings
import com.pixlehavencore.feature.realworld.temperature.TemperatureSettings
import com.pixlehavencore.feature.realworld.thirst.ThirstSettings
import com.pixlehavencore.feature.realworld.weather.WeatherSettings
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object RealWorldSettings {

    @Config("feature/realworld/realworld.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = false
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

    fun init() {
        SeasonSettings.init()
        WeatherSettings.init()
        TemperatureSettings.init()
        ThirstSettings.init()
        FractureSettings.init()
        FoodCorrosionSettings.init()
        StaminaSettings.init()
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", false)
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

        SeasonSettings.reload()
        WeatherSettings.reload()
        TemperatureSettings.reload()
        ThirstSettings.reload()
        FractureSettings.reload()
        FoodCorrosionSettings.reload()
        StaminaSettings.reload()
    }
}
