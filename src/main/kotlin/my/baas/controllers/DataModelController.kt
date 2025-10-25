package my.baas.controllers

import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import io.javalin.openapi.*
import my.baas.dto.SearchRequestDto
import my.baas.services.DataModelService
import kotlin.math.max

data class MigrateRequest(val destinationVersion: String)

object DataModelController {

    private val dataModelService = DataModelService

    @OpenApi(
        summary = "Create a new data model",
        operationId = "createDataModel",
        path = "/api/app/{applicationName}/data/{entityName}/{versionName}",
        methods = [HttpMethod.POST],
        pathParams = [
            OpenApiParam(name = "entityName", type = String::class, description = "The entity name", required = true),
            OpenApiParam(name = "versionName", type = String::class, description = "The version name", required = true)
        ],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(from = Map::class)]),
        responses = [
            OpenApiResponse("201", description = "Data model created successfully"),
            OpenApiResponse("400", description = "Bad request"),
            OpenApiResponse("404", description = "Schema not found")
        ],
        tags = ["Data Models"]
    )
    fun create(ctx: Context) {
        val entityName = ctx.pathParam("entityName")
        val versionName = ctx.pathParam("versionName")

        val data = ctx.bodyAsClass(Map::class.java) as Map<String, Any>
        val createdDataModel = dataModelService.create(entityName, versionName, data)
        ctx.status(201).json(createdDataModel)
    }

    @OpenApi(
        summary = "Get a data model by unique identifier",
        operationId = "getDataModel",
        path = "/api/app/{applicationName}/data/{entityName}/{uniqueIdentifier}",
        methods = [HttpMethod.GET],
        pathParams = [
            OpenApiParam(name = "entityName", type = String::class, description = "The entity name", required = true),
            OpenApiParam(
                name = "uniqueIdentifier",
                type = String::class,
                description = "The unique identifier",
                required = true
            )
        ],
        responses = [
            OpenApiResponse("200", description = "Data model retrieved successfully"),
            OpenApiResponse("404", description = "Data model not found")
        ],
        tags = ["Data Models"]
    )
    fun getOne(ctx: Context) {
        val uniqueIdentifier = ctx.pathParam("uniqueIdentifier")
        val entityName = ctx.pathParam("entityName")

        val dataModel = dataModelService.findByUniqueIdentifier(entityName, uniqueIdentifier)
            ?: throw NotFoundResponse("DataModel not found for entity: $entityName, and uniqueIdentifier: $uniqueIdentifier")
        ctx.status(200).json(dataModel)
    }

    @OpenApi(
        summary = "Get all data models for an entity",
        operationId = "getAllDataModels",
        path = "/api/app/{applicationName}/data/{entityName}",
        methods = [HttpMethod.GET],
        pathParams = [
            OpenApiParam(name = "entityName", type = String::class, description = "The entity name", required = true)
        ],
        queryParams = [
            OpenApiParam("versionName", String::class, "Filter by version name", required = false),
            OpenApiParam("pageNo", Int::class, "Page number (default: 1)", required = false),
            OpenApiParam("pageSize", Int::class, "Page size (max: 100, default: 100)", required = false)
        ],
        responses = [
            OpenApiResponse("200", description = "Data models retrieved successfully")
        ],
        tags = ["Data Models"]
    )
    fun getAll(ctx: Context) {
        val entityName = ctx.pathParam("entityName")
        val versionName = ctx.pathParamMap()["versionName"] //optionally
        val pageSize = max(ctx.queryParam("pageSize")?.toIntOrNull() ?: 100, 100)
        val pageNo = ctx.queryParam("pageNo")?.toIntOrNull() ?: 1

        val dataModels = dataModelService.findAllByEntityName(entityName, versionName, pageNo, pageSize)

        ctx.status(200).json(
            mapOf(
                "pageSize" to dataModels.pageSize,
                "totalCount" to dataModels.totalCount,
                "totalPageCount" to dataModels.totalPageCount,
                "list" to dataModels.list,
            )
        )
    }


    @OpenApi(
        summary = "Update a data model",
        operationId = "updateDataModel",
        path = "/api/app/{applicationName}/data/{entityName}/{versionName}/{uniqueIdentifier}",
        methods = [HttpMethod.PUT],
        pathParams = [
            OpenApiParam(name = "entityName", type = String::class, description = "The entity name", required = true),
            OpenApiParam(name = "versionName", type = String::class, description = "The version name", required = true),
            OpenApiParam(
                name = "uniqueIdentifier",
                type = String::class,
                description = "The unique identifier",
                required = true
            )
        ],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(from = Map::class)]),
        responses = [
            OpenApiResponse("200", description = "Data model updated successfully"),
            OpenApiResponse("400", description = "Bad request"),
            OpenApiResponse("404", description = "Data model not found")
        ],
        tags = ["Data Models"]
    )
    fun update(ctx: Context) {
        val uniqueIdentifier = ctx.pathParam("uniqueIdentifier")
        val entityName = ctx.pathParam("entityName")
        val versionName = ctx.pathParam("versionName")


        val data = ctx.bodyAsClass(Map::class.java) as Map<String, Any>
        val result = dataModelService.update(uniqueIdentifier, entityName, versionName, data)
            ?: throw NotFoundResponse("DataModel not found")
        ctx.status(200).json(result)
    }

    @OpenApi(
        summary = "Partially update a data model",
        operationId = "patchDataModel",
        path = "/api/app/{applicationName}/data/{entityName}/{versionName}/{uniqueIdentifier}",
        methods = [HttpMethod.PATCH],
        pathParams = [
            OpenApiParam(name = "entityName", type = String::class, description = "The entity name", required = true),
            OpenApiParam(name = "versionName", type = String::class, description = "The version name", required = true),
            OpenApiParam(
                name = "uniqueIdentifier",
                type = String::class,
                description = "The unique identifier",
                required = true
            )
        ],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(from = Map::class)]),
        responses = [
            OpenApiResponse("200", description = "Data model patched successfully"),
            OpenApiResponse("400", description = "Bad request"),
            OpenApiResponse("404", description = "Data model not found")
        ],
        tags = ["Data Models"]
    )
    fun patch(ctx: Context) {
        val uniqueIdentifier = ctx.pathParam("uniqueIdentifier")
        val entityName = ctx.pathParam("entityName")
        val versionName = ctx.pathParam("versionName")

        val patchData = ctx.bodyAsClass(Map::class.java) as Map<String, Any>
        val result = dataModelService.patch(uniqueIdentifier, entityName, versionName, patchData)
            ?: throw NotFoundResponse("DataModel not found")
        ctx.status(200).json(result)
    }

    @OpenApi(
        summary = "Delete a data model",
        operationId = "deleteDataModel",
        path = "/api/app/{applicationName}/data/{entityName}/{uniqueIdentifier}",
        methods = [HttpMethod.DELETE],
        pathParams = [
            OpenApiParam(name = "entityName", type = String::class, description = "The entity name", required = true),
            OpenApiParam(
                name = "uniqueIdentifier",
                type = String::class,
                description = "The unique identifier",
                required = true
            )
        ],
        responses = [
            OpenApiResponse("204", description = "Data model deleted successfully"),
            OpenApiResponse("404", description = "Data model not found")
        ],
        tags = ["Data Models"]
    )
    fun delete(ctx: Context) {
        val uniqueIdentifier = ctx.pathParam("uniqueIdentifier")
        val entityName = ctx.pathParam("entityName")
        val deleted = dataModelService.deleteByUniqueIdentifier(entityName, uniqueIdentifier)
        if (!deleted) {
            throw NotFoundResponse("DataModel not found")
        }
        ctx.status(204)
    }

    @OpenApi(
        summary = "Migrate a data model to a different version",
        operationId = "migrateDataModel",
        path = "/api/app/{applicationName}/data/{entityName}/{uniqueIdentifier}/migrate",
        methods = [HttpMethod.POST],
        pathParams = [
            OpenApiParam(name = "entityName", type = String::class, description = "The entity name", required = true),
            OpenApiParam(
                name = "uniqueIdentifier",
                type = String::class,
                description = "The unique identifier",
                required = true
            )
        ],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(from = MigrateRequest::class)]),
        responses = [
            OpenApiResponse("200", description = "Data model migrated successfully"),
            OpenApiResponse("400", description = "Bad request"),
            OpenApiResponse("404", description = "Data model not found")
        ],
        tags = ["Data Models"]
    )
    fun migrate(ctx: Context) {
        val uniqueIdentifier = ctx.pathParam("uniqueIdentifier")
        val entityName = ctx.pathParam("entityName")

        // Expect a destination version in the request body
        val request = ctx.bodyAsClass(MigrateRequest::class.java)

        val migratedDataModel = dataModelService.migrateVersion(
            entityName,
            uniqueIdentifier,
            request.destinationVersion
        )

        ctx.status(200).json(migratedDataModel)
    }

    @OpenApi(
        summary = "Get schema for an entity and version",
        operationId = "getSchema",
        path = "/api/app/{applicationName}/data/{entityName}/{versionName}/schema",
        methods = [HttpMethod.GET],
        pathParams = [
            OpenApiParam(name = "entityName", type = String::class, description = "The entity name", required = true),
            OpenApiParam(name = "versionName", type = String::class, description = "The version name", required = true)
        ],
        responses = [
            OpenApiResponse("200", description = "Schema retrieved successfully"),
            OpenApiResponse("404", description = "Schema not found")
        ],
        tags = ["Data Models"]
    )
    fun getSchema(ctx: Context) {
        val entityName = ctx.pathParam("entityName")
        val versionName = ctx.pathParam("versionName")

        val schema = dataModelService.getSchema(entityName, versionName)
        ctx.status(200).json(schema)
    }

    @OpenApi(
        summary = "Validate payload against schema",
        operationId = "validatePayload",
        path = "/api/app/{applicationName}/data/{entityName}/{versionName}/validate",
        methods = [HttpMethod.POST],
        pathParams = [
            OpenApiParam(name = "entityName", type = String::class, description = "The entity name", required = true),
            OpenApiParam(name = "versionName", type = String::class, description = "The version name", required = true)
        ],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(from = Map::class)]),
        responses = [
            OpenApiResponse("200", description = "Validation result"),
            OpenApiResponse("404", description = "Schema not found")
        ],
        tags = ["Data Models"]
    )
    fun validatePayload(ctx: Context) {
        val entityName = ctx.pathParam("entityName")
        val versionName = ctx.pathParam("versionName")

        val payload = ctx.bodyAsClass(Map::class.java) as Map<String, Any>
        val validationResult = dataModelService.validatePayload(entityName, versionName, payload)
        ctx.status(200).json(validationResult)
    }

    @OpenApi(
        summary = "Search data models",
        operationId = "searchDataModels",
        path = "/api/app/{applicationName}/data/{entityName}/search",
        methods = [HttpMethod.POST],
        pathParams = [
            OpenApiParam(name = "entityName", type = String::class, description = "The entity name", required = true)
        ],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(from = SearchRequestDto::class)]),
        responses = [
            OpenApiResponse("200", description = "Search results")
        ],
        tags = ["Data Models"]
    )
    fun search(ctx: Context) {
        val entityName = ctx.pathParam("entityName")
        val searchRequest = ctx.bodyAsClass(SearchRequestDto::class.java)

        val results = dataModelService.search(
            entityName = entityName,
            filters = searchRequest.filters,
            pageNo = searchRequest.pageNo,
            pageSize = searchRequest.pageSize
        )

        ctx.status(200).json(results)
    }

}