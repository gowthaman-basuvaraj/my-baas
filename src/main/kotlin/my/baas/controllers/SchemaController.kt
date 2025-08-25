package my.baas.controllers

import io.javalin.apibuilder.CrudHandler
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import my.baas.config.AppContext
import my.baas.dto.SchemaModelCreateDto
import my.baas.dto.SchemaModelViewDto
import my.baas.models.SchemaModel
import my.baas.services.TenantLimitService
import my.baas.services.TableManagementService

object SchemaController : CrudHandler {

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

    override fun getOne(ctx: Context, resourceId: String) {

        val schema = AppContext.db.find(SchemaModel::class.java, resourceId)
            ?: throw NotFoundResponse("Schema not found")
        ctx.json(SchemaModelViewDto.fromModel(schema))
    }

    override fun getAll(ctx: Context) {
        val schemas = AppContext.db.find(SchemaModel::class.java).findList()
        val schemaDtos = schemas.map { SchemaModelViewDto.fromModel(it) }
        ctx.json(schemaDtos)
    }

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
