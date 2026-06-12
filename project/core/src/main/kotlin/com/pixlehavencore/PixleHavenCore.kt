package com.pixlehavencore

import com.pixlehavencore.config.ConfigAlignService
import com.pixlehavencore.feature.base.killme.KillmeSettings
import com.pixlehavencore.feature.base.back.BackService
import com.pixlehavencore.feature.base.back.BackStorage
import com.pixlehavencore.feature.base.back.BackSettings
import com.pixlehavencore.feature.base.protection.ProtectionSettings
import com.pixlehavencore.playerstate.PlayerStateService
import com.pixlehavencore.feature.craftingbench.CraftingBenchService
import com.pixlehavencore.feature.flight.FlightService
import com.pixlehavencore.feature.flight.FlightSettings
import com.pixlehavencore.feature.deathdrop.DeathDropSettings
import com.pixlehavencore.feature.deathdrop.DeathDropUsageStorage
import com.pixlehavencore.feature.economy.EconomySettings
import com.pixlehavencore.feature.economy.EconomyPlaceholders
import com.pixlehavencore.feature.economy.EconomyProvider
import com.pixlehavencore.feature.economy.TaxPlaceholders
import com.pixlehavencore.feature.grindstone.GrindstoneRepairSettings
import com.pixlehavencore.feature.keycommand.KeyCommandSettings
import com.pixlehavencore.feature.keycommand.KeyCommandService
import com.pixlehavencore.feature.notification.NotificationService
import com.pixlehavencore.feature.notification.NotificationSettings
import com.pixlehavencore.feature.playerinv.PlayerInvService
import com.pixlehavencore.feature.playerinv.PlayerInvSettings
import com.pixlehavencore.feature.mmhealthbar.MMHealthBarService
import com.pixlehavencore.feature.playerinfo.PlayerInfoService
import com.pixlehavencore.feature.playerinfo.PlayerInfoSettings
import com.pixlehavencore.feature.trade.TradeService
import com.pixlehavencore.feature.vanish.VanishService
import com.pixlehavencore.feature.vanish.VanishSettings
import com.pixlehavencore.feature.veinminer.VeinminerLimitService
import com.pixlehavencore.feature.veinminer.VeinminerSettings
import com.pixlehavencore.feature.optimization.entityclearer.EntityClearerService
import com.pixlehavencore.feature.optimization.viewdistance.ViewDistanceService
import com.pixlehavencore.feature.optimization.viewdistance.ViewDistanceSettings
import com.pixlehavencore.feature.playtime.PlaytimeSettings
import com.pixlehavencore.feature.playtime.PlaytimeStorage
import com.pixlehavencore.feature.playtime.PlaytimeService
import com.pixlehavencore.feature.playtime.PlaytimePlaceholders
import com.pixlehavencore.feature.title.TitleSettings
import com.pixlehavencore.feature.title.TitleStorage
import com.pixlehavencore.feature.title.TitleService
import com.pixlehavencore.feature.title.TitlePlaceholders
import com.pixlehavencore.util.CraftEngineItemsUtil
import com.pixlehavencore.util.EconomyUtils
import com.pixlehavencore.util.ItemUtils
import taboolib.common.platform.Plugin
import taboolib.common.platform.function.info

object PixleHavenCore : Plugin() {

    override fun onEnable() {
        ConfigAlignService.alignAll()
        PixleHavenSettings.init()
        VeinminerSettings.init()
        VeinminerLimitService.init()
        GrindstoneRepairSettings.init()
        CraftingBenchService.init()
        NotificationService.init()
        ViewDistanceService.init()
        EntityClearerService.init()
        KeyCommandService.init()
        PlayerInvService.init()
        TradeService.init()
        VanishSettings.init()
        VanishService.init()
        DeathDropSettings.init()
        DeathDropUsageStorage.init()
        EconomyPlaceholders
        EconomyProvider.init()
        TaxPlaceholders
        KillmeSettings.init()
        BackService.init()
        ProtectionSettings.init()
        PlayerStateService.init()
        PlayerInfoService.init()
        MMHealthBarService.init()
        PlaytimeSettings.init()
        PlaytimeStorage.init()
        PlaytimeService.init()
        PlaytimePlaceholders
        TitleSettings.init()
        TitleStorage.init()
        TitleService.init()
        TitlePlaceholders
        FlightService.init()
        logModulesStatus()
        info("Successfully running PixleHavenCore!")
    }

    private fun logModulesStatus() {
        info("=== PixleHavenCore 启动摘要 ===")
        logEnabledGroup("核心功能", listOf(
            "Veinminer" to VeinminerSettings.enabled,
            "Grindstone Repair" to GrindstoneRepairSettings.enabled,
            "Server Notification" to NotificationSettings.enabled,
            "Vanish" to VanishSettings.enabled,
            "Death Drop" to DeathDropSettings.enabled,
            "Base/Killme" to KillmeSettings.enabled,
            "Base/Back" to BackSettings.enabled,
            "Base/Protection" to ProtectionSettings.creeperProtectEnabled,
            "Warehouse" to PlayerInvSettings.enabled,
            "Key Command" to KeyCommandSettings.enabled,
        ))
        logEnabledGroup("独立模块", listOf(
            "Economy System" to EconomySettings.enabled,
            "Playtime" to PlaytimeSettings.enabled,
            "Crafting Bench" to CraftingBenchService.isEnabled(),
            "PlayerInfo" to PlayerInfoSettings.enabled,
            "MM HealthBar" to MMHealthBarService.isEnabled(),
            "Title" to TitleSettings.enabled,
            "Flight" to FlightSettings.enabled,
        ))
        logEnabledGroup("优化模块", listOf(
            "View Distance Controller" to ViewDistanceSettings.enabled,
            "Entity Clearer" to EntityClearerService.isEnabled(),
        ))
        logEnabledGroup("软依赖", listOf(
            "CraftEngine" to CraftEngineItemsUtil.isAvailable(),
            "Vault Economy" to EconomyUtils.isAvailable(),
        ))
        info("=== 启动完成 ===")
    }

    private fun logEnabledGroup(title: String, items: List<Pair<String, Boolean>>) {
        val enabledNames = items.filter { it.second }.map { it.first }
        if (enabledNames.isNotEmpty()) {
            info("[$title]")
            enabledNames.forEach { name ->
                info("$name 模块 已启动。")
            }
        }
    }

    override fun onDisable() {
        NotificationService.stopAutoNotifications()
        EntityClearerService.stopTasks()
        ViewDistanceService.stop()
        DeathDropUsageStorage.close()
        PlayerInvService.close()
        PlayerInfoService.stop()
        MMHealthBarService.stop()
        CraftingBenchService.stop()
        EconomyProvider.stop()
        PlaytimeService.stop()
        PlaytimeStorage.stop()
        TitleService.stop()
        TitleStorage.stop()
        FlightService.stop()
        VeinminerLimitService.stop()
        TradeService.stop()
        VanishService.stop()
        BackService.stop()
        PlayerStateService.stop()
        BackStorage.close()
        ItemUtils.clearHeadCache()
    }
}
