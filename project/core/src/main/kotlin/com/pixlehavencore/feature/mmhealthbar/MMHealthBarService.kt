package com.pixlehavencore.feature.mmhealthbar

import com.pixlehavencore.bridge.TextBridge
import com.pixlehavencore.util.MythicMobsBridge
import com.pixlehavencore.util.cancelTaskSafely
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.Attributable
import org.bukkit.entity.Damageable
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import taboolib.common.platform.function.submit
import taboolib.platform.util.submit as submitOnEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object MMHealthBarService {

    private val activeBars = ConcurrentHashMap<UUID, ActiveBar>()
    private var tickTask: Any? = null

    private data class ActiveBar(
        val bossBar: BossBar,
        val entityId: UUID,
        val expireTask: Any?,
        val lastDamage: Double = 0.0,
    )

    fun isEnabled(): Boolean = MMHealthBarSettings.enabled

    fun init() {
        MMHealthBarSettings.init()
        startTickTask()
    }

    fun reload() {
        MMHealthBarSettings.reload()
        stopTickTask()
        if (MMHealthBarSettings.enabled) {
            startTickTask()
        }
    }

    fun stop() {
        stopTickTask()
        activeBars.values.forEach { entry ->
            entry.expireTask.cancelTaskSafely()
        }
        activeBars.clear()
    }

    fun showBar(player: Player, entity: Entity, damage: Double = 0.0) {
        val mobInfo = MythicMobsBridge.resolveMobInfo(entity) ?: return
        val damageable = entity as? Damageable ?: return
        val existing = activeBars[player.uniqueId]

        // 同一怪物 → 更新血量和伤害
        if (existing != null && existing.entityId == entity.uniqueId) {
            existing.expireTask.cancelTaskSafely()
            val maxHealth = (entity as? Attributable)?.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
            updateBar(player, existing.bossBar, mobInfo.displayName, damageable.health, maxHealth, damage)
            val newExpire = scheduleExpire(player.uniqueId)
            activeBars[player.uniqueId] = existing.copy(expireTask = newExpire, lastDamage = damage)
            return
        }

        // 新目标 → 移除旧 BossBar，创建新的
        existing?.let {
            it.expireTask.cancelTaskSafely()
            player.hideBossBar(it.bossBar)
        }

        val maxHealth = (entity as? Attributable)?.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
        val progress = (damageable.health / maxHealth).coerceIn(0.0, 1.0).toFloat()
        val bossBar = BossBar.bossBar(
            formatTitle(mobInfo.displayName, damageable.health, maxHealth, damage),
            progress,
            MMHealthBarSettings.barColor,
            MMHealthBarSettings.barOverlay,
        )
        player.showBossBar(bossBar)

        val expireTask = scheduleExpire(player.uniqueId)
        activeBars[player.uniqueId] = ActiveBar(bossBar, entity.uniqueId, expireTask, damage)
    }

    fun removeBar(playerUuid: UUID) {
        val entry = activeBars.remove(playerUuid) ?: return
        entry.expireTask.cancelTaskSafely()
        Bukkit.getPlayer(playerUuid)?.hideBossBar(entry.bossBar)
    }

    fun onEntityRemoved(entityUuid: UUID) {
        val toRemove = activeBars.entries.filter { it.value.entityId == entityUuid }
        toRemove.forEach { (playerUuid, entry) ->
            entry.expireTask.cancelTaskSafely()
            Bukkit.getPlayer(playerUuid)?.hideBossBar(entry.bossBar)
            activeBars.remove(playerUuid)
        }
    }

    private fun tickUpdate() {
        activeBars.entries.toList().forEach { (playerUuid, entry) ->
            val player = Bukkit.getPlayer(playerUuid)
            if (player == null || !player.isOnline) {
                entry.expireTask.cancelTaskSafely()
                activeBars.remove(playerUuid)
                return@forEach
            }
            val entity = Bukkit.getEntity(entry.entityId)
            if (entity == null) {
                entry.expireTask.cancelTaskSafely()
                activeBars.remove(playerUuid)
                return@forEach
            }
            // Folia: 实体状态必须在实体所在区域线程读取
            entity.submitOnEntity {
                if (entity.isDead || !entity.isValid) {
                    entry.expireTask.cancelTaskSafely()
                    player.hideBossBar(entry.bossBar)
                    activeBars.remove(playerUuid)
                    return@submitOnEntity
                }
                val damageable = entity as? Damageable ?: return@submitOnEntity
                val maxHealth = (entity as? Attributable)?.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
                val mobInfo = MythicMobsBridge.resolveMobInfo(entity)
                val name = mobInfo?.displayName ?: "Unknown"
                updateBar(player, entry.bossBar, name, damageable.health, maxHealth, 0.0)
                // 使用 synchronized 确保复合操作的原子性
                synchronized(activeBars) {
                    activeBars[playerUuid] = entry.copy(lastDamage = 0.0)
                }
            }
        }
    }

    private fun updateBar(player: Player, bossBar: BossBar, name: String, health: Double, maxHealth: Double, damage: Double) {
        bossBar.name(formatTitle(name, health, maxHealth, damage))
        bossBar.progress((health / maxHealth).coerceIn(0.0, 1.0).toFloat())
        player.showBossBar(bossBar)
    }

    private fun formatTitle(name: String, health: Double, maxHealth: Double, damage: Double): Component {
        val formatted = MMHealthBarSettings.titleFormat
            .replace("{name}", name)
            .replace("{health}", health.toInt().toString())
            .replace("{max_health}", maxHealth.toInt().toString())
        val title = TextBridge.fromMiniMessage(formatted)
        if (damage <= 0.0) return title
        val damageText = MMHealthBarSettings.damageFormat.replace("{damage}", damage.toInt().toString())
        return title.append(TextBridge.fromMiniMessage(damageText))
    }

    private fun scheduleExpire(playerUuid: UUID): Any? {
        return submit(delay = MMHealthBarSettings.removeDelayTicks) {
            val entry = activeBars.remove(playerUuid)
            if (entry != null) {
                Bukkit.getPlayer(playerUuid)?.hideBossBar(entry.bossBar)
            }
        }
    }

    private fun startTickTask() {
        tickTask = submit(period = MMHealthBarSettings.updateIntervalTicks) {
            tickUpdate()
        }
    }

    private fun stopTickTask() {
        tickTask.cancelTaskSafely()
        tickTask = null
    }
}
