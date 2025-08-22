package my.baas.controllers

import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import my.baas.config.AppContext
import my.baas.models.TenantConfiguration
import my.baas.models.TenantModel

object TenantController {

    fun create(ctx: Context) {
        val tenant = ctx.bodyAsClass(TenantModel::class.java)

        // Check if the domain already exists
        val existingTenant = AppContext.adminDatabase.find(TenantModel::class.java)
            .where()
            .eq("domain", tenant.domain)
            .findOne()

        if (existingTenant != null) {
            throw BadRequestResponse("Tenant with domain '${tenant.domain}' already exists")
        }

        // Validate configuration
        validateTenantConfiguration(tenant.config)

        tenant.save()
        ctx.status(201).json(tenant)
    }

    fun getOne(ctx: Context) {
        val tenantId = ctx.pathParam("id").toLongOrNull()
            ?: throw BadRequestResponse("Invalid tenant ID")

        val tenant = AppContext.adminDatabase.find(TenantModel::class.java, tenantId)
            ?: throw NotFoundResponse("Tenant not found")

        ctx.json(tenant)
    }

    fun getAll(ctx: Context) {
        val page = ctx.queryParam("page")?.toIntOrNull() ?: 1
        val pageSize = ctx.queryParam("pageSize")?.toIntOrNull() ?: 20

        val tenants = AppContext.adminDatabase.find(TenantModel::class.java)
            .setFirstRow((page - 1) * pageSize)
            .setMaxRows(pageSize)
            .findPagedList()

        ctx.json(tenants)
    }

    fun update(ctx: Context) {
        val tenantId = ctx.pathParam("id").toLongOrNull()
            ?: throw BadRequestResponse("Invalid tenant ID")

        val tenant = AppContext.adminDatabase.find(TenantModel::class.java, tenantId)
            ?: throw NotFoundResponse("Tenant not found")

        val updatedTenant = ctx.bodyAsClass(TenantModel::class.java)

        // Check if new domain conflicts with existing tenant
        if (tenant.domain != updatedTenant.domain) {
            val domainExists = AppContext.adminDatabase.find(TenantModel::class.java)
                .where()
                .eq("domain", updatedTenant.domain)
                .ne("id", tenantId)
                .exists()

            if (domainExists) {
                throw BadRequestResponse("Domain '${updatedTenant.domain}' is already in use")
            }
        }

        // Validate configuration if provided
        validateTenantConfiguration(updatedTenant.config)
        
        tenant.name = updatedTenant.name
        tenant.domain = updatedTenant.domain
        tenant.isActive = updatedTenant.isActive
        tenant.allowedIps = updatedTenant.allowedIps
        tenant.config = updatedTenant.config
        tenant.settings = updatedTenant.settings
        tenant.update()

        ctx.json(tenant)
    }

    fun delete(ctx: Context) {
        val tenantId = ctx.pathParam("id").toLongOrNull()
            ?: throw BadRequestResponse("Invalid tenant ID")

        val tenant = AppContext.adminDatabase.find(TenantModel::class.java, tenantId)
            ?: throw NotFoundResponse("Tenant not found")

        tenant.delete()
        ctx.status(204)
    }

    fun activate(ctx: Context) {
        val tenantId = ctx.pathParam("id").toLongOrNull()
            ?: throw BadRequestResponse("Invalid tenant ID")

        val tenant = AppContext.adminDatabase.find(TenantModel::class.java, tenantId)
            ?: throw NotFoundResponse("Tenant not found")

        tenant.isActive = true
        tenant.update()

        ctx.json(mapOf("message" to "Tenant activated successfully", "tenant" to tenant))
    }

    fun deactivate(ctx: Context) {
        val tenantId = ctx.pathParam("id").toLongOrNull()
            ?: throw BadRequestResponse("Invalid tenant ID")

        val tenant = AppContext.adminDatabase.find(TenantModel::class.java, tenantId)
            ?: throw NotFoundResponse("Tenant not found")

        tenant.isActive = false
        tenant.update()

        ctx.json(mapOf("message" to "Tenant deactivated successfully", "tenant" to tenant))
    }

    private fun validateTenantConfiguration(config: TenantConfiguration) {
        if (config.maxSchemas <= 0) {
            throw BadRequestResponse("maxSchemas must be greater than 0")
        }
        if (config.maxReports <= 0) {
            throw BadRequestResponse("maxReports must be greater than 0")
        }
        if (config.jobRetentionDays <= 0) {
            throw BadRequestResponse("jobRetentionDays must be greater than 0")
        }
        if (config.maxReportExecutionTimeMinutes <= 0) {
            throw BadRequestResponse("maxReportExecutionTimeMinutes must be greater than 0")
        }
        
        // Validate JWKS URI format if provided
        config.jwksUri?.let { jwksUri ->
            if (!jwksUri.startsWith("https://") && !jwksUri.startsWith("http://")) {
                throw BadRequestResponse("jwksUri must be a valid HTTP or HTTPS URL")
            }
        }
    }

}