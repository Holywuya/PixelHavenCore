package com.pixlehavencore.feature.realworld

import com.pixlehavencore.feature.realworld.fracture.FractureEngine
import com.pixlehavencore.feature.realworld.fracture.FractureSeverity
import com.pixlehavencore.feature.realworld.stamina.StaminaEngine
import com.pixlehavencore.feature.realworld.stamina.StaminaPhase
import com.pixlehavencore.feature.realworld.stamina.StaminaSettings
import com.pixlehavencore.feature.realworld.weather.WeatherQuery
import com.pixlehavencore.feature.realworld.weather.WeatherSettings
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

object SurvivalEffectApplier {

    fun apply(player: Player, state: PlayerEnvState, global: GlobalEnvState, tickIntervalSeconds: Int) {
        applyExtremeNeedsEffects(player, state, tickIntervalSeconds)

        // 查询一次 WeatherState，供可见性和暴露效果共享，避免重复 WeatherQuery 调用
        val localWeatherState = if (WeatherSettings.localEnabled) {
            WeatherQuery.getWeatherAt(player.location, global)
        } else {
            null
        }

        applyVisibilityWeatherEffects(player, state, global, localWeatherState)
        applyWeatherExposureEffects(player, state, global, tickIntervalSeconds, localWeatherState)

        // walkSpeed / sprint 取骨折与体力中最严格的值，避免后者覆盖前者
        val fractureSeverity = FractureEngine.classifyFracture(state.fracture)
        val fractureSpeed = computeFractureWalkSpeed(fractureSeverity)
        val fractureBlockSprint = fractureSeverity >= FractureSeverity.MODERATE
        val staminaSpeed = computeStaminaWalkSpeed(state)
        val staminaBlockSprint = state.staminaPhase == StaminaPhase.EXHAUSTED ||
            state.staminaPhase == StaminaPhase.DEPLETED
        val targetSpeed = minOf(fractureSpeed, staminaSpeed)
        val blockSprint = fractureBlockSprint || staminaBlockSprint

        if (player.walkSpeed != targetSpeed) player.walkSpeed = targetSpeed
        if (blockSprint) player.isSprinting = false

        applyStaminaEffects(player, state, tickIntervalSeconds)
    }

    private fun computeFractureWalkSpeed(severity: FractureSeverity): Float {
        return when (severity) {
            FractureSeverity.NONE -> 0.2f
            FractureSeverity.MILD -> 0.2f * 0.8f
            FractureSeverity.MODERATE -> 0.2f * 0.5f
            FractureSeverity.SEVERE -> 0.2f * 0.2f
        }
    }

    private fun computeStaminaWalkSpeed(state: PlayerEnvState): Float {
        return when (state.staminaPhase) {
            StaminaPhase.FULL -> 0.2f
            StaminaPhase.TIRED -> 0.2f * StaminaSettings.tiredSpeedMultiplier.toFloat()
            StaminaPhase.EXHAUSTED -> 0.2f * StaminaSettings.exhaustedSpeedMultiplier.toFloat()
            StaminaPhase.DEPLETED -> 0.2f * StaminaSettings.depletedSpeedMultiplier.toFloat()
        }
    }

