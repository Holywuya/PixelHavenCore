package com.pixlehavencore.feature.realworld.stamina

import com.pixlehavencore.bridge.TextBridge
import com.pixlehavencore.feature.realworld.GlobalEnvState
import com.pixlehavencore.feature.realworld.PlayerEnvState
import com.pixlehavencore.feature.realworld.RealWorldService
import com.pixlehavencore.feature.realworld.RealWorldStorage
import com.pixlehavencore.feature.realworld.Season
import com.pixlehavencore.feature.realworld.TemperaturePhase
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

data class StaminaInfo(
    val stamina: Double,
    val maxStamina: Double,
    val percentage: Double,
    val phase: StaminaPhase,
)

object StaminaEngine {

    fun init() {
        StaminaSettings.init()
    }

    fun reload() {
        StaminaSettings.reload()
    }

    fun getMaxStamina(playerState: PlayerEnvState): Double {
        var max = StaminaSettings.maxStamina
        if (StaminaSettings.integrationFractureEnabled && playerState.fracture > StaminaSettings.integrationFractureThreshold) {
            val reductionScale = ((playerState.fracture - StaminaSettings.integrationFractureThreshold) / (100.0 - StaminaSettings.integrationFractureThreshold)).coerceIn(0.0, 1.0)
            max -= StaminaSettings.integrationFractureMaxStaminaReduction * reductionScale
        }
        return max.coerceAtLeast(1.0)
    }

    fun tick(player: Player, playerState: PlayerEnvState, globalState: GlobalEnvState, tickSeconds: Int) {
        if (!StaminaSettings.enabled) return

        val max = getMaxStamina(playerState)
        val elapsed = tickSeconds.coerceAtLeast(0).toDouble()

        // 持续消耗（奔跑/游泳/攀爬）
        val continuousMultiplier = computeContinuousMultiplier(player)
        val environmentMultiplier = computeEnvironmentMultiplier(player, playerState, globalState)
        val integrationMultiplier = computeIntegrationMultiplier(playerState, globalState)
        val totalConsumeMultiplier = (continuousMultiplier * environmentMultiplier * integrationMultiplier).coerceAtMost(StaminaSettings.maxMultiplier)

        val consume = StaminaSettings.baseConsumptionRate * elapsed * totalConsumeMultiplier
        if (consume > 0) {
            val event = StaminaConsumeEvent(player, StaminaConsumeSource.ENVIRONMENT, consume)
            Bukkit.getPluginManager().callEvent(event)
            if (!event.isCancelled) {
                playerState.stamina = (playerState.stamina - consume).coerceIn(0.0, max)
            }
        }

        // 水下憋气消耗
        if (isUnderwater(player)) {
            val underwaterConsume = StaminaSettings.baseConsumptionRate * elapsed * StaminaSettings.underwaterMultiplier
            if (underwaterConsume > 0) {
                val event = StaminaConsumeEvent(player, StaminaConsumeSource.UNDERWATER, underwaterConsume)
                Bukkit.getPluginManager().callEvent(event)
                if (!event.isCancelled) {
                    playerState.stamina = (playerState.stamina - underwaterConsume).coerceIn(0.0, max)
                }
            }
        }

        // 高空消耗
        if (player.location.blockY > StaminaSettings.highAltitudeY) {
            val altitudeConsume = StaminaSettings.baseConsumptionRate * elapsed * StaminaSettings.highAltitudeMultiplier
            if (altitudeConsume > 0) {
                val event = StaminaConsumeEvent(player, StaminaConsumeSource.HIGH_ALTITUDE, altitudeConsume)
                Bukkit.getPluginManager().callEvent(event)
                if (!event.isCancelled) {
                    playerState.stamina = (playerState.stamina - altitudeConsume).coerceIn(0.0, max)
                }
            }
        }

        // 恢复冷却
        if (playerState.staminaRecoveryCooldown > 0) {
            playerState.staminaRecoveryCooldown = (playerState.staminaRecoveryCooldown - elapsed).coerceAtLeast(0.0)
        }

        // 阶段判定与更新
        val oldPhase = playerState.staminaPhase
        val newPhase = classifyPhase(playerState.stamina)
        if (newPhase != oldPhase) {
            val phaseEvent = StaminaPhaseChangeEvent(player, oldPhase, newPhase)
            Bukkit.getPluginManager().callEvent(phaseEvent)
            if (!phaseEvent.isCancelled) {
                playerState.staminaPhase = newPhase
                sendPhaseChangeMessage(player, playerState, oldPhase, newPhase)
            }
        }
    }

