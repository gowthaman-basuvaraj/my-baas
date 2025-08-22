package my.baas.config

import java.nio.file.Path
import java.nio.file.Paths

data class ReportConfig(
    val localStoragePath: String,
    val maxConcurrentJobs: Int,
    val jobTimeoutMinutes: Long,
    val resultRetentionDays: Int,
    val enableMinioUpload: Boolean,
    val minioConfig: MinioConfig?,
    val emailConfig: EmailConfig?
) {
    fun getLocalStoragePathAsPath(): Path = Paths.get(localStoragePath)
    
    companion object {
        fun fromAppConfig(appConfig: AppConfig): ReportConfig {
            val minioConfig = if (appConfig.reportEnableMinioUpload()) {
                MinioConfig(
                    endpoint = appConfig.reportMinioEndpoint().orElseThrow { IllegalArgumentException("Endpoint config is invalid") },
                    bucketName = appConfig.reportMinioBucketName().orElseThrow { IllegalArgumentException("Bucket name is invalid") },
                    accessKey = appConfig.reportMinioAccessKey().orElseThrow { IllegalArgumentException("Access key is invalid") },
                    secretKey = appConfig.reportMinioSecretKey().orElseThrow { IllegalArgumentException("Secret key is invalid") },
                    region = appConfig.reportMinioRegion().orElseThrow { IllegalArgumentException("Region key is invalid") },
                    prefix = appConfig.reportMinioPrefix().orElseThrow { IllegalArgumentException("Prefix key is invalid") },
                    secure = appConfig.reportMinioSecure()
                )
            } else null

            val emailConfig = if (appConfig.emailSmtpHost().isPresent && appConfig.emailFromAddress().isPresent) {
                EmailConfig(
                    smtpHost = appConfig.emailSmtpHost().get(),
                    smtpPort = appConfig.emailSmtpPort(),
                    username = appConfig.emailSmtpUsername().orElse(null),
                    password = appConfig.emailSmtpPassword().orElse(null),
                    auth = appConfig.emailSmtpAuth(),
                    startTlsEnable = appConfig.emailSmtpStartTlsEnable(),
                    fromAddress = appConfig.emailFromAddress().get(),
                    fromName = appConfig.emailFromName()
                )
            } else null
            
            return ReportConfig(
                localStoragePath = appConfig.reportLocalStoragePath(),
                maxConcurrentJobs = appConfig.reportMaxConcurrentJobs(),
                jobTimeoutMinutes = appConfig.reportJobTimeoutMinutes(),
                resultRetentionDays = appConfig.reportResultRetentionDays(),
                enableMinioUpload = appConfig.reportEnableMinioUpload(),
                minioConfig = minioConfig,
                emailConfig = emailConfig
            )
        }
    }
}

data class MinioConfig(
    val endpoint: String,
    val bucketName: String,
    val accessKey: String,
    val secretKey: String,
    val region: String,
    val prefix: String,
    val secure: Boolean
)

data class EmailConfig(
    val smtpHost: String,
    val smtpPort: Int,
    val username: String?,
    val password: String?,
    val auth: Boolean,
    val startTlsEnable: Boolean,
    val fromAddress: String,
    val fromName: String
)