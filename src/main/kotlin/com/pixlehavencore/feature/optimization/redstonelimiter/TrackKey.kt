package com.pixlehavencore.feature.optimization.redstonelimiter

data class TrackKey(
    val worldName: String,
    val x: Int,
    val y: Int,
    val z: Int,
) {
    // 使用 intern() 复用世界名字符串实例，减少内存占用并加速 hashCode
    constructor(worldName: String, x: Int, y: Int, z: Int, intern: Boolean) : this(
        if (intern) worldName.intern() else worldName,
        x, y, z
    )
}
