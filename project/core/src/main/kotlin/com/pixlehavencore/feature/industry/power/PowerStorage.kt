package com.pixlehavencore.feature.industry.power

import com.pixlehavencore.util.DatabaseUtils
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection

object PowerStorage {

    private var dataSource: HikariDataSource? = null

    fun init() {
        dataSource = DatabaseUtils.newHikariDataSource("industry_power", maxPoolSize = 2, minIdle = 1)
        createTables()
    }

    fun close() {
        dataSource?.close()
        dataSource = null
    }

    private fun createTables() {
        execute("""
            CREATE TABLE IF NOT EXISTS industry_power_pool (
                dominion_id TEXT PRIMARY KEY,
                energy REAL NOT NULL DEFAULT 0,
                capacity REAL NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL DEFAULT 0
            )
        """)
        execute("""
            CREATE TABLE IF NOT EXISTS industry_power_generator (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                dominion_id TEXT NOT NULL,
                generator_type TEXT NOT NULL,
                world TEXT NOT NULL,
                x INTEGER NOT NULL,
                y INTEGER NOT NULL,
                z INTEGER NOT NULL,
                UNIQUE(world, x, y, z)
            )
        """)
    }

    fun loadAllPools(): Map<String, EnergyPool> {
        val pools = mutableMapOf<String, EnergyPool>()
        query("SELECT dominion_id, energy, capacity FROM industry_power_pool") { rs ->
            val pool = EnergyPool(
                dominionId = rs.getString("dominion_id"),
                energy = rs.getDouble("energy"),
                capacity = rs.getDouble("capacity")
            )
            pools[pool.dominionId] = pool
        }
        return pools
    }

    fun loadGenerators(dominionId: String, registry: GeneratorRegistry): List<GeneratorRecord> {
        val records = mutableListOf<GeneratorRecord>()
        query("SELECT generator_type, world, x, y, z FROM industry_power_generator WHERE dominion_id = ?", dominionId) { rs ->
            val type = registry.get(rs.getString("generator_type"))
            if (type != null) {
                records.add(GeneratorRecord(
                    type = type,
                    world = rs.getString("world"),
                    x = rs.getInt("x"),
                    y = rs.getInt("y"),
                    z = rs.getInt("z")
                ))
            }
        }
        return records
    }

    fun savePool(pool: EnergyPool) {
        execute("""
            INSERT OR REPLACE INTO industry_power_pool (dominion_id, energy, capacity, updated_at)
            VALUES (?, ?, ?, ?)
        """, pool.dominionId, pool.energy, pool.capacity, System.currentTimeMillis())
    }

    fun saveAllPools(pools: Collection<EnergyPool>) {
        val ds = dataSource ?: return
        ds.connection.use { conn ->
            conn.autoCommit = false
            val stmt = conn.prepareStatement(
                "INSERT OR REPLACE INTO industry_power_pool (dominion_id, energy, capacity, updated_at) VALUES (?, ?, ?, ?)"
            )
            pools.forEach { pool ->
                stmt.setString(1, pool.dominionId)
                stmt.setDouble(2, pool.energy)
                stmt.setDouble(3, pool.capacity)
                stmt.setLong(4, System.currentTimeMillis())
                stmt.addBatch()
            }
            stmt.executeBatch()
            conn.commit()
        }
    }

    fun addGenerator(dominionId: String, generatorTypeId: String, world: String, x: Int, y: Int, z: Int) {
        execute("""
            INSERT INTO industry_power_generator (dominion_id, generator_type, world, x, y, z)
            VALUES (?, ?, ?, ?, ?, ?)
        """, dominionId, generatorTypeId, world, x, y, z)
    }

    fun removeGenerator(world: String, x: Int, y: Int, z: Int) {
        execute("DELETE FROM industry_power_generator WHERE world = ? AND x = ? AND y = ? AND z = ?", world, x, y, z)
    }

    private fun execute(sql: String, vararg params: Any) {
        val ds = dataSource ?: return
        ds.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                params.forEachIndexed { i, p -> stmt.setObject(i + 1, p) }
                stmt.executeUpdate()
            }
        }
    }

    private fun query(sql: String, vararg params: Any, block: (java.sql.ResultSet) -> Unit) {
        val ds = dataSource ?: return
        ds.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                params.forEachIndexed { i, p -> stmt.setObject(i + 1, p) }
                stmt.executeQuery().use { rs ->
                    while (rs.next()) block(rs)
                }
            }
        }
    }
}
