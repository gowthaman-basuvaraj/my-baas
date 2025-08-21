package my.baas.config

import my.baas.auth.CurrentUserProvider
import my.baas.auth.CurrentTenantProvider
import com.fasterxml.jackson.databind.MappingJsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.ebean.Database
import io.ebean.DatabaseFactory
import io.ebean.config.DatabaseConfig
import io.ebean.config.TenantMode
import io.ebean.datasource.DataSourceConfig
import net.cactusthorn.config.core.factory.ConfigFactory

object AppContext {

    val appConfig: AppConfig by lazy {
        ConfigFactory.builder().build().create(AppConfig::class.java)
    }

    val db: Database by lazy {
        DatabaseFactory.create(DatabaseConfig().also { dc ->
            dc.setDataSourceConfig(DataSourceConfig().also {
                it.username = appConfig.dbUsername()
                it.password = appConfig.dbPassword()
                it.url = appConfig.dbUrl()
            })
            dc.objectMapper = objectMapper
            dc.jsonFactory = MappingJsonFactory(objectMapper)
            dc.currentUserProvider = CurrentUserProvider()// this will populate who created field for every db table
            dc.currentTenantProvider = CurrentTenantProvider()// this will populate tenant filtering
            dc.isRunMigration = true
            dc.isDefaultServer = true
            dc.tenantMode = TenantMode.PARTITION
        })
    }

    val objectMapper: ObjectMapper by lazy {
        ObjectMapper().registerModule(KotlinModule.Builder().build())
    }

}
