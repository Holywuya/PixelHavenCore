package com.pixlehavencore.util

import kotlin.random.Random
import java.util.concurrent.ThreadLocalRandom

object RandomUtils {

    fun roll(chance: Double): Boolean {
        return ThreadLocalRandom.current().nextDouble() <= chance.coerceIn(0.0, 1.0)
    }

    fun nextInt(min: Int, max: Int): Int {
        val lower = min.coerceAtLeast(1)
        val upper = max.coerceAtLeast(lower)
        return if (upper <= lower) lower else ThreadLocalRandom.current().nextInt(lower, upper + 1)
    }

    fun nextDouble(min: Double, max: Double): Double {
        val lower = min
        val upper = max.coerceAtLeast(lower)
        return if (upper <= lower) lower else Random.nextDouble(lower, upper)
    }

    fun <T> weighted(items: List<T>, weight: (T) -> Double): T? {
        if (items.isEmpty()) return null
        val totalWeight = items.sumOf { weight(it) }
        if (totalWeight <= 0.0) return items.random()

        var rand = ThreadLocalRandom.current().nextDouble(totalWeight)
        for (item in items) {
            rand -= weight(item)
            if (rand <= 0.0) return item
        }
        return items.last()
    }
}
