package my.baas.services

import io.javalin.http.BadRequestResponse
import my.baas.auth.CurrentUser
import my.baas.config.AppContext
import my.baas.models.ReportModel
import my.baas.models.SchemaModel
import my.baas.models.TenantModel
import org.slf4j.LoggerFactory

object TenantLimitService {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun validateSchemaCreation() {
        val tenant = getCurrentTenant()
        val currentSchemaCount = AppContext.db.find(SchemaModel::class.java)
            .findCount()

        if (currentSchemaCount >= tenant.config.maxSchemas) {
            logger.warn("Schema limit exceeded for tenant: ${tenant.domain}. Maximum allowed: ${tenant.config.maxSchemas}, current: $currentSchemaCount")
            throw BadRequestResponse("Schema limit exceeded. Maximum allowed: ${tenant.config.maxSchemas}, current: $currentSchemaCount")
        }
    }

    fun validateReportCreation() {
        val tenant = getCurrentTenant()
        val currentReportCount = AppContext.db.find(ReportModel::class.java)
            .findCount()

        if (currentReportCount >= tenant.config.maxReports) {
            logger.warn("Report limit exceeded for tenant: ${tenant.domain}. Maximum allowed: ${tenant.config.maxReports}, current: $currentReportCount")
            throw BadRequestResponse("Report limit exceeded. Maximum allowed: ${tenant.config.maxReports}, current: $currentReportCount")
        }
    }


    private fun getCurrentTenant(): TenantModel {
        val currentUser = CurrentUser.get()

        return currentUser.tenant
            ?: run {
                logger.warn("No tenant associated with user: ${currentUser.userId}")
                throw BadRequestResponse("No tenant associated with user")
            }
    }
}