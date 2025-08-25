package my.baas.controllers

import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import my.baas.services.DataModelService
import java.time.Instant
import kotlin.math.max

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
            ?: throw NotFoundResponse("DataModel not found for entity: $entityName, and uniqueIdentifier: $uniqueIdentifier")
        ctx.json(dataModel)
    }

    fun getAll(ctx: Context) {
        val entityName = ctx.pathParam("entityName")
        val versionName = ctx.pathParamMap()["versionName"] //optionally
        val pageSize = max(ctx.queryParam("pageSize")?.toIntOrNull() ?: 100, 100)
        val pageNo = ctx.queryParam("pageNo")?.toIntOrNull() ?: 1

        val dataModels = dataModelService.findAllByEntityName(entityName, versionName, pageNo, pageSize)
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
        )

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


}