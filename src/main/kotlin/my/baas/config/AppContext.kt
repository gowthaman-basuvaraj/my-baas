package my.baas.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MappingJsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ebean.Database
import io.ebean.DatabaseBuilder
import io.ebean.DatabaseFactory
import io.ebean.config.DatabaseConfig
import io.ebean.config.TenantMode
import io.ebean.datasource.DataSourceConfig
import my.baas.auth.CurrentTenantProvider
import my.baas.auth.CurrentUserProvider
import my.baas.models.ReportModel
import my.baas.services.completion.CompletionActionProcessor
import my.baas.services.completion.EmailProcessor
import my.baas.services.completion.S3UploadProcessor
import my.baas.services.completion.SftpUploadProcessor
import net.cactusthorn.config.core.factory.ConfigFactory

object AppContext {

    val appConfig: AppConfig by lazy {
        ConfigFactory.builder().build().create(AppConfig::class.java)
    }

    fun dataSourceConfig(): DatabaseBuilder {
        return DatabaseConfig().also { dc ->
            dc.setDataSourceConfig(DataSourceConfig().also {
                it.username = appConfig.dbUsername()
                it.password = appConfig.dbPassword()
                it.url = appConfig.dbUrl()
            })
            dc.objectMapper = objectMapper
            dc.jsonFactory = MappingJsonFactory(objectMapper)
            dc.currentUserProvider = CurrentUserProvider()
            dc.isRunMigration = true
            dc.isDefaultServer = true
        }
    }

    val db: Database by lazy {
        DatabaseFactory.create(dataSourceConfig().also {
            it.currentTenantProvider(CurrentTenantProvider())
            it.tenantMode(TenantMode.PARTITION)
        })
    }

    //admin DB does not have tenant-related information
    //in the future, where a scenario will arise, there is a DB per tenant, we will handle it at that time
    val adminDatabase: Database by lazy {
        DatabaseFactory.create(dataSourceConfig())
    }

    val objectMapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        findAndRegisterModules()
    }
    val reportConfig: ReportConfig by lazy {
        ReportConfig.fromAppConfig(appConfig)
    }

    val completionActionProcessors: Map<ReportModel.ActionType, CompletionActionProcessor<*>> by lazy {
        mapOf(
            ReportModel.ActionType.S3 to S3UploadProcessor(),
            ReportModel.ActionType.SFTP to SftpUploadProcessor(),
            ReportModel.ActionType.EMAIL to EmailProcessor(reportConfig.emailConfig)
        )
    }

}
