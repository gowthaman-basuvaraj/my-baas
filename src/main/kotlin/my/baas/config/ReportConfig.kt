package my.baas.config

import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.nio.file.Paths

data class ReportConfig(
    val localStoragePath: Path,
    val maxConcurrentJobs: Int,
    val jobTimeoutMinutes: Long,
    val resultRetentionDays: Int,
    val enableMinioUpload: Boolean,
    val minioConfig: MinioConfig,
    val emailConfig: EmailConfig
) {

    companion object {
        private val logger = LoggerFactory.getLogger("ReportConfig")
        fun fromAppConfig(appConfig: AppConfig): ReportConfig {
            val minioConfig = try {
                if (appConfig.reportEnableMinioUpload()) {
                    MinioConfig.Present(
                        endpoint = appConfig.reportMinioEndpoint()
                            .orElseThrow { IllegalArgumentException("Endpoint config is invalid") },
                        bucketName = appConfig.reportMinioBucketName()
                            .orElseThrow { IllegalArgumentException("Bucket name is invalid") },
                        accessKey = appConfig.reportMinioAccessKey()
                            .orElseThrow { IllegalArgumentException("Access key is invalid") },
                        secretKey = appConfig.reportMinioSecretKey()
                            .orElseThrow { IllegalArgumentException("Secret key is invalid") },
                        region = appConfig.reportMinioRegion()
                            .orElseThrow { IllegalArgumentException("Region key is invalid") },
                        prefix = appConfig.reportMinioPrefix()
                            .orElseThrow { IllegalArgumentException("Prefix key is invalid") },
                        secure = appConfig.reportMinioSecure()
                    )
                } else MinioConfig.Empty

            } catch (e: IllegalArgumentException) {
                logger.warn("Unable to construct MinIO Config from {}", appConfig, e)
                MinioConfig.Empty
            }
            val emailConfig = if (appConfig.emailSmtpHost().isPresent && appConfig.emailFromAddress().isPresent) {
                EmailConfig.Present(
                    smtpHost = appConfig.emailSmtpHost().get(),
                    smtpPort = appConfig.emailSmtpPort(),
                    username = appConfig.emailSmtpUsername().orElse(null),
                    password = appConfig.emailSmtpPassword().orElse(null),
                    auth = appConfig.emailSmtpAuth(),
                    startTlsEnable = appConfig.emailSmtpStartTlsEnable(),
                    fromAddress = appConfig.emailFromAddress().get(),
                    fromName = appConfig.emailFromName()
                )
            } else EmailConfig.Empty

            return ReportConfig(
                localStoragePath = Paths.get(appConfig.reportLocalStoragePath()),
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

sealed class MinioConfig {
    data class Present(
        val endpoint: String,
        val bucketName: String,
        val accessKey: String,
        val secretKey: String,
        val region: String,
        val prefix: String,
        val secure: Boolean
    ) : MinioConfig()

    data object Empty : MinioConfig()
}

sealed class EmailConfig {
    data class Present(
        val smtpHost: String,
        val smtpPort: Int,
        val username: String?,
        val password: String?,
        val auth: Boolean,
        val startTlsEnable: Boolean,
        val fromAddress: String,
        val fromName: String
    ) : EmailConfig()

    data object Empty : EmailConfig()
}