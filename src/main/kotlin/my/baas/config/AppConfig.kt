package my.baas.config

import net.cactusthorn.config.core.Config
import net.cactusthorn.config.core.Key
import kotlin.random.Random

@Config
interface AppConfig {

    @Key("database.url")
    fun dbUrl(): String

    @Key("database.username")
    fun dbUsername(): String

    @Key("database.password")
    fun dbPassword(): String

    @Key("auth.wellKnownUrl")
    fun wellKnownUrl(): String

    @Key("redis.enabled")
    fun isRedisEnabled(): Boolean

    @Key("redis.host")
    fun redisHost(): String

    @Key("redis.port")
    fun redisPort(): Int

    @Key("redis.password")
    fun redisPassword(): String?

}
