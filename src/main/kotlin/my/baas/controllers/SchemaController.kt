package my.baas.controllers

import io.javalin.apibuilder.CrudHandler
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import io.javalin.openapi.*
import my.baas.auth.CurrentUser
import my.baas.config.AppContext
import my.baas.dto.SchemaModelCreateDto
import my.baas.dto.SchemaModelUpdateDto
import my.baas.dto.SchemaModelViewDto
import my.baas.models.ApplicationModel
import my.baas.models.SchemaModel
import my.baas.services.TableManagementService
import my.baas.services.TenantLimitService

object SchemaController : CrudHandler {

    @OpenApi(
        summary = "Create a new schema",
        operationId = "createSchema",
        path = "/api/app/{applicationName}/schemas",
        methods = [HttpMethod.POST],
        pathParams = [
            OpenApiParam(name = "applicationName", type = String::class, description = "The application name", required = true)
        ],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(from = SchemaModelCreateDto::class)]),
        responses = [
            OpenApiResponse("201", description = "Schema created successfully"),
            OpenApiResponse("400", description = "Bad request or tenant limit exceeded")
        ],
        tags = ["Schemas"]
    )
    override fun create(ctx: Context) {
        // Validate tenant limits before creating schema
        TenantLimitService.validateSchemaCreation()
        
        // Get application from path
        val applicationName = ctx.pathParam("applicationName")
        val application = AppContext.db.find(ApplicationModel::class.java)
            .where()
            .eq("applicationName", applicationName)
            .findOne()
            ?: throw NotFoundResponse("Application not found")
        
        // Set application context
        CurrentUser.setApplicationContext(application.id, application.applicationName)

        val schemaCreateDto = ctx.bodyAsClass(SchemaModelCreateDto::class.java)
        val schema = schemaCreateDto.toModel()
        schema.application = application
        schema.save()

        // Create the corresponding table for this schema
        TableManagementService.createDataModelTable(schema)

        ctx.status(201).json(SchemaModelViewDto.fromModel(schema))
    }

    @OpenApi(
        summary = "Get a schema by ID",
        operationId = "getSchema",
        path = "/api/app/{applicationName}/schemas/{id}",
        methods = [HttpMethod.GET],
        pathParams = [
            OpenApiParam(name = "applicationName", type = String::class, description = "The application name", required = true),
            OpenApiParam(name = "id", type = String::class, description = "The schema ID", required = true)
        ],
        responses = [
            OpenApiResponse("200", description = "Schema retrieved successfully"),
            OpenApiResponse("404", description = "Schema not found")
        ],
        tags = ["Schemas"]
    )
    override fun getOne(ctx: Context, resourceId: String) {

        val schema = AppContext.db.find(SchemaModel::class.java, resourceId)
            ?: throw NotFoundResponse("Schema not found")
        ctx.status(200).json(SchemaModelViewDto.fromModel(schema))
    }

    @OpenApi(
        summary = "Get all schemas",
        operationId = "getAllSchemas",
        path = "/api/app/{applicationName}/schemas",
        methods = [HttpMethod.GET],
        pathParams = [
            OpenApiParam(name = "applicationName", type = String::class, description = "The application name", required = true)
        ],
        responses = [
            OpenApiResponse("200", description = "Schemas retrieved successfully")
        ],
        tags = ["Schemas"]
    )
    override fun getAll(ctx: Context) {
        // Get application from path
        val applicationName = ctx.pathParam("applicationName")
        val application = AppContext.db.find(ApplicationModel::class.java)
            .where()
            .eq("applicationName", applicationName)
            .findOne()
            ?: throw NotFoundResponse("Application not found")
        
        // Set application context
        CurrentUser.setApplicationContext(application.id, application.applicationName)
        
        val schemas = AppContext.db.find(SchemaModel::class.java)
            .where()
            .eq("application_id", application.id)
            .findList()
        val schemaDtos = schemas.map { SchemaModelViewDto.fromModel(it) }
        ctx.status(200).json(schemaDtos)
    }

    @OpenApi(
        summary = "Update a schema",
        operationId = "updateSchema",
        path = "/api/app/{applicationName}/schemas/{id}",
        methods = [HttpMethod.PUT],
        pathParams = [
            OpenApiParam(name = "applicationName", type = String::class, description = "The application name", required = true),
            OpenApiParam(name = "id", type = String::class, description = "The schema ID", required = true)
        ],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(from = SchemaModelUpdateDto::class)]),
        responses = [
            OpenApiResponse("200", description = "Schema updated successfully"),
            OpenApiResponse("400", description = "Bad request"),
            OpenApiResponse("404", description = "Schema not found")
        ],
        tags = ["Schemas"]
    )
    override fun update(ctx: Context, resourceId: String) {
        val schema = AppContext.db.find(SchemaModel::class.java, resourceId)
            ?: throw NotFoundResponse("Schema not found")
        val tenantId = CurrentUser.getTenant()?.id ?: throw IllegalStateException("No tenant in context")

        val schemaUpdateDto = ctx.bodyAsClass(SchemaModelUpdateDto::class.java)

        // Store old indexed paths for comparison
        val oldIndexedPaths = schema.indexedJsonPaths

        schema.jsonSchema = schemaUpdateDto.jsonSchema
        schema.versionName = schemaUpdateDto.versionName
        schema.indexedJsonPaths = schemaUpdateDto.indexedJsonPaths
        schema.lifecycleScripts = schemaUpdateDto.lifecycleScripts
        schema.isValidationEnabled = schemaUpdateDto.isValidationEnabled
        schema.update()

        // Handle index changes if indexed paths have changed
        if (oldIndexedPaths != schemaUpdateDto.indexedJsonPaths) {
            TableManagementService.updateIndexes(schema, oldIndexedPaths, schemaUpdateDto.indexedJsonPaths, tenantId)
        }

        ctx.status(200).json(SchemaModelViewDto.fromModel(schema))
    }

    @OpenApi(
        summary = "Delete a schema",
        operationId = "deleteSchema",
        path = "/api/app/{applicationName}/schemas/{id}",
        methods = [HttpMethod.DELETE],
        pathParams = [
            OpenApiParam(name = "applicationName", type = String::class, description = "The application name", required = true),
            OpenApiParam(name = "id", type = String::class, description = "The schema ID", required = true)
        ],
        queryParams = [
            OpenApiParam(
                name = "dropTable",
                type = Boolean::class,
                description = "Whether to drop the corresponding table (default: false)",
                required = false
            )
        ],
        responses = [
            OpenApiResponse("204", description = "Schema deleted successfully"),
            OpenApiResponse("404", description = "Schema not found")
        ],
        tags = ["Schemas"]
    )
    override fun delete(ctx: Context, resourceId: String) {
        val schema = AppContext.db.find(SchemaModel::class.java, resourceId)
            ?: throw NotFoundResponse("Schema not found")

        // Drop the corresponding table only if explicitly requested
        val dropTable = ctx.queryParam("dropTable")?.toBoolean() ?: false
        if (dropTable) {
            schema.tenant.let { tenant ->
                val applicationId = schema.application.id
                TableManagementService.dropDataModelTable(tenant.id, applicationId, schema.entityName)
            }
        }

        schema.delete()
        ctx.status(204)
    }
}
