package com.pixlehavencore.feature.industry.power

data class EnergyPool(
    val dominionId: String,
    var energy: Double = 0.0,
    var capacity: Double = 0.0,
    val generators: MutableList<GeneratorRecord> = mutableListOf(),
    var lastTickTime: Long = System.currentTimeMillis()
)

data class GeneratorRecord(
    val type: GeneratorType,
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int
)

interface GeneratorType {
    val id: String
    val displayName: String
    val generatePerSecond: Double
    val capacityContribution: Double
    fun tick(pool: EnergyPool): Double
}

class PassiveGenerator(
    override val id: String,
    override val displayName: String,
    override val generatePerSecond: Double,
    override val capacityContribution: Double
) : GeneratorType {
    override fun tick(pool: EnergyPool): Double = generatePerSecond
}

class FuelGenerator(
    override val id: String,
    override val displayName: String,
    override val generatePerSecond: Double,
    override val capacityContribution: Double
) : GeneratorType {
    override fun tick(pool: EnergyPool): Double {
        return 0.0 // 燃料系统由后续子模块实现
    }
}
