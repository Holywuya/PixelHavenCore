package com.pixlehavencore.util

import java.io.File
import java.util.zip.ZipFile

/**
 * 统一资源 YAML 扫描入口。
 *
 * - JAR 运行态：遍历 zip entry
 * - 开发目录态：通过 Arim FolderReader 扫描
 */
object ArimResourceScanner {

    fun scanYamlFromCodeSource(source: File, include: (String) -> Boolean): List<String> {
        return if (source.isFile) {
            runCatching {
                ZipFile(source).use { zip ->
                    zip.entries().asSequence()
                        .map { it.name }
                        .filter { include(it) }
                        .sorted()
                        .toList()
                }
            }.getOrElse { ex ->
                taboolib.common.platform.function.warning("[ArimResourceScanner] 读取 JAR 文件失败: ${ex.message}")
                emptyList()
            }
        } else {
            scanYamlFromDirectory(source)
                .filter(include)
                .sorted()
        }
    }

    private fun scanYamlFromDirectory(root: File): List<String> {
        val results = mutableListOf<String>()
        ArimFolderUtils.walkYaml(
            root,
            filter = {
                isFile && (
                    extension.equals("yml", true) ||
                        extension.equals("yaml", true)
                    )
            }
        ) {
            val yamlFile = file ?: return@walkYaml
            results += yamlFile.relativeTo(root).invariantSeparatorsPath
        }
        return results
    }
}
