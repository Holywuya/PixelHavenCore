package com.pixlehavencore.util

import taboolib.module.configuration.Configuration
import taboolib.module.configuration.Type
import top.maplex.arim.tools.folderreader.readFolderWalkConfig
import top.maplex.arim.tools.folderreader.releaseResourceFolderAndRead
import java.io.File

/**
 * 统一封装 Arim FolderReader，避免各模块重复实现文件夹遍历逻辑。
 */
object ArimFolderUtils {

    fun walkYaml(folder: File, filter: File.() -> Boolean = { true }, action: Configuration.() -> Unit) {
        readFolderWalkConfig(folder) {
            setReadType(Type.YAML)
            addFilter(filter)
            walk(action)
        }
    }

    fun releaseAndWalkYaml(resourcePath: String, filter: File.() -> Boolean = { true }, action: Configuration.() -> Unit) {
        releaseResourceFolderAndRead(resourcePath) {
            setReadType(Type.YAML)
            addFilter(filter)
            walk(action)
        }
    }
}
