package com.pixlehavencore.util

import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.warning
import taboolib.common.util.supplierLazy
import java.io.File
import java.io.FileInputStream
import java.lang.reflect.Method
import java.util.UUID

object OfflineInventoryUtils {

    data class Snapshot(
        val inventory: Array<ItemStack?>,
        val enderChest: Array<ItemStack?>
    )

    private val nbtAccounterClass = supplierLazy<Unit, Class<*>> {
        Class.forName("net.minecraft.nbt.NbtAccounter")
    }

    private val unlimitedAccounter = supplierLazy<Unit, Any> {
        nbtAccounterClass[Unit].getMethod("unlimitedHeap").invoke(null)
    }

    private val nbtIoClass = supplierLazy<Unit, Class<*>> {
        Class.forName("net.minecraft.nbt.NbtIo")
    }

    private val readMethod = supplierLazy<Unit, Method> { _ ->
        nbtIoClass[Unit].methods.firstOrNull {
            it.name == "readCompressed" && it.parameterTypes.size == 2
        } ?: error("找不到 NbtIo.readCompressed")
    }

    private val itemStackClass = supplierLazy<Unit, Class<*>> {
        Class.forName("net.minecraft.world.item.ItemStack")
    }

    private val parseMethod = supplierLazy<Unit, Method> { _ ->
        itemStackClass[Unit].methods.firstOrNull {
            it.name == "parseOptional" && it.parameterTypes.size == 2
        } ?: error("找不到 ItemStack.parseOptional")
    }

    private val craftItemStackClass = supplierLazy<Unit, Class<*>> {
        Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack")
    }

    private val asBukkitCopy = supplierLazy<Unit, Method> { _ ->
        craftItemStackClass[Unit].getMethod("asBukkitCopy", itemStackClass[Unit])
    }

    /**
     * Folia 线程安全警告：此方法通过 NMS 反射访问服务器内部（CraftServer.getServer()、NbtIo、ItemStack.parseOptional），
     * 在 Folia 多线程环境下不完全安全。调用方必须确保在全局区域调度器（submit {}）内调用，
     * 或接受潜在的线程安全风险。离线玩家数据读取本身是只读操作，风险较低。
     */
    fun load(player: OfflinePlayer): Snapshot? {
        val playerDataFile = resolvePlayerDataFile(player) ?: return null
        val registryAccess = captureRegistryAccess() ?: return null
        return load(playerDataFile, registryAccess, player.uniqueId)
    }

    fun load(playerDataFile: File, registryAccess: Any, playerId: UUID): Snapshot? {
        if (!playerDataFile.exists()) return null

        return runCatching {
            val rootTag = FileInputStream(playerDataFile).use { stream ->
                readMethod[Unit].invoke(null, stream, unlimitedAccounter[Unit])
            }

            val inventory = parseItemList(rootTag, "Inventory", 41, registryAccess)
            val enderChest = parseItemList(rootTag, "EnderItems", 27, registryAccess)
            Snapshot(inventory = inventory, enderChest = enderChest)
        }.onFailure { ex ->
            warning("[OfflineInventory] 读取离线玩家数据失败($playerId): ${ex.message}")
        }.getOrNull()
    }

    fun captureRegistryAccess(): Any? {
        return runCatching {
            val server = Bukkit.getServer()
            val craftServerClass = server.javaClass
            val dedicatedServer = craftServerClass.getMethod("getServer").invoke(server)
            dedicatedServer.javaClass.getMethod("registryAccess").invoke(dedicatedServer)
        }.onFailure { ex ->
            warning("[OfflineInventory] 准备离线数据读取上下文失败: ${ex.message}")
        }.getOrNull()
    }

    private fun parseItemList(rootTag: Any, key: String, size: Int, registryAccess: Any): Array<ItemStack?> {
        val compoundClass = rootTag.javaClass
        val listTag = compoundClass.getMethod("getList", String::class.java, Int::class.javaPrimitiveType).invoke(rootTag, key, 10) ?: return arrayOfNulls(size)
        val listClass = listTag.javaClass
        val listSize = (listClass.getMethod("size").invoke(listTag) as? Int) ?: return arrayOfNulls(size)
        val result = arrayOfNulls<ItemStack>(size)

        for (index in 0 until listSize) {
            val entry = listClass.getMethod("getCompound", Int::class.javaPrimitiveType).invoke(listTag, index) ?: continue
            val slotByte = (entry.javaClass.getMethod("getByte", String::class.java).invoke(entry, "Slot") as? Byte) ?: continue
            val slot = normalizeSlot(slotByte.toInt())
            if (slot !in result.indices) continue
            val nmsItem = parseMethod[Unit].invoke(null, registryAccess, entry) ?: continue
            val bukkitItem = asBukkitCopy[Unit].invoke(null, nmsItem) as? ItemStack ?: continue
            result[slot] = bukkitItem
        }
        return result
    }

    private fun normalizeSlot(raw: Int): Int {
        return when (raw) {
            in 0..35 -> raw
            100 -> 36
            101 -> 37
            102 -> 38
            103 -> 39
            -106, 150 -> 40
            else -> -1  // 返回 -1 表示无效的 slot
        }
    }

    fun resolvePlayerDataFile(player: OfflinePlayer): File? {
        val primaryWorld = Bukkit.getWorlds().firstOrNull { it.environment.name == "NORMAL" } ?: Bukkit.getWorlds().firstOrNull()
            ?: return null
        return File(primaryWorld.worldFolder, "playerdata/${player.uniqueId}.dat")
    }
}
