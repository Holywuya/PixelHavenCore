package com.pixlehavencore.feature.chat

import com.pixlehavencore.PixleHavenSettings
import com.pixlehavencore.util.ArimJsonUtils
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.warning
import taboolib.expansion.AlkaidRedis
import taboolib.expansion.SingleRedisConnection
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Redis 跨服聊天服务（按 SimpleChat 语义迁移）。
 */
object SimpleChatRedisService {

    private val stateLock = Any()

    @Volatile
    private var shuttingDown = false

    @Volatile
    private var connection: SingleRedisConnection? = null

    private val started = AtomicBoolean(false)

    fun initAsync() {
        if (!started.compareAndSet(false, true)) {
            return
        }
        shuttingDown = false
        submit(async = true) {
            synchronized(stateLock) {
                initInternal()
            }
        }
    }

    fun reloadAsync() {
        shuttingDown = false
        submit(async = true) {
            synchronized(stateLock) {
                shutdownInternal()
                initInternal()
            }
        }
    }

    fun shutdown() {
        shuttingDown = true
        started.set(false)
        synchronized(stateLock) {
            shutdownInternal()
        }
    }

    fun publishIfEnabled(senderName: String, serialized: String) {
        if (!SimpleChatSettings.redisEnabled) {
            return
        }
        val payload = if (SimpleChatSettings.redisClearClick) {
            SimpleChatRedisFilterService.filterSerializedComponent(serialized)
        } else {
            serialized
        }
        if (shuttingDown) {
            return
        }
        submit(async = true) {
            val currentConnection = synchronized(stateLock) { connection } ?: return@submit
            runCatching {
                currentConnection.publish(channelName(), "${SimpleChatSettings.redisServerId}:$payload")
            }.onFailure { ex ->
                warning("[SimpleChat] 发布跨服消息失败: ${ex.message}")
            }
        }
    }

    private fun initInternal() {
        if (!SimpleChatSettings.redisEnabled) {
            return
        }
        runCatching {
            val connector = AlkaidRedis.create()
                .host(SimpleChatSettings.redisHost)
                .port(SimpleChatSettings.redisPort)
                .connect(PixleHavenSettings.redisConnect)
                .timeout(PixleHavenSettings.redisTimeout)
                .reconnectDelay(1000L)

            val user = PixleHavenSettings.redisUser.trim()
            val pass = SimpleChatSettings.redisPassword.trim().ifBlank { PixleHavenSettings.redisPassword.trim() }
            if (user.isNotBlank()) {
                connector.auth(user)
            }
            if (pass.isNotBlank()) {
                connector.pass(pass)
            }

            val createdConnection = connector.connect().connection()
            createdConnection.get("phcore:chat:ping")

            if (shuttingDown) {
                createdConnection.close()
                return
            }

            connection = createdConnection
            subscribe()

            info("[SimpleChat] Redis 已连接: ${SimpleChatSettings.redisHost}:${SimpleChatSettings.redisPort}")
        }.onFailure { ex ->
            warning("[SimpleChat] Redis 初始化失败: ${ex.message}")
            shutdownInternal()
        }
    }

    private fun shutdownInternal() {
        runCatching { connection?.close() }
        connection = null
    }

    private fun subscribe() {
        val currentConnection = connection ?: return
        submit(async = true) {
            runCatching {
                currentConnection.subscribe(channelName(), patternMode = false) {
                    if (shuttingDown) {
                        close()
                    } else {
                        handleIncoming(message)
                    }
                }
            }.onFailure { ex ->
                warning("[SimpleChat] 订阅 Redis 频道失败: ${ex.message}")
            }
        }
    }

    private fun channelName(): String {
        val db = SimpleChatSettings.redisDatabase
        return if (db <= 0) {
            SimpleChatSettings.redisChannel
        } else {
            "db${db}:${SimpleChatSettings.redisChannel}"
        }
    }

    private fun handleIncoming(payload: String) {
        val idx = payload.indexOf(':')
        if (idx <= 0 || idx >= payload.length - 1) {
            return
        }
        val senderServer = payload.substring(0, idx)
        if (senderServer.equals(SimpleChatSettings.redisServerId, true)) {
            return
        }
        val serialized = payload.substring(idx + 1)
        val component = runCatching { GsonComponentSerializer.gson().deserialize(serialized) }.getOrNull() ?: return
        // Folia: handleCrossServerMessage 内部已正确使用 submitOnEntity 调度，无需额外 submit 包裹
        SimpleChatListener.handleCrossServerMessage(component, senderServer)
    }
}

private object SimpleChatRedisFilterService {

    fun filterSerializedComponent(serialized: String): String {
        val json = ArimJsonUtils.parseTree(serialized) ?: return serialized
        sanitizeNode(json)
        return json.toString()
    }

    private fun sanitizeNode(node: com.google.gson.JsonElement) {
        if (node.isJsonObject) {
            val obj = node.asJsonObject

            val clickEvent = obj.getAsJsonObject("clickEvent")
            if (clickEvent != null) {
                val action = clickEvent.get("action")?.asString?.lowercase().orEmpty()
                val keep = action == "open_url" || action == "copy_to_clipboard"
                if (!keep) {
                    obj.remove("clickEvent")
                }
            }

            val hoverEvent = obj.getAsJsonObject("hoverEvent")
            if (hoverEvent != null) {
                val value = hoverEvent.get("value")
                if (value != null && containsIgnoreKeyword(value.toString())) {
                    if (!containsWhitelistKeyword(value.toString())) {
                        obj.remove("hoverEvent")
                    }
                }
            }

            obj.entrySet().forEach { (_, child) -> sanitizeNode(child) }
        } else if (node.isJsonArray) {
            node.asJsonArray.forEach { sanitizeNode(it) }
        }
    }

    private fun containsIgnoreKeyword(text: String): Boolean {
        return SimpleChatSettings.redisIgnoreKeywords.any { kw ->
            kw.isNotBlank() && text.contains(kw, ignoreCase = true)
        }
    }

    private fun containsWhitelistKeyword(text: String): Boolean {
        return SimpleChatSettings.redisWhitelistKeywords.any { kw ->
            kw.isNotBlank() && text.contains(kw, ignoreCase = true)
        }
    }
}
