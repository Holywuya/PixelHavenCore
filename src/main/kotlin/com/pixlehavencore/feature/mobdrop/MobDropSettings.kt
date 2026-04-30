package com.pixlehavencore.feature.mobdrop

import org.bukkit.entity.EntityType
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object MobDropSettings {

    @Config("feature/mob-drop.yml")
    private lateinit var config: Configuration

    var enabled: Boolean = true
        private set

    /** key = EntityType，value = 该实体的掉落配置（含 clearVanillaDrops 和规则列表） */
    var rules: Map<EntityType, EntityDropConfig> = emptyMap()
        private set

    /** key = MythicMob 内部 ID，value = 该 MM 怪物的掉落配置 */
    var mythicRules: Map<String, EntityDropConfig> = emptyMap()
        private set

    /** 抢夺等级 -> 掉落概率乘数（乘算，不是加算） */
    var lootingMultiplierByLevel: Map<Int, Double> = mapOf(0 to 1.0)
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        lootingMultiplierByLevel = loadMultiplierMap("looting-multiplier-by-level")

        val section = config.getConfigurationSection("drops")
        if (section == null) {
            rules = emptyMap()
            mythicRules = emptyMap()
            return
        }

        val loaded = mutableMapOf<EntityType, EntityDropConfig>()
        val loadedMythic = mutableMapOf<String, EntityDropConfig>()
        section.getKeys(false).forEach { key ->
            val entitySection = section.getConfigurationSection(key) ?: return@forEach
            val clearVanilla = entitySection.getBoolean("clearVanillaDrops", false)
            val lines = entitySection.getStringList("items")
            val parsed = lines.mapNotNull { parseItemRule(it) }
            val moneySection = entitySection.getConfigurationSection("money")
            val moneyLines = entitySection.getStringList("money")
            val config = EntityDropConfig(
                clearVanillaDrops = clearVanilla,
                rules = parsed,
                moneyRules = when {
                    moneyLines.isNotEmpty() -> moneyLines.mapNotNull { parseMoneyRule(it) }
                    moneySection != null && moneySection.getBoolean("enabled", false) -> listOf(
                        MoneyDropRule(
                            chanceExpression = moneySection.getDouble("chance", 1.0).toString(),
                            minExpression = moneySection.getDouble("min", 0.0).toString(),
                            maxExpression = moneySection.getDouble("max", 0.0).toString(),
                            mode = MoneyDropMode.fromConfig(moneySection.getString("mode")),
                            radius = moneySection.getDouble("radius", 8.0).coerceAtLeast(0.0),
                            split = moneySection.getBoolean("split", true),
                        )
                    )
                    else -> emptyList()
                }
            )

            val type = runCatching { EntityType.valueOf(key.uppercase()) }.getOrNull()
            if (type != null) {
                loaded[type] = config
            } else if (key.isNotBlank()) {
                loadedMythic[key.trim()] = config
            }
        }
        rules = loaded
        mythicRules = loadedMythic
    }

    fun resolveLootingMultiplier(level: Int): Double {
        if (level <= 0) return lootingMultiplierByLevel[0] ?: 1.0
        return lootingMultiplierByLevel[level]
            ?: lootingMultiplierByLevel.filterKeys { it <= level }.maxByOrNull { it.key }?.value
            ?: 1.0
    }

    private fun loadMultiplierMap(path: String): Map<Int, Double> {
        val section = config.getConfigurationSection(path) ?: return mapOf(0 to 1.0)
        val loaded = mutableMapOf<Int, Double>()
        section.getKeys(false).forEach { key ->
            val level = key.toIntOrNull() ?: return@forEach
            loaded[level.coerceAtLeast(0)] = section.getDouble(key, 1.0).coerceAtLeast(0.0)
        }
        if (loaded.isEmpty()) {
            loaded[0] = 1.0
        }
        loaded.putIfAbsent(0, 1.0)
        return loaded.toSortedMap()
    }

    private fun parseItemRule(line: String): MobDropRule? {
        val parts = line.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.size < 3) return null
        val spec = parts[0]
        val second = parts[1]
        val third = parts[2]
        return if (third.contains('-') && second.toDoubleOrNull() != null) {
            val range = third.split('-', limit = 2)
            val min = range.getOrNull(0)?.toIntOrNull() ?: return null
            val max = (range.getOrNull(1)?.toIntOrNull() ?: min).coerceAtLeast(min)
            MobDropRule(
                itemSpec = spec,
                chanceExpression = second,
                amountExpression = null,
                fixedAmountMin = min.coerceAtLeast(1),
                fixedAmountMax = max,
            )
        } else {
            MobDropRule(
                itemSpec = spec,
                chanceExpression = third,
                amountExpression = second,
                fixedAmountMin = null,
                fixedAmountMax = null,
            )
        }
    }

    private fun parseMoneyRule(line: String): MoneyDropRule? {
        val parts = line.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.size < 3) return null
        return MoneyDropRule(
            chanceExpression = parts[0],
            minExpression = parts[1],
            maxExpression = parts[2],
            mode = MoneyDropMode.fromConfig(parts.getOrNull(3)),
            radius = parts.getOrNull(4)?.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 8.0,
            split = parts.getOrNull(5)?.toBooleanStrictOrNull() ?: true,
        )
    }

    /** 单个怪物类型的掉落配置 */
    data class EntityDropConfig(
        val clearVanillaDrops: Boolean,
        val rules: List<MobDropRule>,
        val moneyRules: List<MoneyDropRule> = emptyList(),
    )

    data class MobDropRule(
        val itemSpec: String,
        val chanceExpression: String,
        val amountExpression: String?,
        val fixedAmountMin: Int?,
        val fixedAmountMax: Int?,
    )

    data class MoneyDropRule(
        val chanceExpression: String,
        val minExpression: String,
        val maxExpression: String,
        val mode: MoneyDropMode = MoneyDropMode.KILLER,
        val radius: Double = 8.0,
        val split: Boolean = true,
    )

    enum class MoneyDropMode(val configName: String) {
        KILLER("killer"),
        NEARBY("nearby");

        companion object {
            fun fromConfig(raw: String?): MoneyDropMode {
                return when (raw?.trim()?.lowercase()) {
                    "nearby", "range", "radius" -> NEARBY
                    else -> KILLER
                }
            }
        }
    }

    fun evaluateDouble(expression: String, level: Int): Double? {
        return FormulaEvaluator(expression, level).parse()
    }

    fun resolveChance(expression: String, level: Int): Double {
        return (evaluateDouble(expression, level) ?: 0.0).coerceIn(0.0, 1.0)
    }

    fun resolveAmount(rule: MobDropRule, level: Int): Int {
        val fixedMin = rule.fixedAmountMin
        val fixedMax = rule.fixedAmountMax
        if (fixedMin != null && fixedMax != null) {
            return (if (fixedMax <= fixedMin) fixedMin else kotlin.random.Random.nextInt(fixedMin, fixedMax + 1)).coerceAtLeast(1)
        }
        val evaluated = evaluateDouble(rule.amountExpression ?: "1", level) ?: 1.0
        return kotlin.math.round(evaluated).toInt().coerceAtLeast(1)
    }

    private class FormulaEvaluator(expression: String, level: Int) {
        private val input = expression.replace("{level}", level.toString()).replace(" ", "")
        private var index = 0

        fun parse(): Double? {
            val value = parseExpression() ?: return null
            return if (index == input.length) value else null
        }

        private fun parseExpression(): Double? {
            var value = parseTerm() ?: return null
            while (index < input.length) {
                when (input[index]) {
                    '+' -> {
                        index++
                        value += parseTerm() ?: return null
                    }
                    '-' -> {
                        index++
                        value -= parseTerm() ?: return null
                    }
                    else -> return value
                }
            }
            return value
        }

        private fun parseTerm(): Double? {
            var value = parseFactor() ?: return null
            while (index < input.length) {
                when (input[index]) {
                    '*' -> {
                        index++
                        value *= parseFactor() ?: return null
                    }
                    '/' -> {
                        index++
                        val divisor = parseFactor() ?: return null
                        if (divisor == 0.0) return null
                        value /= divisor
                    }
                    else -> return value
                }
            }
            return value
        }

        private fun parseFactor(): Double? {
            if (index >= input.length) return null
            if (input[index] == '(') {
                index++
                val value = parseExpression() ?: return null
                if (index >= input.length || input[index] != ')') return null
                index++
                return value
            }
            val start = index
            if (input[index] == '+' || input[index] == '-') {
                index++
            }
            while (index < input.length && (input[index].isDigit() || input[index] == '.')) {
                index++
            }
            if (start == index) return null
            return input.substring(start, index).toDoubleOrNull()
        }
    }
}
