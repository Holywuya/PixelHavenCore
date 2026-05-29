package com.pixlehavencore.feature.realworld.fracture

import com.pixlehavencore.bridge.TextBridge
import com.pixlehavencore.feature.realworld.PlayerEnvState
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent

/**
 * 骨折引擎
 * 处理高处坠落导致的骨折效果
 *
 * 骨折值 0-100：
 * - 0-19: 无骨折
 * - 20-49: 轻微骨折（移动减速 20%）
 * - 50-79: 中度骨折（移动减速 50%，禁止疾跑）
 * - 80-100: 严重骨折（移动减速 80%，禁止疾跑）
 *
 * 恢复方式：
 * - 自然恢复：每分钟恢复 2 点（骨折时无法疾跑时减半）
 * - 物品治疗：绷带恢复 30 点，石膏恢复 100 点（立即治愈）
 */
object FractureEngine {

    /**
     * 处理坠落伤害，计算骨折值
     */
    fun onFallDamage(player: Player, state: PlayerEnvState, event: EntityDamageEvent) {
        if (!FractureSettings.enabled) return
        if (event.cause != EntityDamageEvent.DamageCause.FALL) return

        val damage = event.finalDamage
        if (damage < FractureSettings.minFallDamage) return

        // 根据坠落伤害计算骨折值增量
        // 公式：(伤害 - 最小阈值) * 系数
        val fractureIncrease = (damage - FractureSettings.minFallDamage) * FractureSettings.damageMultiplier
        val newFracture = (state.fracture + fractureIncrease).coerceAtMost(100.0)

        if (newFracture > state.fracture) {
            val oldSeverity = classifyFracture(state.fracture)
            state.fracture = newFracture
            val newSeverity = classifyFracture(newFracture)

            // 播放骨折音效
            player.playSound(player.location, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.5f)
            player.playSound(player.location, Sound.BLOCK_BONE_BLOCK_BREAK, 0.8f, 1.0f)

            // 发送骨折提示
            val message = when (newSeverity) {
                FractureSeverity.NONE -> ""
                FractureSeverity.MILD -> if (oldSeverity == FractureSeverity.NONE) {
                    "&c你的腿部受到轻微骨折，移动速度降低！"
                } else ""
                FractureSeverity.MODERATE -> if (oldSeverity.ordinal < FractureSeverity.MODERATE.ordinal) {
                    "&c你的腿部受到中度骨折，无法疾跑！"
                } else ""
                FractureSeverity.SEVERE -> if (oldSeverity.ordinal < FractureSeverity.SEVERE.ordinal) {
                    "&4你的腿部严重骨折，几乎无法移动！"
                } else ""
            }

            if (message.isNotEmpty()) {
                TextBridge.sendActionBar(player, message)
            }
        }
    }

    /**
     * 骨折自然恢复（每 tick 调用）
     * 阶段惩罚（移动速度、疾跑禁止）已迁入 SurvivalEffectApplier
     */
    fun tickRecovery(player: Player, state: PlayerEnvState, tickSeconds: Int) {
        if (!FractureSettings.enabled) return

        if (state.fracture <= 0) return

        val severity = classifyFracture(state.fracture)

        // 基础恢复速率：每分钟 2 点
        var recoveryPerSecond = FractureSettings.recoveryRate / 60.0

        // 如果无法疾跑，恢复速度减半
        if (severity >= FractureSeverity.MODERATE) {
            recoveryPerSecond *= 0.5
        }

        val recovery = recoveryPerSecond * tickSeconds
        state.fracture = (state.fracture - recovery).coerceAtLeast(0.0)

        // 骨折完全恢复时通知玩家
        if (state.fracture == 0.0 && severity != FractureSeverity.NONE) {
            TextBridge.sendActionBar(player, "&a你的骨折已经完全恢复！")
            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f)
        }
    }

    /**
     * 使用治疗物品
     */
    fun useTreatment(player: Player, state: PlayerEnvState, treatment: FractureTreatment): Boolean {
        if (!FractureSettings.enabled) return false
        if (state.fracture <= 0) {
            TextBridge.sendActionBar(player, "&c你没有骨折，无需治疗！")
            return false
        }

        when (treatment) {
            FractureTreatment.BANDAGE -> {
                state.fracture = (state.fracture - FractureSettings.bandageHealAmount).coerceAtLeast(0.0)
                TextBridge.sendActionBar(player, "&a使用绷带，骨折值降低 30 点！")
                player.playSound(player.location, Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.0f)
            }
            FractureTreatment.CAST -> {
                state.fracture = 0.0
                TextBridge.sendActionBar(player, "&a使用石膏，骨折完全治愈！")
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f)
            }
        }

        // 恢复正常速度
        player.walkSpeed = 0.2f
        return true
    }

    /**
     * 分类骨折等级
     */
    fun classifyFracture(fractureValue: Double): FractureSeverity {
        return when {
            fractureValue < FractureSettings.mildThreshold -> FractureSeverity.NONE
            fractureValue < FractureSettings.moderateThreshold -> FractureSeverity.MILD
            fractureValue < FractureSettings.severeThreshold -> FractureSeverity.MODERATE
            else -> FractureSeverity.SEVERE
        }
    }

    /**
     * 获取骨折状态的颜色代码
     */
    fun getFractureColor(severity: FractureSeverity): String {
        return when (severity) {
            FractureSeverity.NONE -> "&a"
            FractureSeverity.MILD -> "&e"
            FractureSeverity.MODERATE -> "&6"
            FractureSeverity.SEVERE -> "&c"
        }
    }

    /**
     * 获取骨折状态的显示名称
     */
    fun getFractureDisplayName(severity: FractureSeverity): String {
        return when (severity) {
            FractureSeverity.NONE -> "健康"
            FractureSeverity.MILD -> "轻微骨折"
            FractureSeverity.MODERATE -> "中度骨折"
            FractureSeverity.SEVERE -> "严重骨折"
        }
    }
}

/**
 * 骨折等级
 */
enum class FractureSeverity {
    NONE,       // 无骨折
    MILD,       // 轻微
    MODERATE,   // 中度
    SEVERE      // 严重
}

/**
 * 治疗物品类型
 */
enum class FractureTreatment {
    BANDAGE,  // 绷带：恢复 30 点
    CAST      // 石膏：完全治愈
}
