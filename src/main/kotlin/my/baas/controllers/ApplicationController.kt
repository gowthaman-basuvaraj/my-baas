package my.baas.controllers

import io.javalin.apibuilder.CrudHandler
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import io.javalin.openapi.*
import my.baas.config.AppContext
import my.baas.dto.ApplicationModelCreateDto
import my.baas.dto.ApplicationModelUpdateDto
import my.baas.dto.ApplicationModelViewDto
import my.baas.models.ApplicationModel

object ApplicationController : CrudHandler {

    @OpenApi(
        summary = "Create a new application",
        operationId = "createApplication",
        path = "/api/applications",
        methods = [HttpMethod.POST],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(from = ApplicationModelCreateDto::class)]),
        responses = [
            OpenApiResponse("201", description = "Application created successfully"),
            OpenApiResponse("400", description = "Bad request")
        ],
        tags = ["Applications"]
    )
    override fun create(ctx: Context) {
        val appCreateDto = ctx.bodyAsClass(ApplicationModelCreateDto::class.java)
        val application = appCreateDto.toModel()
        if(application.applicationName.isEmpty()) throw BadRequestResponse("Application name must not be empty")

        val found = AppContext.db.find(ApplicationModel::class.java)
            .where()
            .eq("applicationName", application.applicationName)
            .findOne()

        if(found != null) throw BadRequestResponse("Application name must be unique")

        application.save()

        ctx.status(201).json(ApplicationModelViewDto.fromModel(application))
    }

    @OpenApi(
        summary = "Get an application by ID",
        operationId = "getApplication",
        path = "/api/applications/{id}",
        methods = [HttpMethod.GET],
        pathParams = [
            OpenApiParam(name = "id", type = String::class, description = "The application ID", required = true)
        ],
        responses = [
            OpenApiResponse("200", description = "Application retrieved successfully"),
            OpenApiResponse("404", description = "Application not found")
        ],
        tags = ["Applications"]
    )
    override fun getOne(ctx: Context, resourceId: String) {
        val application = AppContext.db.find(ApplicationModel::class.java, resourceId)
            ?: throw NotFoundResponse("Application not found")
        ctx.json(ApplicationModelViewDto.fromModel(application))
    }

    @OpenApi(
        summary = "Get all applications",
        operationId = "getAllApplications",
        path = "/api/applications",
        methods = [HttpMethod.GET],
        responses = [
            OpenApiResponse("200", description = "Applications retrieved successfully")
        ],
        tags = ["Applications"]
    )
    override fun getAll(ctx: Context) {
        val applications = AppContext.db.find(ApplicationModel::class.java).findList()
        val appDtos = applications.map { ApplicationModelViewDto.fromModel(it) }
        ctx.json(appDtos)
    }

    @OpenApi(
        summary = "Update an application",
        operationId = "updateApplication",
        path = "/api/applications/{id}",
        methods = [HttpMethod.PATCH],
        pathParams = [
            OpenApiParam(name = "id", type = String::class, description = "The application ID", required = true)
        ],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(from = ApplicationModelUpdateDto::class)]),
        responses = [
            OpenApiResponse("200", description = "Application updated successfully"),
            OpenApiResponse("400", description = "Bad request"),
            OpenApiResponse("404", description = "Application not found")
        ],
        tags = ["Applications"]
    )
    override fun update(ctx: Context, resourceId: String) {
        val application = AppContext.db.find(ApplicationModel::class.java, resourceId)
            ?: throw NotFoundResponse("Application not found")

        val appUpdateDto = ctx.bodyAsClass(ApplicationModelUpdateDto::class.java)

        application.description = appUpdateDto.description
        application.isActive = appUpdateDto.isActive
        application.update()

        ctx.json(ApplicationModelViewDto.fromModel(application))
    }

    @OpenApi(
        summary = "Delete an application",
        operationId = "deleteApplication",
        path = "/api/applications/{id}",
        methods = [HttpMethod.DELETE],
        pathParams = [
            OpenApiParam(name = "id", type = String::class, description = "The application ID", required = true)
        ],
        responses = [
            OpenApiResponse("204", description = "Application deleted successfully"),
            OpenApiResponse("404", description = "Application not found")
        ],
        tags = ["Applications"]
    )
    override fun delete(ctx: Context, resourceId: String) {
        val application = AppContext.db.find(ApplicationModel::class.java, resourceId)
            ?: throw NotFoundResponse("Application not found")

        application.delete()
        ctx.status(204)
    }

}