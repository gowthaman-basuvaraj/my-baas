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

    // Report configuration
    @Key("report.local.storage.path")
    fun reportLocalStoragePath(): String

    @Key("report.max.concurrent.jobs")
    fun reportMaxConcurrentJobs(): Int

    @Key("report.job.timeout.minutes")
    fun reportJobTimeoutMinutes(): Long

    @Key("report.result.retention.days")
    fun reportResultRetentionDays(): Int

    @Key("report.enable.minio.upload")
    fun reportEnableMinioUpload(): Boolean

    @Key("report.minio.endpoint")
    fun reportMinioEndpoint(): String

    @Key("report.minio.bucket.name")
    fun reportMinioBucketName(): String

    @Key("report.minio.access.key")
    fun reportMinioAccessKey(): String

    @Key("report.minio.secret.key")
    fun reportMinioSecretKey(): String

    @Key("report.minio.region")
    fun reportMinioRegion(): String

    @Key("report.minio.prefix")
    fun reportMinioPrefix(): String

    @Key("report.minio.secure")
    fun reportMinioSecure(): Boolean

}
