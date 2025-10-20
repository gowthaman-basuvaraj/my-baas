package my.baas.services

import io.javalin.websocket.WsContext
import my.baas.config.AppContext.objectMapper
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

enum class EventType {
    CREATED,
    UPDATED,
    PATCHED,
    DELETED,
    MIGRATED
}

data class DataChangeEvent(
    val eventType: EventType,
    val entityName: String,
    val uniqueIdentifier: String,
    val versionName: String,
    val tenantId: UUID,
    val data: Map<String, Any>? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class SubscriptionMessage(
    val action: String = "noop", // "subscribe" or "unsubscribe"
    val entityName: String? = null,
    val uniqueIdentifier: String? = null
)

data class SubscriptionKey(
    val entityName: String,
    val uniqueIdentifier: String? = null
)

object WebSocketEventManager {

    private val logger = LoggerFactory.getLogger(WebSocketEventManager::class.java)

    // Map of tenant to WsContext to their subscriptions
    private val tenantClients =
        ConcurrentHashMap<UUID, ConcurrentHashMap<WsContext, CopyOnWriteArraySet<SubscriptionKey>>>()


    fun handleConnection(ctx: WsContext, tenantId: UUID) {

        tenantClients.computeIfAbsent(tenantId) { ConcurrentHashMap() }.putIfAbsent(ctx, CopyOnWriteArraySet())

        logger.debug("WebSocket connection established for tenant: [$tenantId]")

        ctx.send(
            objectMapper.writeValueAsString(
                mapOf(
                    "type" to "connected",
                    "message" to "WebSocket connection established",
                    "tenantId" to tenantId
                )
            )
        )
    }

    fun handleDisconnection(ctx: WsContext, tenantId: UUID) {
        // Remove all subscriptions for this client
        tenantClients[tenantId]?.remove(ctx)

        logger.debug("WebSocket connection closed and cleaned up for tenantId [$tenantId]")
    }

    fun handleMessage(ctx: WsContext, message: String, tenantId: UUID) {
        try {
            val subscriptionMessage = objectMapper.readValue(message, SubscriptionMessage::class.java)

            when (subscriptionMessage.action.lowercase()) {
                "subscribe" -> handleSubscribe(ctx, subscriptionMessage, tenantId)
                "unsubscribe" -> handleUnsubscribe(ctx, subscriptionMessage, tenantId)
                "ping" -> ctx.send(objectMapper.writeValueAsString(mapOf("type" to "pong")))
                else -> {
                    ctx.send(
                        objectMapper.writeValueAsString(
                            mapOf(
                                "type" to "error",
                                "message" to "Unknown action: ${subscriptionMessage.action}"
                            )
                        )
                    )
                }
            }
        } catch (e: Exception) {
            ctx.send(
                objectMapper.writeValueAsString(
                    mapOf(
                        "type" to "error",
                        "message" to "Invalid message format: ${e.message}"
                    )
                )
            )
        }
    }

    private fun handleSubscribe(ctx: WsContext, message: SubscriptionMessage, tenantId: UUID) {
        if (message.entityName == null) {
            ctx.send(
                objectMapper.writeValueAsString(
                    mapOf(
                        "type" to "error",
                        "message" to "entityName is required for subscription"
                    )
                )
            )
            return
        }

        val subscriptionKey = SubscriptionKey(message.entityName, message.uniqueIdentifier)

        tenantClients[tenantId]?.get(ctx)?.add(subscriptionKey)

        ctx.send(
            objectMapper.writeValueAsString(
                mapOf(
                    "type" to "subscribed",
                    "entityName" to message.entityName,
                    "uniqueIdentifier" to message.uniqueIdentifier,
                    "message" to "Successfully subscribed to events"
                )
            )
        )
    }

    private fun handleUnsubscribe(ctx: WsContext, message: SubscriptionMessage, tenantId: UUID) {
        if (message.entityName == null) {
            ctx.send(
                objectMapper.writeValueAsString(
                    mapOf(
                        "type" to "error",
                        "message" to "entityName is required for unsubscription"
                    )
                )
            )
            return
        }

        val subscriptionKey = SubscriptionKey(message.entityName, message.uniqueIdentifier)

        tenantClients[tenantId]?.get(ctx)?.remove(subscriptionKey)


        ctx.send(
            objectMapper.writeValueAsString(
                mapOf(
                    "type" to "unsubscribed",
                    "entityName" to message.entityName,
                    "uniqueIdentifier" to message.uniqueIdentifier,
                    "message" to "Successfully unsubscribed from events"
                )
            )
        )
    }

    fun publishEventToLocalClients(event: DataChangeEvent) {
        val eventJson = objectMapper.writeValueAsString(
            mapOf(
                "type" to "event",
                "eventType" to event.eventType.name,
                "entityName" to event.entityName,
                "uniqueIdentifier" to event.uniqueIdentifier,
                "versionName" to event.versionName,
                "data" to event.data,
                "timestamp" to event.timestamp
            )
        )

        listOf(
            SubscriptionKey(event.entityName),
            SubscriptionKey(event.entityName, event.uniqueIdentifier)
        ).forEach { subEvent ->

            tenantClients[event.tenantId]?.forEach { (ctx, subs) ->
                if (subs.contains(subEvent)) {
                    try {
                        ctx.send(eventJson)
                    } catch (e: Exception) {
                        logger.warn("exception in publishEventToLocalClients event", e)
                    }
                }
            }
        }

    }
}