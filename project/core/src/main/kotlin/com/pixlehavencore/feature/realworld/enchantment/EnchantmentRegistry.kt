package com.pixlehavencore.feature.realworld.enchantment

import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEventType
import io.papermc.paper.registry.RegistryKey
import io.papermc.paper.registry.TypedKey
import io.papermc.paper.registry.data.EnchantmentRegistryEntry
import io.papermc.paper.registry.event.RegistryComposeEvent
import io.papermc.paper.registry.event.RegistryEvents
import io.papermc.paper.registry.keys.ItemTypeKeys
import io.papermc.paper.registry.set.RegistrySet
import net.kyori.adventure.key.Key.key
import net.kyori.adventure.text.Component
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.EquipmentSlotGroup
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake

object EnchantmentRegistry {

    val TEMPERATURE_RESISTANCE_KEY: TypedKey<Enchantment> = TypedKey.create(RegistryKey.ENCHANTMENT, key("phcore", "temperature_resistance"))

    @Awake(LifeCycle.ENABLE)
    fun register() {
        try {
            val plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("phcore") ?: return
            val getLifecycleManager = plugin.javaClass.getMethod("getLifecycleManager")
            val lifecycleManager = getLifecycleManager.invoke(plugin) as LifecycleEventManager<BootstrapContext>

            @Suppress("UNCHECKED_CAST")
            val eventType = RegistryEvents.ENCHANTMENT.compose() as LifecycleEventType<BootstrapContext, RegistryComposeEvent<Enchantment, EnchantmentRegistryEntry.Builder>, *>

            lifecycleManager.registerEventHandler(eventType) { event ->
                event.registry().register(TEMPERATURE_RESISTANCE_KEY) { builder ->
                    builder.description(Component.text("温度抵抗"))
                        .supportedItems(
                            RegistrySet.keySet(
                                RegistryKey.ITEM,
                                ItemTypeKeys.LEATHER_HELMET,
                                ItemTypeKeys.LEATHER_CHESTPLATE,
                                ItemTypeKeys.LEATHER_LEGGINGS,
                                ItemTypeKeys.LEATHER_BOOTS,
                                ItemTypeKeys.CHAINMAIL_HELMET,
                                ItemTypeKeys.CHAINMAIL_CHESTPLATE,
                                ItemTypeKeys.CHAINMAIL_LEGGINGS,
                                ItemTypeKeys.CHAINMAIL_BOOTS,
                                ItemTypeKeys.IRON_HELMET,
                                ItemTypeKeys.IRON_CHESTPLATE,
                                ItemTypeKeys.IRON_LEGGINGS,
                                ItemTypeKeys.IRON_BOOTS,
                                ItemTypeKeys.GOLDEN_HELMET,
                                ItemTypeKeys.GOLDEN_CHESTPLATE,
                                ItemTypeKeys.GOLDEN_LEGGINGS,
                                ItemTypeKeys.GOLDEN_BOOTS,
                                ItemTypeKeys.DIAMOND_HELMET,
                                ItemTypeKeys.DIAMOND_CHESTPLATE,
                                ItemTypeKeys.DIAMOND_LEGGINGS,
                                ItemTypeKeys.DIAMOND_BOOTS,
                                ItemTypeKeys.NETHERITE_HELMET,
                                ItemTypeKeys.NETHERITE_CHESTPLATE,
                                ItemTypeKeys.NETHERITE_LEGGINGS,
                                ItemTypeKeys.NETHERITE_BOOTS,
                                ItemTypeKeys.TURTLE_HELMET
                            )
                        )
                        .weight(2)
                        .maxLevel(3)
                        .minimumCost(EnchantmentRegistryEntry.EnchantmentCost.of(5, 8))
                        .maximumCost(EnchantmentRegistryEntry.EnchantmentCost.of(50, 8))
                        .anvilCost(1)
                        .activeSlots(EquipmentSlotGroup.HEAD, EquipmentSlotGroup.CHEST, EquipmentSlotGroup.LEGS, EquipmentSlotGroup.FEET)
                        .exclusiveWith(RegistrySet.keySet(RegistryKey.ENCHANTMENT))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}