    fun checkIdle(player: Player, playerState: PlayerEnvState, deltaSeconds: Double) {
        if (!StaminaSettings.enabled || !StaminaSettings.idleEnabled) return

        if (!canIdleRecover(player, playerState)) {
            playerState.staminaIdleTimer = 0.0
            return
        }

        val isMoving = player.velocity.lengthSquared() > 0.001 || player.isSprinting || player.isFlying
        if (isMoving) {
            playerState.staminaIdleTimer = 0.0
            return
        }

        playerState.staminaIdleTimer += deltaSeconds
        if (playerState.staminaIdleTimer < StaminaSettings.idleDelaySeconds) return

        val max = getMaxStamina(playerState)
        if (playerState.stamina >= max) return

        var recoveryRate = StaminaSettings.baseRecoveryRate * StaminaSettings.idleMultiplier

        if (StaminaSettings.integrationThirstEnabled && playerState.hydration < StaminaSettings.integrationThirstDehydrationThreshold) {
            recoveryRate *= StaminaSettings.integrationThirstRecoveryMultiplier
        }

        if (StaminaSettings.integrationFoodEnabled && player.foodLevel >= 20) {
            recoveryRate *= StaminaSettings.integrationFoodFullSaturationBonus
        }

        val recovery = recoveryRate * deltaSeconds
        if (recovery > 0) {
            val event = StaminaRecoverEvent(player, StaminaRecoverSource.IDLE, recovery)
            Bukkit.getPluginManager().callEvent(event)
            if (!event.isCancelled) {
                playerState.stamina = (playerState.stamina + recovery).coerceIn(0.0, max)
            }
        }
    }

    fun onAttack(player: Player, playerState: PlayerEnvState) {
        consumeAction(player, playerState, StaminaSettings.attackCost, StaminaConsumeSource.ATTACK)
    }

    fun onMine(player: Player, playerState: PlayerEnvState) {
        consumeAction(player, playerState, StaminaSettings.mineCost, StaminaConsumeSource.MINE)
    }

    fun onUseTool(player: Player, playerState: PlayerEnvState) {
        consumeAction(player, playerState, StaminaSettings.useToolCost, StaminaConsumeSource.USE_TOOL)
    }

    fun onEat(player: Player, playerState: PlayerEnvState, hungerRestored: Int) {
        if (!StaminaSettings.enabled || !StaminaSettings.foodEnabled) return
        if (playerState.staminaRecoveryCooldown > 0) return

        val max = getMaxStamina(playerState)
        if (playerState.stamina >= max) return

        val recovery = hungerRestored * StaminaSettings.hungerToStaminaRatio
        if (recovery > 0) {
            val event = StaminaRecoverEvent(player, StaminaRecoverSource.FOOD, recovery)
            Bukkit.getPluginManager().callEvent(event)
            if (!event.isCancelled) {
                playerState.stamina = (playerState.stamina + recovery).coerceIn(0.0, max)
                playerState.staminaRecoveryCooldown = StaminaSettings.foodCooldownSeconds
            }
        }
    }

    fun onDrink(player: Player, playerState: PlayerEnvState, hydrationRestored: Double) {
        if (!StaminaSettings.enabled || !StaminaSettings.drinkEnabled) return

        val max = getMaxStamina(playerState)
        if (playerState.stamina >= max) return

        val recovery = hydrationRestored * StaminaSettings.hydrationToStaminaRatio
        if (recovery > 0) {
            val event = StaminaRecoverEvent(player, StaminaRecoverSource.DRINK, recovery)
            Bukkit.getPluginManager().callEvent(event)
            if (!event.isCancelled) {
                playerState.stamina = (playerState.stamina + recovery).coerceIn(0.0, max)
            }
        }
    }

    fun onSleep(player: Player, playerState: PlayerEnvState, isOutdoor: Boolean) {
        if (!StaminaSettings.enabled || !StaminaSettings.sleepEnabled) return

        if (StaminaSettings.sleepBlockedInExtremeTemp) {
            val phase = playerState.temperaturePhase
            if (phase == TemperaturePhase.SEVERE_HEAT || phase == TemperaturePhase.SEVERE_COLD) return
        }

        val max = getMaxStamina(playerState)
        val recovery = if (isOutdoor) max * (StaminaSettings.outdoorRecoveryPercent / 100.0) else max

        if (recovery > 0) {
            val event = StaminaRecoverEvent(player, StaminaRecoverSource.SLEEP, recovery)
            Bukkit.getPluginManager().callEvent(event)
            if (!event.isCancelled) {
                playerState.stamina = (playerState.stamina + recovery).coerceIn(0.0, max)
            }
        }
    }

    fun onSpecialItem(player: Player, playerState: PlayerEnvState, material: Material) {
        if (!StaminaSettings.enabled || !StaminaSettings.specialItemsEnabled) return

        val ratio = StaminaSettings.specialItems[material] ?: return
        val max = getMaxStamina(playerState)
        if (playerState.stamina >= max) return

        val recovery = max * ratio
        if (recovery > 0) {
            val event = StaminaRecoverEvent(player, StaminaRecoverSource.SPECIAL_ITEM, recovery)
            Bukkit.getPluginManager().callEvent(event)
            if (!event.isCancelled) {
                playerState.stamina = (playerState.stamina + recovery).coerceIn(0.0, max)
            }
        }
    }

