package my.baas.controllers

import io.javalin.apibuilder.CrudHandler
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import io.javalin.openapi.*
import my.baas.config.AppContext
import my.baas.dto.SchemaModelCreateDto
import my.baas.dto.SchemaModelViewDto
import my.baas.models.SchemaModel
import my.baas.services.TableManagementService
import my.baas.services.TenantLimitService

object SchemaController : CrudHandler {

    @OpenApi(
        summary = "Create a new schema",
        operationId = "createSchema",
        path = "/api/schemas",
        methods = [HttpMethod.POST],
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
        
        val schemaCreateDto = ctx.bodyAsClass(SchemaModelCreateDto::class.java)
        val schema = schemaCreateDto.toModel()
        schema.save()
        
        // Create the corresponding table for this schema
        TableManagementService.createDataModelTable(schema)
        
        ctx.status(201).json(SchemaModelViewDto.fromModel(schema))
    }

    @OpenApi(
        summary = "Get a schema by ID",
        operationId = "getSchema",
        path = "/api/schemas/{id}",
        methods = [HttpMethod.GET],
        pathParams = [
            OpenApiParam("id", String::class, "The schema ID")
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
        ctx.json(SchemaModelViewDto.fromModel(schema))
    }

    @OpenApi(
        summary = "Get all schemas",
        operationId = "getAllSchemas",
        path = "/api/schemas",
        methods = [HttpMethod.GET],
        responses = [
            OpenApiResponse("200", description = "Schemas retrieved successfully")
        ],
        tags = ["Schemas"]
    )
    override fun getAll(ctx: Context) {
        val schemas = AppContext.db.find(SchemaModel::class.java).findList()
        val schemaDtos = schemas.map { SchemaModelViewDto.fromModel(it) }
        ctx.json(schemaDtos)
    }

    @OpenApi(
        summary = "Update a schema",
        operationId = "updateSchema",
        path = "/api/schemas/{id}",
        methods = [HttpMethod.PUT],
        pathParams = [
            OpenApiParam("id", String::class, "The schema ID")
        ],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(from = SchemaModelCreateDto::class)]),
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

        val schemaUpdateDto = ctx.bodyAsClass(SchemaModelCreateDto::class.java)
        
        // Store old indexed paths for comparison
        val oldIndexedPaths = schema.indexedJsonPaths
        
        schema.entityName = schemaUpdateDto.entityName
        schema.jsonSchema = schemaUpdateDto.jsonSchema
        schema.versionName = schemaUpdateDto.versionName
        schema.uniqueIdentifierFormatter = schemaUpdateDto.uniqueIdentifierFormatter
        schema.indexedJsonPaths = schemaUpdateDto.indexedJsonPaths
        schema.lifecycleScripts = schemaUpdateDto.lifecycleScripts
        schema.isValidationEnabled = schemaUpdateDto.isValidationEnabled
        schema.update()
        
        // Handle index changes if indexed paths have changed
        if (oldIndexedPaths != schemaUpdateDto.indexedJsonPaths) {
            TableManagementService.updateIndexes(schema, oldIndexedPaths, schemaUpdateDto.indexedJsonPaths)
        }
        
        ctx.json(SchemaModelViewDto.fromModel(schema))
    }

    @OpenApi(
        summary = "Delete a schema",
        operationId = "deleteSchema",
        path = "/api/schemas/{id}",
        methods = [HttpMethod.DELETE],
        pathParams = [
            OpenApiParam("id", String::class, "The schema ID")
        ],
        queryParams = [
            OpenApiParam("dropTable", Boolean::class, "Whether to drop the corresponding table (default: false)", required = false)
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
            schema.tenantId.let { tenantId ->
                TableManagementService.dropDataModelTable(tenantId, schema.entityName)
            }
        }
        
        schema.delete()
        ctx.status(204)
    }
}
