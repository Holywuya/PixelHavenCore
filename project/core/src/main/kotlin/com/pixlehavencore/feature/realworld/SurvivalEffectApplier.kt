package com.pixlehavencore.feature.realworld

import com.pixlehavencore.feature.realworld.fracture.FractureEngine
import com.pixlehavencore.feature.realworld.fracture.FractureSeverity
import com.pixlehavencore.feature.realworld.stamina.StaminaEngine
import com.pixlehavencore.feature.realworld.stamina.StaminaPhase
import com.pixlehavencore.feature.realworld.stamina.StaminaSettings
import com.pixlehavencore.feature.realworld.weather.WeatherSettings
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

object SurvivalEffectApplier {

    fun apply(player: Player, state: PlayerEnvState, global: GlobalEnvState, tickIntervalSeconds: Int) {
        applyExtremeNeedsEffects(player, state, tickIntervalSeconds)

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