    private fun applyExtremeNeedsEffects(player: Player, state: PlayerEnvState, tickIntervalSeconds: Int) {
        val severe = isInSevereState(state)
        if (!severe) {
            state.graceTimer = 0.0
            state.damageTimer = 0.0
            clearDamagingEffects(player)
            applyTemperatureEffects(player, state, tickIntervalSeconds, true)
            applyThirstEffects(player, state, tickIntervalSeconds, true)
            return
        }

        if (state.graceTimer <= 0.0 && state.damageTimer <= 0.0) {
            state.graceTimer = WeatherSettings.extremeGracePeriodSeconds.toDouble()
            state.damageTimer = WeatherSettings.extremeDamageIntervalSeconds.toDouble()
            clearDamagingEffects(player)
            applyTemperatureEffects(player, state, tickIntervalSeconds, false)
            applyThirstEffects(player, state, tickIntervalSeconds, false)
            return
        }

        val elapsedSeconds = tickIntervalSeconds.coerceAtLeast(0).toDouble()
        if (state.graceTimer > 0.0) {
            state.graceTimer = (state.graceTimer - elapsedSeconds).coerceAtLeast(0.0)
            if (state.graceTimer > 0.0) {
                clearDamagingEffects(player)
                applyTemperatureEffects(player, state, tickIntervalSeconds, false)
                applyThirstEffects(player, state, tickIntervalSeconds, false)
                return
            }

            applyTemperatureEffects(player, state, tickIntervalSeconds, true)
            applyThirstEffects(player, state, tickIntervalSeconds, true)
            return
        }

        applyTemperatureEffects(player, state, tickIntervalSeconds, true)
        applyThirstEffects(player, state, tickIntervalSeconds, true)

        state.damageTimer -= elapsedSeconds
        if (state.damageTimer > 0.0) {
            return
        }

        player.damage(WeatherSettings.extremeBaseDamageHearts * 2.0)
        state.damageTimer = WeatherSettings.extremeDamageIntervalSeconds.toDouble()
    }

    private fun applyTemperatureEffects(
        player: Player,
        state: PlayerEnvState,
        tickIntervalSeconds: Int,
        allowDamagingEffects: Boolean,
    ) {
        when (state.temperaturePhase) {
            TemperaturePhase.SEVERE_HEAT -> {
                if (allowDamagingEffects) {
                    addEffect(player, PotionEffectType.WITHER, 0, tickIntervalSeconds)
                }
                addEffect(player, PotionEffectType.BLINDNESS, 0, tickIntervalSeconds)
            }
            TemperaturePhase.HEAT -> {
                addEffect(player, PotionEffectType.SLOWNESS, 0, tickIntervalSeconds)
                addEffect(player, PotionEffectType.HUNGER, 1, tickIntervalSeconds)
            }
            TemperaturePhase.COMFORTABLE -> Unit
            TemperaturePhase.COLD_MILD -> {
                addEffect(player, PotionEffectType.HUNGER, 0, tickIntervalSeconds)
            }
            TemperaturePhase.COLD -> {
                addEffect(player, PotionEffectType.SLOWNESS, 0, tickIntervalSeconds)
                addEffect(player, PotionEffectType.MINING_FATIGUE, 0, tickIntervalSeconds)
            }
            TemperaturePhase.SEVERE_COLD -> {
                addEffect(player, PotionEffectType.SLOWNESS, 1, tickIntervalSeconds)
                if (allowDamagingEffects) {
                    addEffect(player, PotionEffectType.WITHER, 0, tickIntervalSeconds)
                }
            }
        }
    }

    private fun applyThirstEffects(
        player: Player,
        state: PlayerEnvState,
        tickIntervalSeconds: Int,
        allowDamagingEffects: Boolean,
    ) {
        when (state.thirstPhase) {
            ThirstPhase.FULL -> Unit
            ThirstPhase.THIRSTY -> {
                addEffect(player, PotionEffectType.HUNGER, 0, tickIntervalSeconds)
            }
            ThirstPhase.SEVERE_THIRST -> {
                addEffect(player, PotionEffectType.HUNGER, 1, tickIntervalSeconds)
                addEffect(player, PotionEffectType.SLOWNESS, 0, tickIntervalSeconds)
            }
            ThirstPhase.DEHYDRATED -> {
                if (allowDamagingEffects) {
                    addEffect(player, PotionEffectType.WITHER, 0, tickIntervalSeconds)
                }
            }
        }
    }