    fun classifyPhase(stamina: Double): StaminaPhase {
        val max = StaminaSettings.maxStamina
        val percentage = (stamina / max) * 100.0
        return StaminaPhase.fromPercentage(percentage)
    }

    fun getStaminaInfo(player: Player): StaminaInfo? {
        val state = RealWorldStorage.getPlayerSnapshot(player.uniqueId) ?: return null
        val max = getMaxStamina(state)
        val percentage = (state.stamina / max * 100.0).coerceIn(0.0, 100.0)
        return StaminaInfo(state.stamina, max, percentage, state.staminaPhase)
    }

    fun setStamina(player: Player, amount: Double) {
        RealWorldStorage.withPlayerState(player.uniqueId) { state ->
            val max = getMaxStamina(state)
            state.stamina = amount.coerceIn(0.0, max)
            state.staminaPhase = classifyPhase(state.stamina)
            RealWorldStorage.markPlayerDirty(player.uniqueId)
        }
    }

    fun addStamina(player: Player, amount: Double) {
        RealWorldStorage.withPlayerState(player.uniqueId) { state ->
            val max = getMaxStamina(state)
            state.stamina = (state.stamina + amount).coerceIn(0.0, max)
            state.staminaPhase = classifyPhase(state.stamina)
            RealWorldStorage.markPlayerDirty(player.uniqueId)
        }
    }

    fun removeStamina(player: Player, amount: Double) {
        RealWorldStorage.withPlayerState(player.uniqueId) { state ->
            val max = getMaxStamina(state)
            val event = StaminaConsumeEvent(player, StaminaConsumeSource.COMMAND, amount)
            Bukkit.getPluginManager().callEvent(event)
            if (!event.isCancelled) {
                state.stamina = (state.stamina - amount).coerceIn(0.0, max)
                state.staminaPhase = classifyPhase(state.stamina)
                RealWorldStorage.markPlayerDirty(player.uniqueId)
            }
        }
    }

    fun resetStamina(player: Player) {
        RealWorldStorage.withPlayerState(player.uniqueId) { state ->
            state.stamina = getMaxStamina(state)
            state.staminaPhase = StaminaPhase.FULL
            state.staminaIdleTimer = 0.0
            state.staminaRecoveryCooldown = 0.0
            state.staminaChatWarnCooldown = 0.0
            RealWorldStorage.markPlayerDirty(player.uniqueId)
        }
    }

    // --- 内部方法 ---

    private fun consumeAction(player: Player, playerState: PlayerEnvState, baseCost: Double, source: StaminaConsumeSource) {
        if (!StaminaSettings.enabled) return

        val global = RealWorldService.getGlobalStateSnapshot() ?: GlobalEnvState()
        val environmentMultiplier = computeEnvironmentMultiplier(player, playerState, global)
        val cost = baseCost * environmentMultiplier
        if (cost <= 0) return

        val event = StaminaConsumeEvent(player, source, cost)
        Bukkit.getPluginManager().callEvent(event)
        if (!event.isCancelled) {
            val max = getMaxStamina(playerState)
            playerState.stamina = (playerState.stamina - cost).coerceIn(0.0, max)
        }
    }

    private fun computeContinuousMultiplier(player: Player): Double {
        return when {
            player.isSprinting -> StaminaSettings.sprintMultiplier
            isSwimming(player) -> StaminaSettings.swimMultiplier
            isClimbing(player) -> StaminaSettings.climbMultiplier
            else -> 1.0
        }
    }

    private fun computeEnvironmentMultiplier(player: Player, state: PlayerEnvState, global: GlobalEnvState): Double {
        var multiplier = 1.0

        if (StaminaSettings.integrationTemperatureEnabled) {
            val phase = state.temperaturePhase
            if (phase == TemperaturePhase.SEVERE_HEAT || phase == TemperaturePhase.SEVERE_COLD) {
                multiplier *= StaminaSettings.integrationTemperatureExtremeMultiplier
            } else if (phase == TemperaturePhase.HEAT || phase == TemperaturePhase.COLD || phase == TemperaturePhase.COLD_MILD) {
                multiplier *= StaminaSettings.integrationTemperatureMildMultiplier
            }
        }

        if (StaminaSettings.integrationFractureEnabled && state.fracture > StaminaSettings.integrationFractureThreshold) {
            multiplier *= StaminaSettings.integrationFractureConsumptionMultiplier
        }

        if (StaminaSettings.integrationWetnessEnabled && state.wetness > StaminaSettings.integrationWetnessThreshold) {
            multiplier *= StaminaSettings.integrationWetnessConsumptionMultiplier
        }

        if (StaminaSettings.integrationWeatherEnabled && !state.isWeatherSheltered) {
            val weather = global.weather
            if (weather.isExtreme) {
                multiplier *= StaminaSettings.integrationWeatherExtremeMultiplier
            }
        }

        if (StaminaSettings.integrationSeasonEnabled) {
            when (global.season) {
                Season.WINTER -> multiplier *= StaminaSettings.integrationSeasonWinterMultiplier
                Season.SUMMER -> multiplier *= StaminaSettings.integrationSeasonSummerMultiplier
                else -> {}
            }
        }

        return multiplier
    }

