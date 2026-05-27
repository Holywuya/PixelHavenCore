package com.pixlehavencore.feature.realworld

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

enum class StaminaPhase(
    val displayName: String,
    val speedMultiplier: Double,
    val canSprint: Boolean,
) {
    FULL("充沛", 1.0, true),
    TIRED("疲劳", 0.85, true),
    EXHAUSTED("筋疲力尽", 0.70, false),
    DEPLETED("体力耗尽", 0.50, false);

    companion object {
        fun fromPercentage(percentage: Double): StaminaPhase = when {
            percentage > 60.0 -> FULL
            percentage > 30.0 -> TIRED
            percentage > 10.0 -> EXHAUSTED
            else -> DEPLETED
        }
    }
}

enum class StaminaConsumeSource {
    SPRINT,
    SWIM,
    CLIMB,
    ATTACK,
    MINE,
    USE_TOOL,
    UNDERWATER,
    HIGH_ALTITUDE,
    ENVIRONMENT,
}

enum class StaminaRecoverSource {
    IDLE,
    FOOD,
    DRINK,
    SLEEP,
    SPECIAL_ITEM,
    COMMAND,
}

class StaminaConsumeEvent(
    val player: Player,
    val source: StaminaConsumeSource,
    val amount: Double,
) : Event(true), Cancellable {

    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled
    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

    override fun getHandlers(): HandlerList = getHandlerList()

    companion object {
        private val handlers = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }
}

class StaminaRecoverEvent(
    val player: Player,
    val source: StaminaRecoverSource,
    val amount: Double,
) : Event(true), Cancellable {

    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled
    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

    override fun getHandlers(): HandlerList = getHandlerList()

    companion object {
        private val handlers = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }
}

class StaminaPhaseChangeEvent(
    val player: Player,
    val from: StaminaPhase,
    val to: StaminaPhase,
) : Event(true), Cancellable {

    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled
    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

    override fun getHandlers(): HandlerList = getHandlerList()

    companion object {
        private val handlers = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }
}

class StaminaDepletedEvent(
    val player: Player,
) : Event(true) {

    override fun getHandlers(): HandlerList = getHandlerList()

    companion object {
        private val handlers = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }
}
