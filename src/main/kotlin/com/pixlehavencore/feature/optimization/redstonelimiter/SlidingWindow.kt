package com.pixlehavencore.feature.optimization.redstonelimiter

class SlidingWindow(private val windowMs: Long) {

    private val timestamps: java.util.LinkedList<Long> = java.util.LinkedList()

    var lastAccessTime: Long = 0L
        private set

    // 追加时间戳到队尾，O(1)
    @Synchronized
    fun record(now: Long) {
        timestamps.addLast(now)
        lastAccessTime = now
    }

    // 淘汰窗口外过期时间戳，返回窗口内频率（次/秒），均摊 O(1)
    @Synchronized
    fun getFrequency(now: Long): Double {
        val cutoff = now - windowMs
        while (timestamps.isNotEmpty() && timestamps.first() < cutoff) {
            timestamps.removeFirst()
        }
        return timestamps.size.toDouble() / (windowMs / 1000.0)
    }

    // 判定是否已过期（最后时间戳超出 expiryMs 无活动）
    @Synchronized
    fun isExpired(now: Long, expiryMs: Long): Boolean {
        return timestamps.isEmpty() || (now - lastAccessTime > expiryMs)
    }
}
