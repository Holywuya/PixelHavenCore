package com.pixlehavencore

import com.pixlehavencore.feature.base.killme.KillmeSettings
import com.pixlehavencore.feature.base.back.BackService
import com.pixlehavencore.feature.base.protection.ProtectionSettings

import com.pixlehavencore.feature.customcraft.CustomCraftService
import com.pixlehavencore.feature.deathdrop.DeathDropSettings
import com.pixlehavencore.feature.deathdrop.DeathDropUsageStorage
import com.pixlehavencore.feature.flight.FlightService
import com.pixlehavencore.feature.economy.EconomyProvider
import com.pixlehavencore.feature.grindstone.GrindstoneRepairSettings
import com.pixlehavencore.feature.keycommand.KeyCommandService
import com.pixlehavencore.feature.notification.NotificationService
import com.pixlehavencore.feature.industry.power.PowerService
import com.pixlehavencore.feature.optimization.entityclearer.EntityClearerService
import com.pixlehavencore.feature.optimization.viewdistance.ViewDistanceService
import com.pixlehavencore.feature.playerinv.PlayerInvService
import com.pixlehavencore.feature.mmhealthbar.MMHealthBarService
import com.pixlehavencore.feature.playerinfo.PlayerInfoService
import com.pixlehavencore.feature.trade.TradeService
import com.pixlehavencore.feature.vanish.VanishSettings
import com.pixlehavencore.feature.veinminer.VeinminerSettings
import com.pixlehavencore.feature.playtime.PlaytimeSettings
import com.pixlehavencore.feature.playtime.PlaytimeStorage
import com.pixlehavencore.feature.playtime.PlaytimeService
import com.pixlehavencore.util.ADMIN_PERMISSION
import com.pixlehavencore.util.msg
import com.pixlehavencore.util.requirePermission
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.warning
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@CommandHeader(name = "phc", aliases = ["phcore"], permissionDefault = PermissionDefault.TRUE)
object MainCommand {

    private val reloading = AtomicBoolean(false)

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.msg("<gold>=== PixleHavenCore 命令帮助 ===")
            sender.msg("<aqua>/phc reload <gray>- 重载所有模块配置")
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (!sender.requirePermission(ADMIN_PERMISSION)) {
                return@execute
            }
            if (!reloading.compareAndSet(false, true)) {
                sender.msg("<yellow>已有全局重载任务正在执行，请稍后再试。")
                return@execute
            }

            sender.msg("<gray>正在异步重载 PixleHavenCore 全部模块，请稍候...")
            submit(async = true) {
                val failed = runCatching { reloadAllModules() }.getOrElse { ex ->
                    warning("[MainCommand] /phc reload fatal: ${ex.stackTraceToString()}")
                    mutableListOf("global: ${ex.message ?: ex.javaClass.simpleName}")
                }
                submit {
                    if (failed.isEmpty()) {
                        sender.msg("<green>PixleHavenCore 全局重载完成。")
                    } else {
                        sender.msg("<yellow>全局重载已完成，但以下模块重载失败：")
                        failed.forEach { sender.msg("<red>- $it") }
                    }
                    reloading.set(false)
                }
            }
        }
    }

    private data class ReloadStep(
        val name: String,
        val runOnMainThread: Boolean,
        val action: () -> Unit
    )

    private fun runStep(step: ReloadStep, failed: MutableList<String>) {
        runCatching { step.action() }.onFailure { ex ->
            val reason = ex.message ?: ex.javaClass.simpleName
            failed += "${step.name}: $reason"
            warning("[MainCommand] /phc reload failed at ${step.name}: ${ex.stackTraceToString()}")
        }
    }

    private fun runMainThreadStep(step: ReloadStep, failed: MutableList<String>) {
        // 使用 CompletableFuture + submit 调度到主线程，
        // 避免与 TabooLib submit {} 的线程池争用导致潜在死锁
        val future = CompletableFuture<Unit>()
        submit {
            runCatching { step.action() }.onFailure { ex ->
                val reason = ex.message ?: ex.javaClass.simpleName
                failed += "${step.name}: $reason"
                warning("[MainCommand] /phc reload failed at ${step.name}: ${ex.stackTraceToString()}")
            }
            future.complete(Unit)
        }
        val completed = runCatching { future.get(30, TimeUnit.SECONDS) }.getOrDefault(null) != null
        if (!completed) {
            failed += "${step.name}: timeout"
            warning("[MainCommand] /phc reload timed out at ${step.name}")
        }
    }

    private fun runWrappedStep(step: ReloadStep, failed: MutableList<String>) {
        if (step.runOnMainThread) {
            runMainThreadStep(step, failed)
        } else {
            runStep(step, failed)
        }
    }

    private fun reloadAllModules(): List<String> {
        val failed = mutableListOf<String>()

        val steps = listOf(
            ReloadStep("settings", false) { PixleHavenSettings.reload() },
            ReloadStep("veinminer", false) { VeinminerSettings.init() },
            ReloadStep("grindstone", false) { GrindstoneRepairSettings.init() },
            ReloadStep("notification", true) { NotificationService.reload() },
            ReloadStep("view-distance", true) { ViewDistanceService.reload() },
            ReloadStep("entity-clearer", false) { EntityClearerService.reload() },
            ReloadStep("key-command", false) { KeyCommandService.reload() },
            ReloadStep("player-inv", false) { PlayerInvService.reload() },
            ReloadStep("trade", false) { TradeService.reload() },
            ReloadStep("vanish", false) { VanishSettings.init() },
            ReloadStep("death-drop", false) { DeathDropSettings.init() },
            ReloadStep("death-drop-usage", false) { DeathDropUsageStorage.init() },
            ReloadStep("economy", true) { EconomyProvider.reload() },
            ReloadStep("base-killme", false) { KillmeSettings.init() },
            ReloadStep("base-back", false) { BackService.reload() },
            ReloadStep("base-protection", false) { ProtectionSettings.init() },
            ReloadStep("playerinfo", false) { PlayerInfoService.reload() },
            ReloadStep("mm-healthbar", false) { MMHealthBarService.reload() },
            ReloadStep("playtime-settings", false) { PlaytimeSettings.reload() },
            ReloadStep("playtime-storage", false) { PlaytimeStorage.reload() },
            ReloadStep("playtime-service", false) { PlaytimeService.reload() },
            ReloadStep("flight", false) { FlightService.reload() },
            ReloadStep("customcraft", false) { CustomCraftService.reload() },
            ReloadStep("industry-power", false) { PowerService.reload() }
        )

        steps.forEach { step ->
            runWrappedStep(step, failed)
        }

        return failed
    }
}
