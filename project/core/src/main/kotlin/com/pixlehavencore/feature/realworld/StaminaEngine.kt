package com.pixlehavencore.feature.realworld

import com.pixlehavencore.bridge.TextBridge
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

        // 惩罚应用
        applyPenalties(player, playerState)

        // DEPLETED 提醒
        if (playerState.staminaPhase == StaminaPhase.DEPLETED) {
            playerState.staminaChatWarnCooldown -= elapsed
            if (playerState.staminaChatWarnCooldown <= 0.0) {
                playerState.staminaChatWarnCooldown = StaminaSettings.messageDepletedReminderCooldownSeconds.toDouble()
                TextBridge.sendActionBar(player, StaminaSettings.messageDepletedReminder)
            }
        }
    }

    fun checkIdle(player: Player, playerState: PlayerEnvState, deltaSeconds: Double) {
        if (!StaminaSettings.enabled || !StaminaSettings.recoveryIdleEnabled) return

        // 检查是否可以休息恢复
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
        if (playerState.staminaIdleTimer < StaminaSettings.recoveryIdleDelaySeconds) return

        val max = getMaxStamina(playerState)
        if (playerState.stamina >= max) return

        var recoveryRate = StaminaSettings.baseRecoveryRate * StaminaSettings.recoveryIdleMultiplier

        // 联动：口渴 < 30 时恢复减半
        if (StaminaSettings.integrationThirstEnabled && playerState.hydration < StaminaSettings.integrationThirstDehydrationThreshold) {
            recoveryRate *= StaminaSettings.integrationThirstRecoveryMultiplier
        }

        // 联动：饱食度满时恢复加成
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
        if (!StaminaSettings.enabled || !StaminaSettings.recoveryFoodEnabled) return
        if (playerState.staminaRecoveryCooldown > 0) return

        val max = getMaxStamina(playerState)
        if (playerState.stamina >= max) return

        val recovery = hungerRestored * StaminaSettings.recoveryFoodHungerToStaminaRatio
        if (recovery > 0) {
            val event = StaminaRecoverEvent(player, StaminaRecoverSource.FOOD, recovery)
            Bukkit.getPluginManager().callEvent(event)
            if (!event.isCancelled) {
                playerState.stamina = (playerState.stamina + recovery).coerceIn(0.0, max)
                playerState.staminaRecoveryCooldown = StaminaSettings.recoveryFoodCooldownSeconds.toDouble()
            }
        }
    }

    fun onDrink(player: Player, playerState: PlayerEnvState, hydrationRestored: Double) {
        if (!StaminaSettings.enabled || !StaminaSettings.recoveryDrinkEnabled) return

        val max = getMaxStamina(playerState)
        if (playerState.stamina >= max) return

        val recovery = hydrationRestored * StaminaSettings.recoveryDrinkHydrationToStaminaRatio
        if (recovery > 0) {
            val event = StaminaRecoverEvent(player, StaminaRecoverSource.DRINK, recovery)
            Bukkit.getPluginManager().callEvent(event)
            if (!event.isCancelled) {
                playerState.stamina = (playerState.stamina + recovery).coerceIn(0.0, max)
            }
        }
    }

    fun onSleep(player: Player, playerState: PlayerEnvState, isOutdoor: Boolean) {
        if (!StaminaSettings.enabled || !StaminaSettings.recoverySleepEnabled) return

        // 极端温度下禁止睡觉恢复
        if (StaminaSettings.recoverySleepBlockedInExtremeTemperature) {
            val phase = playerState.temperaturePhase
            if (phase == TemperaturePhase.SEVERE_HEAT || phase == TemperaturePhase.SEVERE_COLD) return
        }

        val max = getMaxStamina(playerState)
        val recovery = if (isOutdoor) max * StaminaSettings.recoverySleepOutdoorRecoveryPercent else max

        if (recovery > 0) {
            val event = StaminaRecoverEvent(player, StaminaRecoverSource.SLEEP, recovery)
            Bukkit.getPluginManager().callEvent(event)
            if (!event.isCancelled) {
                playerState.stamina = (playerState.stamina + recovery).coerceIn(0.0, max)
            }
        }
    }

    fun onSpecialItem(player: Player, playerState: PlayerEnvState, material: Material) {
        if (!StaminaSettings.enabled || !StaminaSettings.recoverySpecialItemsEnabled) return

        val ratio = StaminaSettings.recoverySpecialItems[material] ?: return
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

        // 极端温度
        if (StaminaSettings.integrationTemperatureEnabled) {
            val phase = state.temperaturePhase
            if (phase == TemperaturePhase.SEVERE_HEAT || phase == TemperaturePhase.SEVERE_COLD) {
                multiplier *= StaminaSettings.integrationTemperatureExtremeMultiplier
            } else if (phase == TemperaturePhase.HEAT || phase == TemperaturePhase.COLD || phase == TemperaturePhase.COLD_MILD) {
                multiplier *= StaminaSettings.integrationTemperatureMildMultiplier
            }
        }

        // 骨折
        if (StaminaSettings.integrationFractureEnabled && state.fracture > StaminaSettings.integrationFractureThreshold) {
            multiplier *= StaminaSettings.integrationFractureConsumptionMultiplier
        }

        // 潮湿
        if (StaminaSettings.integrationWetnessEnabled && state.wetness > StaminaSettings.integrationWetnessThreshold) {
            multiplier *= StaminaSettings.integrationWetnessConsumptionMultiplier
        }

        // 极端天气
        if (StaminaSettings.integrationWeatherEnabled && !state.isWeatherSheltered) {
            val weather = global.weather
            if (weather.isExtreme) {
                multiplier *= StaminaSettings.integrationWeatherExtremeMultiplier
            }
        }

        // 季节
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
        // 此方法用于 tick 中的整体倍率叠加，返回基础 1.0
        // 环境倍率已在 computeEnvironmentMultiplier 中处理
        return 1.0
    }

    private fun canIdleRecover(player: Player, state: PlayerEnvState): Boolean {
        // 极端温度下禁止休息
        if (StaminaSettings.recoveryIdleBlockedInExtremeTemperature) {
            val phase = state.temperaturePhase
            if (phase == TemperaturePhase.SEVERE_HEAT || phase == TemperaturePhase.SEVERE_COLD) return false
        }

        // 水下禁止休息
        if (StaminaSettings.recoveryIdleBlockedUnderwater && isUnderwater(player)) return false

        return true
    }

    private fun applyPenalties(player: Player, state: PlayerEnvState) {
        val phase = state.staminaPhase
        val durationTicks = StaminaSettings.penaltyEffectDurationSeconds * 20 + 10

        when (phase) {
            StaminaPhase.FULL -> {
                // 无惩罚，恢复默认速度
                resetWalkSpeed(player)
            }
            StaminaPhase.TIRED -> {
                val targetSpeed = 0.2f * StaminaSettings.penaltyTiredSpeedMultiplier.toFloat()
                if (player.walkSpeed != targetSpeed) {
                    player.walkSpeed = targetSpeed
                }
            }
            StaminaPhase.EXHAUSTED -> {
                val targetSpeed = 0.2f * StaminaSettings.penaltyExhaustedSpeedMultiplier.toFloat()
                if (player.walkSpeed != targetSpeed) {
                    player.walkSpeed = targetSpeed
                }
                player.isSprinting = false
                PotionEffectType.MINING_FATIGUE?.let {
                    player.addPotionEffect(PotionEffect(it, durationTicks, StaminaSettings.penaltyExhaustedMiningFatigueAmplifier, false, false, false))
                }
                PotionEffectType.WEAKNESS?.let {
                    player.addPotionEffect(PotionEffect(it, durationTicks, StaminaSettings.penaltyExhaustedWeaknessAmplifier, false, false, false))
                }
            }
            StaminaPhase.DEPLETED -> {
                val targetSpeed = 0.2f * StaminaSettings.penaltyDepletedSpeedMultiplier.toFloat()
                if (player.walkSpeed != targetSpeed) {
                    player.walkSpeed = targetSpeed
                }
                player.isSprinting = false
                PotionEffectType.MINING_FATIGUE?.let {
                    player.addPotionEffect(PotionEffect(it, durationTicks, StaminaSettings.penaltyDepletedMiningFatigueAmplifier, false, false, false))
                }
                PotionEffectType.WEAKNESS?.let {
                    player.addPotionEffect(PotionEffect(it, durationTicks, StaminaSettings.penaltyDepletedWeaknessAmplifier, false, false, false))
                }
            }
        }
    }

    private fun resetWalkSpeed(player: Player) {
        // 仅在体力为 FULL 且其他系统未修改速度时重置
        // 简单策略：如果当前速度是体力系统设置的减速值，则重置
        val currentSpeed = player.walkSpeed
        val expectedSpeeds = listOf(
            0.2f * StaminaSettings.penaltyTiredSpeedMultiplier.toFloat(),
            0.2f * StaminaSettings.penaltyExhaustedSpeedMultiplier.toFloat(),
            0.2f * StaminaSettings.penaltyDepletedSpeedMultiplier.toFloat(),
        )
        if (currentSpeed in expectedSpeeds) {
            player.walkSpeed = 0.2f
        }
    }

    private fun sendPhaseChangeMessage(player: Player, state: PlayerEnvState, oldPhase: StaminaPhase, newPhase: StaminaPhase) {
        // 进入更低阶段的提醒
        val enterMessage = when (newPhase) {
            StaminaPhase.TIRED -> if (oldPhase == StaminaPhase.FULL) StaminaSettings.messageEnterTired else null
            StaminaPhase.EXHAUSTED -> if (oldPhase.ordinal < StaminaPhase.EXHAUSTED.ordinal) StaminaSettings.messageEnterExhausted else null
            StaminaPhase.DEPLETED -> if (oldPhase.ordinal < StaminaPhase.DEPLETED.ordinal) StaminaSettings.messageEnterDepleted else null
            StaminaPhase.FULL -> null
        }

        // 恢复到更高阶段的提醒
        val recoverMessage = when (oldPhase) {
            StaminaPhase.TIRED -> if (newPhase == StaminaPhase.FULL) StaminaSettings.messageRecoveredFromTired else null
            StaminaPhase.EXHAUSTED -> if (newPhase.ordinal < StaminaPhase.EXHAUSTED.ordinal) StaminaSettings.messageRecoveredFromExhausted else null
            StaminaPhase.DEPLETED -> if (newPhase.ordinal < StaminaPhase.DEPLETED.ordinal) StaminaSettings.messageRecoveredFromDepleted else null
            StaminaPhase.FULL -> null
        }

        val message = enterMessage ?: recoverMessage ?: return
        val max = getMaxStamina(state)
        val percent = (state.stamina / max * 100.0).toInt()
        val formatted = message.replace("{stamina}", percent.toString())
        TextBridge.sendActionBar(player, formatted)
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