    private fun applyVisibilityWeatherEffects(
        player: Player,
        state: PlayerEnvState,
        global: GlobalEnvState,
        localWeatherState: com.pixlehavencore.feature.realworld.WeatherState?,
    ) {
        val visibilityWeather = if (localWeatherState != null) {
            localWeatherState.type.takeIf { it.affectsVisibility }
        } else {
            global.weather.takeIf { it.affectsVisibility }
        }
        val weather = visibilityWeather ?: return
        val visibilityDurationSeconds = WeatherSettings.visibilityEffectDurationSeconds
        when (weather) {
            WeatherType.FOG -> {
                addEffect(player, PotionEffectType.BLINDNESS, WeatherSettings.fogBlindnessAmplifier, visibilityDurationSeconds)
            }
            WeatherType.BLIZZARD -> {
                if (!state.isWeatherSheltered) {
                    addEffect(player, PotionEffectType.BLINDNESS, WeatherSettings.blizzardBlindnessAmplifier, visibilityDurationSeconds)
                }
            }
            WeatherType.SANDSTORM -> {
                if (!state.isWeatherSheltered) {
                    addEffect(player, PotionEffectType.BLINDNESS, WeatherSettings.sandstormBlindnessAmplifier, visibilityDurationSeconds)
                }
            }
            else -> Unit
        }
    }

    private fun applyWeatherExposureEffects(
        player: Player,
        state: PlayerEnvState,
        global: GlobalEnvState,
        tickIntervalSeconds: Int,
        localWeatherState: com.pixlehavencore.feature.realworld.WeatherState?,
    ) {
        val weather = currentDamagingWeather(global, state, localWeatherState) ?: run {
            resetWeatherExposure(state)
            clearWeatherExposureEffects(player)
            return
        }

        val elapsedSeconds = tickIntervalSeconds.coerceAtLeast(0).toDouble()
        val allowDamage = advanceWeatherExposure(state, weather, elapsedSeconds)
        applyWeatherEffectByType(player, weather, tickIntervalSeconds, allowDamage)
        if (!allowDamage) {
            return
        }

        if (state.weatherExposureDamageTimer > 0.0) {
            return
        }

        player.damage(computeWeatherExposureDamage(global, weather))
        state.weatherExposureDamageTimer = WeatherSettings.extremeDamageIntervalSeconds.toDouble()
    }

    private fun currentDamagingWeather(
        global: GlobalEnvState,
        state: PlayerEnvState,
        localWeatherState: com.pixlehavencore.feature.realworld.WeatherState?,
    ): WeatherType? {
        if (state.isWeatherSheltered) {
            return null
        }
        val weather = if (localWeatherState != null) {
            localWeatherState.type
        } else {
            global.weather
        }
        if (!weather.hasDamageEffect || !weather.isExtreme) {
            return null
        }
        return weather
    }

    private fun advanceWeatherExposure(
        state: PlayerEnvState,
        weather: WeatherType,
        elapsedSeconds: Double,
    ): Boolean {
        val switchedWeather = state.weatherExposureSource != weather
        if (switchedWeather) {
            state.weatherExposureSource = weather
            state.weatherExposureGraceTimer = WeatherSettings.extremeGracePeriodSeconds.toDouble()
            state.weatherExposureDamageTimer = WeatherSettings.extremeDamageIntervalSeconds.toDouble()
        }

        if (state.weatherExposureGraceTimer > 0.0) {
            state.weatherExposureGraceTimer = (state.weatherExposureGraceTimer - elapsedSeconds).coerceAtLeast(0.0)
            return state.weatherExposureGraceTimer <= 0.0
        }

        state.weatherExposureDamageTimer = (state.weatherExposureDamageTimer - elapsedSeconds).coerceAtLeast(0.0)
        return true
    }

