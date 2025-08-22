package my.baas.config

import java.nio.file.Path
import java.nio.file.Paths

data class ReportConfig(
    val localStoragePath: String,
    val maxConcurrentJobs: Int,
    val jobTimeoutMinutes: Long,
    val resultRetentionDays: Int,
    val enableMinioUpload: Boolean,
    val minioConfig: MinioConfig?
) {
    fun getLocalStoragePathAsPath(): Path = Paths.get(localStoragePath)
    
    companion object {
        fun fromAppConfig(appConfig: AppConfig): ReportConfig {
            val minioConfig = if (appConfig.reportEnableMinioUpload()) {
                MinioConfig(
                    endpoint = appConfig.reportMinioEndpoint(),
                    bucketName = appConfig.reportMinioBucketName(),
                    accessKey = appConfig.reportMinioAccessKey(),
                    secretKey = appConfig.reportMinioSecretKey(),
                    region = appConfig.reportMinioRegion(),
                    prefix = appConfig.reportMinioPrefix(),
                    secure = appConfig.reportMinioSecure()
                )
            } else null
            
            return ReportConfig(
                localStoragePath = appConfig.reportLocalStoragePath(),
                maxConcurrentJobs = appConfig.reportMaxConcurrentJobs(),
                jobTimeoutMinutes = appConfig.reportJobTimeoutMinutes(),
                resultRetentionDays = appConfig.reportResultRetentionDays(),
                enableMinioUpload = appConfig.reportEnableMinioUpload(),
                minioConfig = minioConfig
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