package com.pixlehavencore.feature.deathdrop

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object DeathDropSettings {

    @Config("feature/death-drop.yml")
    private lateinit var config: Configuration

    /** 模块总开关 */
    var enabled: Boolean = true
        private set

    /**
     * 触发惩罚的世界名称集合。
     * 默认包含末地（world_the_end）和下界（world_nether）。
     * 多世界插件中可填任意自定义世界名。
     */
    var worlds: Set<String> = setOf("world_the_end", "world_nether")
        private set

    /**
     * 物品掉落率下限（%，0–100）。
     * 每次死亡时在 [dropChanceMin, dropChanceMax] 范围内随机取一个掉落率，
     * 每件物品各自独立以此概率决定是否掉落到地面；
     * 未选中的物品会保留在玩家背包中。
     *
     * 将 min == max 可固定为确定值（无随机）。
     */
    var dropChanceMin: Double = 20.0
        private set

    /** 物品掉落率上限（%，0–100），须 >= dropChanceMin */
    var dropChanceMax: Double = 60.0
        private set

    /**
     * 拥有此权限的玩家不受死亡惩罚影响。
     * 留空则对所有玩家生效。
     */
    var exemptPermission: String = "phcore.deathdrop.bypass"
        private set

    /**
     * 玩家死亡后收到的提示消息。
     * 支持 & 颜色代码，以及以下占位符：
     *   {dropped} — 实际掉落的物品堆数
     *   {kept}    — 保留在背包中的物品堆数
     *   {rate}    — 本次死亡使用的掉落率（整数 %）
     * 留空则不发送任何消息。
     */
    var deathMessage: String = "&c你在危险维度死亡！掉落率 &e{rate}%&c，" +
            "&e{dropped} &c堆物品落地，&e{kept} &c堆物品保留在背包。"
        private set

    fun init() {
        reload()
    }

    fun reload() {
        config.reload()
        enabled = config.getBoolean("enabled", true)
        worlds = config.getStringList("worlds").toSet()

        val rawMin = config.getDouble("dropChance.min", 20.0).coerceIn(0.0, 100.0)
        val rawMax = config.getDouble("dropChance.max", 60.0).coerceIn(0.0, 100.0)
        // 保证 min <= max，防止配置填反
        dropChanceMin = minOf(rawMin, rawMax)
        dropChanceMax = maxOf(rawMin, rawMax)

        exemptPermission = config.getString("exemptPermission") ?: "phcore.deathdrop.bypass"
        deathMessage = config.getString("deathMessage") ?: ""
    }
}
