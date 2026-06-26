package com.pixlehavencore.util

import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.warning
import taboolib.common.util.supplierLazy
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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

    private val writeMethod = supplierLazy<Unit, Method> { _ ->
        nbtIoClass[Unit].methods.firstOrNull {
            it.name == "writeCompressed" && it.parameterTypes.size == 2 && it.parameterTypes[1] == java.io.OutputStream::class.java
        } ?: error("找不到 NbtIo.writeCompressed")
    }

    private val listTagClass = supplierLazy<Unit, Class<*>> {
        Class.forName("net.minecraft.nbt.ListTag")
    }

    private val compoundTagClass = supplierLazy<Unit, Class<*>> {
        Class.forName("net.minecraft.nbt.CompoundTag")
    }

    private val saveOptionalMethod = supplierLazy<Unit, Method> { _ ->
        itemStackClass[Unit].methods.firstOrNull {
            it.name == "saveOptional" && it.parameterTypes.size == 1
        } ?: error("找不到 ItemStack.saveOptional")
    }

    private val asNMSCopyMethod = supplierLazy<Unit, Method> { _ ->
        craftItemStackClass[Unit].getMethod("asNMSCopy", org.bukkit.inventory.ItemStack::class.java)
    }

    private val tagToString = supplierLazy<Unit, Method> { _ ->
        listTagClass[Unit].getMethod("add", Any::class.java)
    }

    private val compoundPutMethod = supplierLazy<Unit, Method> { _ ->
        compoundTagClass[Unit].getMethod("put", String::class.java, Any::class.java)
    }

    private val compoundPutByteMethod = supplierLazy<Unit, Method> { _ ->
        compoundTagClass[Unit].getMethod("putByte", String::class.java, Byte::class.javaPrimitiveType)
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

    fun save(player: OfflinePlayer, inventory: Array<ItemStack?>?, enderChest: Array<ItemStack?>?): Boolean {
        val playerDataFile = resolvePlayerDataFile(player) ?: return false
        val registryAccess = captureRegistryAccess() ?: return false
        return saveToFile(playerDataFile, registryAccess, player.uniqueId, inventory, enderChest)
    }

    private fun saveToFile(
        playerDataFile: File,
        registryAccess: Any,
        playerId: UUID,
        inventory: Array<ItemStack?>?,
        enderChest: Array<ItemStack?>?
    ): Boolean {
        if (!playerDataFile.exists()) return false

        return runCatching {
            val rootTag = FileInputStream(playerDataFile).use { stream ->
                readMethod[Unit].invoke(null, stream, unlimitedAccounter[Unit])
            }

            if (inventory != null) {
                val invList = buildItemListTag(inventory, registryAccess, isEnderChest = false)
                compoundPutMethod[Unit].invoke(rootTag, "Inventory", invList)
            }

            if (enderChest != null) {
                val ecList = buildItemListTag(enderChest, registryAccess, isEnderChest = true)
                compoundPutMethod[Unit].invoke(rootTag, "EnderItems", ecList)
            }

            FileOutputStream(playerDataFile).use { stream ->
                writeMethod[Unit].invoke(null, rootTag, stream)
            }
            true
        }.onFailure { ex ->
            warning("[OfflineInventory] 保存离线玩家数据失败($playerId): ${ex.message}")
        }.getOrDefault(false)
    }

    private fun buildItemListTag(items: Array<ItemStack?>, registryAccess: Any, isEnderChest: Boolean): Any {
        val listTag = listTagClass[Unit].getDeclaredConstructor().newInstance()
        for (index in items.indices) {
            val item = items[index] ?: continue
            val nmsItem = asNMSCopyMethod[Unit].invoke(null, item) ?: continue
            val optionalTag = saveOptionalMethod[Unit].invoke(nmsItem, registryAccess)
            val compoundMethod = optionalTag?.javaClass?.getMethod("orElse", Any::class.java) ?: continue
            val tag = compoundMethod.invoke(optionalTag, null) ?: continue
            if (!compoundTagClass[Unit].isInstance(tag)) continue
            val slot = denormalizeSlot(index, isEnderChest)
            if (slot == -1) continue
            compoundPutByteMethod[Unit].invoke(tag, "Slot", slot.toByte())
            tagToString[Unit].invoke(listTag, tag)
        }
        return listTag
    }

    private fun denormalizeSlot(index: Int, isEnderChest: Boolean): Int {
        if (isEnderChest) return if (index in 0..26) index else -1
        return when (index) {
            in 0..35 -> index
            36 -> 100
            37 -> 101
            38 -> 102
            39 -> 103
            40 -> -106
            else -> -1
        }
    }

    fun resolvePlayerDataFile(player: OfflinePlayer): File? {
        val primaryWorld = Bukkit.getWorlds().firstOrNull { it.environment.name == "NORMAL" } ?: Bukkit.getWorlds().firstOrNull()
            ?: return null
        return File(primaryWorld.worldFolder, "playerdata/${player.uniqueId}.dat")
    }
}