    private fun computeIntegrationMultiplier(state: PlayerEnvState, global: GlobalEnvState): Double {
        return 1.0
    }

    private fun canIdleRecover(player: Player, state: PlayerEnvState): Boolean {
        if (StaminaSettings.idleBlockedInExtremeTemp) {
            val phase = state.temperaturePhase
            if (phase == TemperaturePhase.SEVERE_HEAT || phase == TemperaturePhase.SEVERE_COLD) return false
        }

        if (StaminaSettings.idleBlockedUnderwater && isUnderwater(player)) return false

        return true
    }

    /**
     * 体力阶段药水效果（walkSpeed/sprint 由 SurvivalEffectApplier 统一管理）
     */
    internal fun applyEffects(player: Player, state: PlayerEnvState, tickSeconds: Int) {
        val phase = state.staminaPhase
        val durationTicks = StaminaSettings.effectDurationSeconds * 20 + 10

        when (phase) {
            StaminaPhase.FULL, StaminaPhase.TIRED -> Unit
            StaminaPhase.EXHAUSTED -> {
                PotionEffectType.MINING_FATIGUE?.let {
                    player.addPotionEffect(PotionEffect(it, durationTicks, StaminaSettings.exhaustedMiningFatigueAmplifier, false, false, false))
                }
                PotionEffectType.WEAKNESS?.let {
                    player.addPotionEffect(PotionEffect(it, durationTicks, StaminaSettings.exhaustedWeaknessAmplifier, false, false, false))
                }
            }
            StaminaPhase.DEPLETED -> {
                PotionEffectType.MINING_FATIGUE?.let {
                    player.addPotionEffect(PotionEffect(it, durationTicks, StaminaSettings.depletedMiningFatigueAmplifier, false, false, false))
                }
                PotionEffectType.WEAKNESS?.let {
                    player.addPotionEffect(PotionEffect(it, durationTicks, StaminaSettings.depletedWeaknessAmplifier, false, false, false))
                }
            }
        }
    }

    private fun sendPhaseChangeMessage(player: Player, state: PlayerEnvState, oldPhase: StaminaPhase, newPhase: StaminaPhase) {
        val enterMessage = when (newPhase) {
            StaminaPhase.TIRED -> if (oldPhase == StaminaPhase.FULL) StaminaSettings.msgEnterTired else null
            StaminaPhase.EXHAUSTED -> if (oldPhase.ordinal < StaminaPhase.EXHAUSTED.ordinal) StaminaSettings.msgEnterExhausted else null
            StaminaPhase.DEPLETED -> if (oldPhase.ordinal < StaminaPhase.DEPLETED.ordinal) StaminaSettings.msgEnterDepleted else null
            StaminaPhase.FULL -> null
        }

        val recoverMessage = when (oldPhase) {
            StaminaPhase.TIRED -> if (newPhase == StaminaPhase.FULL) StaminaSettings.msgRecoveredFromTired else null
            StaminaPhase.EXHAUSTED -> if (newPhase.ordinal < StaminaPhase.EXHAUSTED.ordinal) StaminaSettings.msgRecoveredFromExhausted else null
            StaminaPhase.DEPLETED -> if (newPhase.ordinal < StaminaPhase.DEPLETED.ordinal) StaminaSettings.msgRecoveredFromDepleted else null
            StaminaPhase.FULL -> null
        }

        val message = enterMessage ?: recoverMessage ?: return
        val max = getMaxStamina(state)
        val percent = (state.stamina / max * 100.0).toInt()
        val formatted = message.replace("&", "§").replace("{stamina}", percent.toString())
        player.sendMessage(formatted)
    }

    private fun isSwimming(player: Player): Boolean {
        return player.isSwimming || player.location.block.type == Material.WATER
    }

    private fun isClimbing(player: Player): Boolean {
        val block = player.location.block
        return block.type == Material.LADDER || block.type == Material.VINE
    }

    private fun isUnderwater(player: Player): Boolean {
        val eyeBlock = player.eyeLocation.block.type
        return eyeBlock == Material.WATER || eyeBlock == Material.BUBBLE_COLUMN
    }
}
