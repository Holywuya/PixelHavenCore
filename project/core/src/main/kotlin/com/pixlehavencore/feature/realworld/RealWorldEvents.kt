package com.pixlehavencore.feature.realworld

import com.pixlehavencore.feature.realworld.fracture.FractureEngine
import com.pixlehavencore.feature.realworld.fracture.FractureSettings
import com.pixlehavencore.feature.realworld.fracture.FractureTreatment
import com.pixlehavencore.feature.realworld.foodcorrosion.FoodCorrosionEngine
import com.pixlehavencore.feature.realworld.thirst.ThirstEngine
import com.pixlehavencore.feature.realworld.thirst.ThirstSettings
import com.pixlehavencore.util.TextUtils
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.potion.PotionType
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.platform.util.isRightClick
import taboolib.platform.util.submit as submitOnEntity

object RealWorldEvents {

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerItemConsume(event: PlayerItemConsumeEvent) {
        if (!RealWorldSettings.enabled) return

        val item = event.item
        val player = event.player
        val uuid = player.uniqueId
        val generation = RealWorldService.lifecycleGeneration

        if (isWaterBottle(item.type, item.itemMeta as? PotionMeta)) {
            player.submitOnEntity {
                if (!RealWorldService.isActive(generation)) return@submitOnEntity
                RealWorldStorage.withPlayerState(uuid) { state ->
                    ThirstEngine.onWaterBottleConsume(state)
                } ?: return@submitOnEntity
                if (!RealWorldService.isActive(generation)) return@submitOnEntity
                RealWorldStorage.markPlayerDirty(uuid)
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (!RealWorldSettings.enabled) return
        if (!event.isRightClick()) return
        if (event.hand != null && event.hand != EquipmentSlot.HAND) return

        val player = event.player
        val uuid = player.uniqueId
        val generation = RealWorldService.lifecycleGeneration

        val heldItem = event.item
        if (heldItem != null && !heldItem.type.isAir) {
            val treatment = when (heldItem.type) {
                FractureSettings.bandageMaterial -> FractureTreatment.BANDAGE
                FractureSettings.castMaterial -> FractureTreatment.CAST
                else -> null
            }
            if (treatment != null) {
                player.submitOnEntity {
                    if (!RealWorldService.isActive(generation)) return@submitOnEntity
                    val changed = RealWorldStorage.withPlayerState(uuid) { state ->
                        FractureEngine.useTreatment(player, state, treatment)
                    } ?: return@submitOnEntity
                    if (!changed) return@submitOnEntity
                    heldItem.amount = heldItem.amount - 1
                    RealWorldStorage.markPlayerDirty(uuid)
                }
                return
            }
        }

        val block = event.clickedBlock ?: return
        if (!ThirstEngine.isDrinker(block) && !ThirstEngine.isNaturalWaterSource(block)) return

        player.submitOnEntity {
            if (!RealWorldService.isActive(generation)) return@submitOnEntity
            val changed = RealWorldStorage.withPlayerState(uuid) { state ->
                when {
                    ThirstEngine.isDrinker(block) -> handleDrinkerInteract(uuid, state, block)
                    else -> ThirstEngine.onRightClickNaturalWaterSource(player, state, block)
                }
            } ?: return@submitOnEntity
            if (!changed) return@submitOnEntity
            RealWorldStorage.markPlayerDirty(uuid)
        }
    }

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageEvent) {
        if (!RealWorldSettings.enabled || !FractureSettings.enabled) return

        val player = event.entity as? Player ?: return
        val uuid = player.uniqueId
        val generation = RealWorldService.lifecycleGeneration

        player.submitOnEntity {
            if (!RealWorldService.isActive(generation)) return@submitOnEntity
            RealWorldStorage.withPlayerState(uuid) { state ->
                FractureEngine.onFallDamage(player, state, event)
            } ?: return@submitOnEntity
            if (!RealWorldService.isActive(generation)) return@submitOnEntity
            RealWorldStorage.markPlayerDirty(uuid)
        }
    }

    private fun handleDrinkerInteract(uuid: java.util.UUID, state: PlayerEnvState, block: org.bukkit.block.Block): Boolean {
        if (!ThirstEngine.isDrinker(block)) return false

        val now = System.currentTimeMillis()
        val cooldownUntil = RealWorldService.drinkerCooldownUntil[uuid] ?: 0L
        if (now < cooldownUntil) return false

        val changed = ThirstEngine.onRightClickDrinker(state, block)
        if (!changed) return false

        val cooldownMillis = ThirstSettings.drinkerCooldownSeconds.coerceAtLeast(0) * 1000L
        if (cooldownMillis > 0L) {
            RealWorldService.drinkerCooldownUntil[uuid] = now + cooldownMillis
        }
        return true
    }

    private fun isWaterBottle(type: Material, meta: PotionMeta?): Boolean {
        return type == Material.POTION && meta?.basePotionType == PotionType.WATER
    }

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        if (!RealWorldSettings.enabled) return

        val inventory = event.inventory
        if (!FoodCorrosionEngine.isStorageContainer(inventory.type)) return

        val player = event.player as? Player ?: return
        val generation = RealWorldService.lifecycleGeneration

        player.submitOnEntity {
            if (!RealWorldService.isActive(generation)) return@submitOnEntity
            val expired = FoodCorrosionEngine.tickContainer(inventory)
            if (expired.isEmpty()) return@submitOnEntity
            for ((slot, item) in expired) {
                inventory.setItem(slot, item)
            }
            if (expired.isNotEmpty()) {
                player.sendMessage(
                    TextUtils.parseMiniMessage("<yellow>容器中有 ${expired.size} 个食物已经腐烂了！")
                )
            }
        }
    }
}

class RealWorldSeasonChangedEvent(
    val previousSeason: Season,
    val season: Season,
    val seasonProgress: Double,
) : Event(true) {
    override fun getHandlers(): HandlerList = getHandlerList()
    companion object {
        private val handlers = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = handlers
    }
}
