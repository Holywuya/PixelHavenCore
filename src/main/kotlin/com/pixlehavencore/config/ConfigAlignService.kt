package com.pixlehavencore.config

import com.pixlehavencore.PixleHavenCore
import com.pixlehavencore.util.ArimResourceScanner
import org.bukkit.configuration.file.YamlConfiguration
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * 配置文件键对齐服务。
 *
 * 仅在插件 onEnable 时执行一次，reload 时不执行。
 *
 * 对齐规则：
 *   1. 服务器配置文件中存在但插件内置模板中不存在的键 → 删除（防止废弃配置污染）
 *   2. 插件内置模板中存在但服务器配置文件中不存在的键 → 补全（使用模板默认值）
 *   3. version 字段始终与插件内置模板同步
 *
 * 动态 section 白名单：
 *   某些配置文件中存在用户可自由扩展的 section（如权限组），插件模板只提供示例条目，
 *   用户可能自行添加更多。白名单内的 section 路径前缀下的所有键跳过删除和补全检查，
 *   以保护用户自定义数据不被覆盖或删除。
 *
 * 适用范围：settings.yml、feature/ 下的 yml 文件。
 */
object ConfigAlignService {

    /**
     * 动态 section 白名单：文件路径 → 白名单 section 前缀列表。
     * 白名单内的路径前缀下的所有子键在对齐时完全跳过（不删除、不补全）。
     *
     * 命名约定使用文件的资源路径（相对于 resources 根目录），
     * 例如 "feature/veinminer.yml"。
     */
    private val DYNAMIC_SECTIONS: Map<String, List<String>> = mapOf(
        // veinminer 权限组：用户可自行添加任意组，插件只提供示例
        "feature/veinminer.yml" to listOf("groups"),
        // chat 当前无动态 section
        "feature/chat/chat.yml" to emptyList(),
        // mob-drop 怪物掉落表：drops 下每个 key 是 EntityType，用户可自行添加/删除任意怪物
        "feature/mob-drop.yml" to listOf("drops"),
        // economy/tax 阶梯税率：用户可自由增删档位（tier1/tier2/...），插件只提供示例
        "feature/economy/tax.yml" to listOf("tax-brackets"),
        // grindstone repair 规则：用户可自由增删规则 key
        "feature/grindstone-repair.yml" to listOf("grindstoneRepair.rules"),
        // economy 货币定义：用户可自由增删货币类型
        "feature/economy/economy.yml" to listOf("currencies"),
        // crafting bench 的工作台映射、等级和专精均允许自由扩展
        "feature/crafting-bench/config.yml" to listOf("craftengine_blocks", "bench_tiers", "specializations", "queue.permission_limits"),
        // spawn-reducer 列表型配置，enabled-world 是管理员整体控制的列表，注册空白名单以便对齐发现
        "feature/optimization/spawn-reducer.yml" to emptyList(),
        // redstone-limiter 列表型配置，无嵌套动态 section，注册空白名单以便对齐发现
        "feature/optimization/redstone-limiter.yml" to emptyList()
    )

    /**
     * 判断某个键是否属于动态 section（白名单保护），
     * 即该键以任一白名单前缀开头（前缀 + "." 或完全相等）。
     */
    private fun isDynamicKey(resourcePath: String, key: String): Boolean {
        val prefixes = DYNAMIC_SECTIONS[resourcePath] ?: return false
        return prefixes.any { prefix ->
            key == prefix || key.startsWith("$prefix.")
        }
    }

    fun alignAll() {
        val resources = discoverManagedResources()
        var aligned = 0
        resources.forEach { resourcePath ->
            if (alignResource(resourcePath)) aligned++
        }
        if (aligned > 0) {
            info("[Config] 配置文件对齐完成，共处理 $aligned 个文件。")
        } else {
            info("[Config] 配置文件已全部对齐，无需修改。")
        }
    }

    /**
     * 对齐单个配置文件。
     * @return true 表示发生了修改并已保存
     */
    private fun alignResource(resourcePath: String): Boolean {
        val target = File(getDataFolder(), resourcePath)
        // 如果服务器上该文件不存在，由 ConfigMigrationService 负责释放，此处跳过
        if (!target.exists()) return false

        val template = loadResourceYaml(resourcePath) ?: return false
        val current = YamlConfiguration.loadConfiguration(target)

        val templateKeys = template.getKeys(true).toSet()
        val currentKeys = current.getKeys(true).toSet()

        var changed = false

        // 1. 删除服务器文件中有但模板中没有的键（叶子节点，避免误删父节点）
        //    白名单内的动态 section 下的键完全跳过，保护用户自定义数据
        val keysToRemove = currentKeys - templateKeys
        keysToRemove
            .filter { key -> !currentKeys.any { other -> other != key && other.startsWith("$key.") } }
            .filter { key -> !isDynamicKey(resourcePath, key) }
            .forEach { key ->
                current.set(key, null)
                changed = true
                warning("[Config] [$resourcePath] 删除废弃配置键: $key")
            }

        // 2. 补全模板中有但服务器文件中缺失的键
        //    白名单内的动态 section 下的键同样跳过，不强行写入模板示例条目
        //    注意：只补全叶子节点，且必须确认当前文件中该键确实不存在（防止误覆盖已有值）
        val keysToAdd = templateKeys - current.getKeys(true).toSet()
        keysToAdd
            .filter { key -> !isDynamicKey(resourcePath, key) }
            .forEach { key ->
                // 只设置叶子节点（不是某个子节点的父路径），避免覆盖已有的 section
                val isParent = templateKeys.any { other -> other != key && other.startsWith("$key.") }
                // 二次确认：若当前文件中该键已存在（值非 null），跳过，绝不覆盖用户已设置的值
                if (!isParent && current.get(key) == null) {
                    current.set(key, template.get(key))
                    changed = true
                    info("[Config] [$resourcePath] 补全缺失配置键: $key = ${template.get(key)}")
                }
            }

        // 3. 同步 version 字段
        val templateVersion = template.get("version")
        if (templateVersion != null && current.get("version") != templateVersion) {
            current.set("version", templateVersion)
            changed = true
            info("[Config] [$resourcePath] 更新配置版本: $templateVersion")
        }

        if (changed) {
            current.save(target)
        }
        return changed
    }

    private fun loadResourceYaml(resourcePath: String): YamlConfiguration? {
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath) ?: return null
        return stream.use { input ->
            InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
                YamlConfiguration.loadConfiguration(reader)
            }
        }
    }

    /**
     * 发现需要对齐的托管 YAML 资源路径（仅 settings.yml 和 feature/ 下的文件）。
     */
    private fun discoverManagedResources(): List<String> {
        val source = File(PixleHavenCore::class.java.protectionDomain.codeSource.location.toURI())
        return ArimResourceScanner.scanYamlFromCodeSource(source) { isManagedResource(it) }
    }

    private fun isManagedResource(path: String): Boolean {
        return (path == "settings.yml" || path.startsWith("feature/")) &&
            (path.endsWith(".yml") || path.endsWith(".yaml"))
    }
}
