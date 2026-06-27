package com.pixlehavencore.feature.industry.power

import org.bukkit.Bukkit
import org.bukkit.Location
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submit
import java.util.concurrent.ConcurrentHashMap

object PowerService {

    private val pools = ConcurrentHashMap<String, EnergyPool>()
    private var tickTaskActive = false

    fun init() {
        PowerSettings.init()
        GeneratorRegistry.reload()
        DominionBridge.init()
        PowerStorage.init()

        if (!PowerSettings.enabled) {
            info("[Industry-Power] 模块已禁用")
            return
        }

        loadFromStorage()
        startTickTask()
        info("[Industry-Power] 已启动，已加载 ${pools.size} 个领地能量池")
    }

    fun reload() {
        stopTickTask()
        PowerSettings.reload()
        GeneratorRegistry.reload()
        loadFromStorage()
        startTickTask()
    }

    fun stop() {
        stopTickTask()
        PowerStorage.close()
        pools.clear()
    }

    private fun loadFromStorage() {
        val storedPools = PowerStorage.loadAllPools()
        for ((id, pool) in storedPools) {
            val generators = PowerStorage.loadGenerators(id, GeneratorRegistry)
            pool.generators.addAll(generators)
            recalculateCapacity(pool)
            pools[id] = pool
        }
    }

    private fun startTickTask() {
        tickTaskActive = true
        submit(async = true, period = 20) {
            if (!tickTaskActive) {
                cancel()
                return@submit
            }
            tick()
        }
    }

    private fun stopTickTask() {
        tickTaskActive = false
    }

    private var lastSaveTime = System.currentTimeMillis()

    private fun tick() {
        val now = System.currentTimeMillis()
        for ((_, pool) in pools) {
            val elapsedSeconds = ((now - pool.lastTickTime) / 1000.0).coerceAtMost(60.0)
            if (elapsedSeconds <= 0.0) continue
            pool.lastTickTime = now

            var totalGenerated = 0.0
            for (gen in pool.generators) {
                totalGenerated += gen.type.tick(pool) * elapsedSeconds
            }
            pool.energy = (pool.energy + totalGenerated).coerceIn(0.0, pool.capacity)
        }

        if (now - lastSaveTime >= 60000) {
            lastSaveTime = now
            submit(async = true) {
                PowerStorage.saveAllPools(pools.values)
            }
        }
    }

    fun getPool(dominionId: String): EnergyPool? = pools[dominionId]

    fun getAllPools(): Map<String, EnergyPool> = pools.toMap()

    fun getOrCreatePool(dominionId: String): EnergyPool {
        return pools.getOrPut(dominionId) {
            EnergyPool(dominionId = dominionId, capacity = PowerSettings.maxEnergyPerDominion)
        }
    }

    private fun recalculateCapacity(pool: EnergyPool) {
        pool.capacity = PowerSettings.maxEnergyPerDominion + pool.generators.sumOf { it.type.capacityContribution }
        if (pool.energy > pool.capacity) pool.energy = pool.capacity
    }

    fun addGenerator(dominionId: String, generatorType: GeneratorType, location: Location) {
        val pool = getOrCreatePool(dominionId)
        val record = GeneratorRecord(
            type = generatorType,
            world = location.world?.name ?: return,
            x = location.blockX,
            y = location.blockY,
            z = location.blockZ
        )
        pool.generators.add(record)
        recalculateCapacity(pool)
        PowerStorage.savePool(pool)
        PowerStorage.addGenerator(dominionId, generatorType.id, record.world, record.x, record.y, record.z)
    }

    fun removeGenerator(location: Location): Boolean {
        val world = location.world?.name ?: return false
        for ((_, pool) in pools) {
            val removed = pool.generators.removeAll { gen ->
                gen.world == world && gen.x == location.blockX && gen.y == location.blockY && gen.z == location.blockZ
            }
            if (removed) {
                recalculateCapacity(pool)
                PowerStorage.savePool(pool)
                PowerStorage.removeGenerator(world, location.blockX, location.blockY, location.blockZ)
                return true
            }
        }
        return false
    }
}
