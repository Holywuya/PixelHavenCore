package com.pixlehavencore.config

import com.pixlehavencore.PixleHavenCore
import com.pixlehavencore.util.ArimResourceScanner
import org.bukkit.configuration.file.YamlConfiguration
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.info
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object ConfigMigrationService {

    fun updateAll() {
        val resources = discoverYamlResources()
        resources.forEach { updateResource(it) }
    }

    private fun updateResource(resourcePath: String) {
        val target = File(getDataFolder(), resourcePath)
        target.parentFile?.mkdirs()

        val resourceStream = javaClass.classLoader.getResourceAsStream(resourcePath) ?: return
        resourceStream.use { input ->
            if (!target.exists()) {
                target.outputStream().use { output -> input.copyTo(output) }
                info("[Config] 已释放新配置文件: $resourcePath")
                return
            }
        }

        val defaults = loadResourceYaml(resourcePath) ?: return
        val current = YamlConfiguration.loadConfiguration(target)
        var changed = false

        defaults.getKeys(true).forEach { path ->
            if (!current.contains(path)) {
                current.set(path, defaults.get(path))
                changed = true
            }
        }

        val defaultVersion = defaults.get("version")
        if (defaultVersion != null && current.get("version") != defaultVersion) {
            current.set("version", defaultVersion)
            changed = true
        }

        if (changed) {
            current.save(target)
            info("[Config] 已补全配置文件缺失项: $resourcePath")
        }
    }

    private fun loadResourceYaml(resourcePath: String): YamlConfiguration? {
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath) ?: return null
        stream.use { input ->
            InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
                return YamlConfiguration.loadConfiguration(reader)
            }
        }
    }

    private fun discoverYamlResources(): List<String> {
        val source = File(PixleHavenCore::class.java.protectionDomain.codeSource.location.toURI())
        return ArimResourceScanner.scanYamlFromCodeSource(source) { isManagedYaml(it) }
    }

    private fun isManagedYaml(path: String): Boolean {
        return (path == "settings.yml" || path.startsWith("feature/")) &&
            (path.endsWith(".yml") || path.endsWith(".yaml"))
    }
}
