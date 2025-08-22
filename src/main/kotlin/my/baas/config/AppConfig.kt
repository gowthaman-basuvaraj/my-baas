package my.baas.config

import net.cactusthorn.config.core.Config
import net.cactusthorn.config.core.Default
import net.cactusthorn.config.core.Key
import java.util.*

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
    @Default("false")
    fun isRedisEnabled(): Boolean

    @Key("redis.host")
    fun redisHost(): Optional<String>

    @Key("redis.user")
    fun redisUser(): Optional<String>

    @Key("redis.port")
    fun redisPort(): Optional<Int>

    @Key("redis.password")
    fun redisPassword(): Optional<String>

    // Report configuration
    @Key("report.local.storage.path")
    @Default("/tmp/my-baas-reports")
    fun reportLocalStoragePath(): String

    @Key("report.max.concurrent.jobs")
    @Default("10")
    fun reportMaxConcurrentJobs(): Int

    @Key("report.job.timeout.minutes")
    @Default("5")
    fun reportJobTimeoutMinutes(): Long

    @Key("report.result.retention.days")
    @Default("10")
    fun reportResultRetentionDays(): Int

    @Key("report.enable.minio.upload")
    fun reportEnableMinioUpload(): Boolean

    @Key("report.minio.endpoint")
    fun reportMinioEndpoint(): Optional<String>

    @Key("report.minio.bucket.name")
    fun reportMinioBucketName(): Optional<String>

    @Key("report.minio.access.key")
    fun reportMinioAccessKey(): Optional<String>

    @Key("report.minio.secret.key")
    fun reportMinioSecretKey(): Optional<String>

    @Key("report.minio.region")
    fun reportMinioRegion(): Optional<String>

    @Key("report.minio.prefix")
    fun reportMinioPrefix(): Optional<String>

    @Key("report.minio.secure")
    @Default("true")
    fun reportMinioSecure(): Boolean

    // Email configuration
    @Key("email.smtp.host")
    fun emailSmtpHost(): Optional<String>

    @Key("email.smtp.port")
    @Default("587")
    fun emailSmtpPort(): Int

    @Key("email.smtp.username")
    fun emailSmtpUsername(): Optional<String>

    @Key("email.smtp.password")
    fun emailSmtpPassword(): Optional<String>

    @Key("email.smtp.auth")
    @Default("true")
    fun emailSmtpAuth(): Boolean

    @Key("email.smtp.starttls.enable")
    @Default("true")
    fun emailSmtpStartTlsEnable(): Boolean

    @Key("email.from.address")
    fun emailFromAddress(): Optional<String>

    @Key("email.from.name")
    @Default("MyBaaS Reports")
    fun emailFromName(): String

}