    private fun applyWeatherEffectByType(
        player: Player,
        weather: WeatherType,
        tickIntervalSeconds: Int,
        allowDamage: Boolean,
    ) {
        when (weather) {
            WeatherType.BLIZZARD -> {
                addEffect(player, PotionEffectType.SLOWNESS, 1, tickIntervalSeconds)
                spawnWeatherParticles(player, Particle.SNOWFLAKE, 30, 0.8, 0.5, 0.8, 0.1)
            }
            WeatherType.SANDSTORM -> {
                addEffect(player, PotionEffectType.SLOWNESS, 0, tickIntervalSeconds)
                addEffect(player, PotionEffectType.MINING_FATIGUE, 0, tickIntervalSeconds)
                spawnWeatherParticles(player, Particle.DUST, 40, 1.2, 0.8, 1.2, 0.05)
            }
            WeatherType.ACID_RAIN -> {
                addEffect(player, PotionEffectType.WEAKNESS, 0, tickIntervalSeconds)
                if (allowDamage) {
                    addEffect(player, PotionEffectType.POISON, 0, tickIntervalSeconds)
                }
                spawnWeatherParticles(player, Particle.FALLING_WATER, 25, 0.6, 1.0, 0.6, 0.2)
                val loc = player.location.add(0.0, 1.0, 0.0)
                player.world.spawnParticle(Particle.ENTITY_EFFECT, loc, 15, 0.5, 0.8, 0.5, 0.0)
            }
            else -> Unit
        }
    }

    private fun spawnWeatherParticles(
        player: Player,
        particle: Particle,
        count: Int,
        offsetX: Double,
        offsetY: Double,
        offsetZ: Double,
        speed: Double,
        data: Any? = null
    ) {
        val location = player.location.add(0.0, 1.0, 0.0)
        if (data != null) {
            player.world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, speed, data)
        } else {
            player.world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, speed)
        }
    }

    private fun computeWeatherExposureDamage(global: GlobalEnvState, weather: WeatherType): Double {
        val intensity = global.weatherIntensity.coerceIn(0.0, 1.0)
        val severityMultiplier = when (weather) {
            WeatherType.BLIZZARD -> 0.75
            WeatherType.SANDSTORM -> 1.0
            WeatherType.ACID_RAIN -> 1.35
            else -> 1.0
        }
        val scaledHearts = WeatherSettings.extremeBaseDamageHearts * severityMultiplier * (0.5 + intensity)
        return scaledHearts.coerceAtLeast(0.0) * 2.0
    }

    private fun resetWeatherExposure(state: PlayerEnvState) {
        state.weatherExposureSource = null
        state.weatherExposureGraceTimer = 0.0
        state.weatherExposureDamageTimer = 0.0
    }

    private fun clearWeatherExposureEffects(player: Player) {
        // 天气暴露效果使用短持续时间，自然过期即可，避免误清除其他系统或外部来源的同类药水效果。
    }

    private fun isInSevereState(state: PlayerEnvState): Boolean {
        return state.temperaturePhase == TemperaturePhase.SEVERE_HEAT ||
            state.temperaturePhase == TemperaturePhase.SEVERE_COLD ||
            state.thirstPhase == ThirstPhase.DEHYDRATED
    }

    private fun clearDamagingEffects(player: Player) {
        player.removePotionEffect(PotionEffectType.WITHER)
    }

    private fun addEffect(
        player: Player,
        type: PotionEffectType?,
        amplifier: Int,
        tickIntervalSeconds: Int,
    ) {
        type ?: return
        val durationTicks = tickIntervalSeconds.coerceAtLeast(1) * 20 + 10
        player.addPotionEffect(PotionEffect(type, durationTicks, amplifier, false, false, false))
    }

    private fun applyStaminaEffects(player: Player, state: PlayerEnvState, tickIntervalSeconds: Int) {
        StaminaEngine.applyEffects(player, state, tickIntervalSeconds)

        // DEPLETED 聊天提醒
        if (state.staminaPhase == StaminaPhase.DEPLETED) {
            state.staminaChatWarnCooldown -= tickIntervalSeconds.coerceAtLeast(0).toDouble()
            if (state.staminaChatWarnCooldown <= 0.0) {
                state.staminaChatWarnCooldown = StaminaSettings.depletedReminderCooldownSeconds
                val max = StaminaEngine.getMaxStamina(state)
                val percent = (state.stamina / max * 100).toInt()
                player.sendMessage(
                    StaminaSettings.msgDepletedReminder
                        .replace("&", "§")
                        .replace("{stamina}", percent.toString())
                )
            }
        }
    }
}
