package my.baas.services

import my.baas.config.AppConfig
import my.baas.config.AppContext
import my.baas.config.AppContext.objectMapper
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPooled
import redis.clients.jedis.JedisPubSub
import kotlin.concurrent.thread

object RedisEventPublisher {

    private val logger = LoggerFactory.getLogger(RedisEventPublisher::class.java)
    private const val EVENTS_CHANNEL = "mybaas:events"

    private val appConfig: AppConfig = AppContext.appConfig
    private val password = appConfig.redisPassword()

    private val jedisPool: JedisPooled by lazy {
        if (password.isNullOrBlank()) {
            JedisPooled(appConfig.redisHost(), appConfig.redisPort())
        } else {
            JedisPooled(appConfig.redisHost(), appConfig.redisPort(), "", password)
        }
    }
    private var isInitialized = false

    fun initialize() {
        if (isInitialized || !appConfig.isRedisEnabled()) {
            return
        }

        try {

            // Start subscriber in background thread
            startSubscriber()

            isInitialized = true
            logger.info("Redis event publisher initialized successfully")
        } catch (e: Exception) {
            logger.error("Failed to initialize Redis: ${e.message}", e)
        }
    }

    fun publishEvent(event: DataChangeEvent) {
        if (!isInitialized) {
            return
        }

        try {
            val eventJson = objectMapper.writeValueAsString(event)
            jedisPool.publish(EVENTS_CHANNEL, eventJson)
            logger.debug("Published event to Redis: {} for {}", event.eventType, event.entityName)
        } catch (e: Exception) {
            logger.error("Failed to publish event to Redis: ${e.message}", e)
        }
    }

    private fun startSubscriber() {


        thread(start = true, isDaemon = true) {
            try {
                jedisPool.subscribe(object : JedisPubSub() {
                    override fun onMessage(channel: String?, message: String?) {
                        if (channel == EVENTS_CHANNEL && message != null) {
                            try {
                                val event = objectMapper.readValue(message, DataChangeEvent::class.java)
                                // Forward to local WebSocket clients
                                WebSocketEventManager.publishEventToLocalClients(event)
                                logger.debug("Processed Redis event: {} for {}", event.eventType, event.entityName)
                            } catch (e: Exception) {
                                logger.error("Failed to process Redis event message: ${e.message}", e)
                            }
                        }
                    }

                    override fun onSubscribe(channel: String?, subscribedChannels: Int) {
                        logger.info("Subscribed to Redis channel: $channel")
                    }

                    override fun onUnsubscribe(channel: String?, subscribedChannels: Int) {
                        logger.info("Unsubscribed from Redis channel: $channel")
                    }

                    override fun onPSubscribe(pattern: String?, subscribedChannels: Int) {
                        // Not used
                    }

                    override fun onPUnsubscribe(pattern: String?, subscribedChannels: Int) {
                        // Not used
                    }

                    override fun onPMessage(pattern: String?, channel: String?, message: String?) {
                        // Not used
                    }
                }, EVENTS_CHANNEL)

            } catch (e: Exception) {
                logger.error("Redis subscriber error: ${e.message}", e)
                // Try to reconnect after a delay
                Thread.sleep(5000)
                if (isInitialized) {
                    startSubscriber()
                }
            }
        }
    }


    fun isRedisEnabled(): Boolean = appConfig.isRedisEnabled() && isInitialized

}