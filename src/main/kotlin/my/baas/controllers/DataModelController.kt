package my.baas.controllers

import my.baas.services.DataModelService
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse

object DataModelController {

    private val dataModelService = DataModelService()

    fun create(ctx: Context) {
        val entityName = ctx.pathParam("entityName")
        val versionName = ctx.pathParam("versionName")

        val data = ctx.bodyAsClass(Map::class.java) as Map<String, Any>
        val createdDataModel = dataModelService.create(entityName, versionName, data)
        ctx.status(201).json(createdDataModel)
    }

    fun getOne(ctx: Context) {
        val uniqueIdentifier = ctx.pathParam("uniqueIdentifier")
        val entityName = ctx.pathParam("entityName")

        val dataModel = dataModelService.findByUniqueIdentifier(entityName, uniqueIdentifier)
            ?: throw NotFoundResponse("DataModel not found")
        ctx.json(dataModel)
    }

    fun getAll(ctx: Context) {
        val entityName = ctx.pathParam("entityName")

        val dataModels = dataModelService.findAllByEntityName(entityName)
        ctx.json(dataModels)
    }

    fun search(ctx: Context) {
        val entityName = ctx.pathParam("entityName")

        val searchRequest = ctx.bodyAsClass(SearchRequest::class.java)
        val dataModels = dataModelService.searchWithFilters(entityName, searchRequest)
        ctx.json(dataModels)
    }

    fun update(ctx: Context) {
        val uniqueIdentifier = ctx.pathParam("uniqueIdentifier")
        val entityName = ctx.pathParam("entityName")
        val versionName = ctx.pathParam("versionName")


        val data = ctx.bodyAsClass(Map::class.java) as Map<String, Any>
        val result = dataModelService.update(uniqueIdentifier, entityName, versionName, data)
            ?: throw NotFoundResponse("DataModel not found")
        ctx.json(result)
    }

    fun patch(ctx: Context) {
        val uniqueIdentifier = ctx.pathParam("uniqueIdentifier")
        val entityName = ctx.pathParam("entityName")
        val versionName = ctx.pathParam("versionName")

        val patchData = ctx.bodyAsClass(Map::class.java) as Map<String, Any>
        val result = dataModelService.patch(uniqueIdentifier, entityName, versionName, patchData)
            ?: throw NotFoundResponse("DataModel not found")
        ctx.json(result)
    }

    fun delete(ctx: Context) {
        val uniqueIdentifier = ctx.pathParam("uniqueIdentifier")
        val entityName = ctx.pathParam("entityName")
        val deleted = dataModelService.deleteByUniqueIdentifier(entityName, uniqueIdentifier)
        if (!deleted) {
            throw NotFoundResponse("DataModel not found")
        }
        ctx.status(204)
    }

    fun migrate(ctx: Context) {
        val uniqueIdentifier = ctx.pathParam("uniqueIdentifier")
        val entityName = ctx.pathParam("entityName")
        
        // Expect a destination version in the request body
        data class MigrateRequest(val destinationVersion: String)
        val request = ctx.bodyAsClass(MigrateRequest::class.java)
        
        val migratedDataModel = dataModelService.migrateVersion(
            entityName, 
            uniqueIdentifier, 
            request.destinationVersion
        ) ?: throw NotFoundResponse("DataModel not found")
        
        ctx.json(migratedDataModel)
    }

    fun getSchema(ctx: Context) {
        val entityName = ctx.pathParam("entityName")
        val versionName = ctx.pathParam("versionName")
        
        val schema = dataModelService.getSchema(entityName, versionName)
        ctx.json(schema)
    }

    fun validatePayload(ctx: Context) {
        val entityName = ctx.pathParam("entityName")
        val versionName = ctx.pathParam("versionName")
        
        val payload = ctx.bodyAsClass(Map::class.java) as Map<String, Any>
        val validationResult = dataModelService.validatePayload(entityName, versionName, payload)
        ctx.json(validationResult)
    }
    
    fun getActiveSubscriptions(ctx: Context) {
        val subscriptions = my.baas.services.WebSocketEventManager.getActiveSubscriptions()
        ctx.json(subscriptions)
    }
}