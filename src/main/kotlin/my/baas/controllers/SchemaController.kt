package my.baas.controllers

import io.ebean.DB
import io.javalin.apibuilder.CrudHandler
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import my.baas.models.SchemaModel

object SchemaController : CrudHandler {

    override fun create(ctx: Context) {
        val schema = ctx.bodyAsClass(SchemaModel::class.java)
        schema.save()
        ctx.status(201).json(schema)
    }

    override fun getOne(ctx: Context, resourceId: String) {

        val schema = DB.find(SchemaModel::class.java, resourceId)
            ?: throw NotFoundResponse("Schema not found")
        ctx.json(schema)
    }

    override fun getAll(ctx: Context) {
        val schemas = DB.find(SchemaModel::class.java).findList()
        ctx.json(schemas)
    }

    override fun update(ctx: Context, resourceId: String) {
        val schema = DB.find(SchemaModel::class.java, resourceId)
            ?: throw NotFoundResponse("Schema not found")

        val updatedSchema = ctx.bodyAsClass(SchemaModel::class.java)
        schema.entityName = updatedSchema.entityName
        schema.jsonSchema = updatedSchema.jsonSchema
        schema.versionName = updatedSchema.versionName
        schema.uniqueIdentifierFormatter = updatedSchema.uniqueIdentifierFormatter
        schema.indexedJsonPaths = updatedSchema.indexedJsonPaths
        schema.lifecycleScripts = updatedSchema.lifecycleScripts
        schema.isValidationEnabled = updatedSchema.isValidationEnabled
        schema.update()
        ctx.json(schema)
    }

    override fun delete(ctx: Context, resourceId: String) {
        val schema = DB.find(SchemaModel::class.java, resourceId)
            ?: throw NotFoundResponse("Schema not found")
        schema.delete()
        ctx.status(204)
    }
}
