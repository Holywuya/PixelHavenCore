package com.pixlehavencore

import taboolib.common.platform.Plugin
import taboolib.common.platform.function.info
import com.pixlehavencore.feature.veinminer.VeinminerHook
import com.pixlehavencore.feature.veinminer.VeinminerLimitService
import com.pixlehavencore.feature.veinminer.VeinminerSettings
import com.pixlehavencore.feature.grindstone.GrindstoneRepairSettings
import com.pixlehavencore.feature.chat.ChatSettings
import com.pixlehavencore.feature.notification.NotificationSettings
import com.pixlehavencore.feature.notification.NotificationService
import com.pixlehavencore.feature.notification.NotificationCommand
import com.pixlehavencore.feature.optimization.viewdistance.ViewDistanceSettings
import com.pixlehavencore.feature.optimization.viewdistance.ViewDistanceService
import com.pixlehavencore.feature.menu.SimpleMenuSettings
import com.pixlehavencore.feature.menu.TrMenuLiteService
import com.pixlehavencore.feature.vanish.VanishSettings
import com.pixlehavencore.feature.vanish.VanishService
import com.pixlehavencore.feature.deathdrop.DeathDropSettings
import com.pixlehavencore.feature.helpinterceptor.HelpInterceptorSettings
import com.pixlehavencore.feature.helpinterceptor.HelpInterceptorService
import com.pixlehavencore.feature.autorestart.AutoRestartSettings
import com.pixlehavencore.feature.autorestart.AutoRestartService

object PixleHavenCore : Plugin() {

    override fun onEnable() {
        PixleHavenSettings.init()
        VeinminerSettings.init()
        VeinminerLimitService.init()
        VeinminerHook.init()
        GrindstoneRepairSettings.init()
        ChatSettings.init()
        NotificationSettings.init()
        NotificationService.init()
        ViewDistanceSettings.init()
        ViewDistanceService.init()
        SimpleMenuSettings.init()
        TrMenuLiteService.init()
        VanishSettings.init()
        VanishService.init()
        DeathDropSettings.init()
        HelpInterceptorSettings.init()
        HelpInterceptorService.init()
        AutoRestartSettings.init()
        AutoRestartService.init()
        logModulesStatus()
        info("Successfully running PixleHavenCore!")
    }

    private fun logModulesStatus() {
        info("=== PixleHavenCore 功能状态报告 ===")

        if (VeinminerSettings.enabled) {
            info("✓ 核心功能已启用: Veinminer (矿物连锁)")
        } else {
            info("✗ 核心功能已禁用: Veinminer (矿物连锁)")
        }

        if (GrindstoneRepairSettings.enabled) {
            info("✓ 核心功能已启用: Grindstone Repair (砂轮修复)")
        } else {
            info("✗ 核心功能已禁用: Grindstone Repair (砂轮修复)")
        }

        if (NotificationSettings.enabled) {
            info("✓ 核心功能已启用: Server Notification (服务器通知)")
        } else {
            info("✗ 核心功能已禁用: Server Notification (服务器通知)")
        }

        info("=== 优化模块状态 ===")
        if (ViewDistanceSettings.enabled) {
            info("✓ 优化已启用: View Distance Controller (视距控制)")
        } else {
            info("✗ 优化已禁用: View Distance Controller (视距控制)")
        }

        if (VanishSettings.enabled) {
            info("✓ 核心功能已启用: Vanish (隐身)")
        } else {
            info("✗ 核心功能已禁用: Vanish (隐身)")
        }

        if (DeathDropSettings.enabled) {
            info("✓ 核心功能已启用: Death Drop (危险世界死亡惩罚)")
        } else {
            info("✗ 核心功能已禁用: Death Drop (危险世界死亡惩罚)")
        }

        if (HelpInterceptorSettings.enabled) {
            info("✓ 核心功能已启用: Help Interceptor (帮助拦截)")
        } else {
            info("✗ 核心功能已禁用: Help Interceptor (帮助拦截)")
        }

        if (AutoRestartSettings.enabled) {
            info("✓ 核心功能已启用: Auto Restart (自动重启)")
        } else {
            info("✗ 核心功能已禁用: Auto Restart (自动重启)")
        }

        info("=== 插件初始化完成 ===")
    }
}
