package my.baas.controllers

import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import io.javalin.openapi.*
import my.baas.dto.ReportModelCreateDto
import my.baas.dto.ReportModelViewDto
import my.baas.models.ReportExecutionRequest
import my.baas.services.JobRunnerService
import my.baas.services.ReportService
import java.io.FileInputStream

object ReportController {

    private val reportService = ReportService()

    @OpenApi(
        summary = "Create a new report",
        operationId = "createReport",
        path = "/api/reports",
        methods = [HttpMethod.POST],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(from = ReportModelCreateDto::class)]),
        responses = [
            OpenApiResponse("201", description = "Report created successfully"),
            OpenApiResponse("400", description = "Bad request")
        ],
        tags = ["Reports"]
    )
    fun create(ctx: Context) {
        val reportCreateDto = ctx.bodyAsClass(ReportModelCreateDto::class.java)
        val report = reportCreateDto.toModel()
        val createdReport = reportService.createReport(report)
        ctx.status(201).json(ReportModelViewDto.fromModel(createdReport))
    }

    @OpenApi(
        summary = "Get a report by ID",
        operationId = "getReport",
        path = "/api/reports/{id}",
        methods = [HttpMethod.GET],
        pathParams = [
            OpenApiParam("id", Long::class, "The report ID")
        ],
        responses = [
            OpenApiResponse("200", description = "Report retrieved successfully"),
            OpenApiResponse("400", description = "Invalid report ID"),
            OpenApiResponse("404", description = "Report not found")
        ],
        tags = ["Reports"]
    )
    fun getOne(ctx: Context) {
        val reportId = ctx.pathParam("id").toLongOrNull()
            ?: throw BadRequestResponse("Invalid report ID")

        val report = reportService.getReport(reportId)
        ctx.json(ReportModelViewDto.fromModel(report))
    }

    @OpenApi(
        summary = "Get all reports",
        operationId = "getAllReports",
        path = "/api/reports",
        methods = [HttpMethod.GET],
        queryParams = [
            OpenApiParam("page", Int::class, "Page number (default: 1)", required = false),
            OpenApiParam("pageSize", Int::class, "Page size (default: 20)", required = false)
        ],
        responses = [
            OpenApiResponse("200", description = "Reports retrieved successfully")
        ],
        tags = ["Reports"]
    )
    fun getAll(ctx: Context) {
        val page = ctx.queryParam("page")?.toIntOrNull() ?: 1
        val pageSize = ctx.queryParam("pageSize")?.toIntOrNull() ?: 20

        val reports = reportService.getAllReports(page, pageSize)
        val reportDtos = reports.list.map { ReportModelViewDto.fromModel(it) }

        // Create a response with the same pagination structure but DTOs instead of models
        val dtoResponse = mapOf(
            "list" to reportDtos,
            "totalCount" to reports.totalCount,
            "pageSize" to reports.pageSize,
            "pageIndex" to reports.pageIndex,
        )

        ctx.json(dtoResponse)
    }

    @OpenApi(
        summary = "Update a report",
        operationId = "updateReport",
        path = "/api/reports/{id}",
        methods = [HttpMethod.PUT],
        pathParams = [
            OpenApiParam("id", Long::class, "The report ID")
        ],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(from = ReportModelCreateDto::class)]),
        responses = [
            OpenApiResponse("200", description = "Report updated successfully"),
            OpenApiResponse("400", description = "Bad request"),
            OpenApiResponse("404", description = "Report not found")
        ],
        tags = ["Reports"]
    )
    fun update(ctx: Context) {
        val reportId = ctx.pathParam("id").toLongOrNull()
            ?: throw BadRequestResponse("Invalid report ID")

        val reportCreateDto = ctx.bodyAsClass(ReportModelCreateDto::class.java)
        val updatedReport = reportCreateDto.toModel()
        val report = reportService.updateReport(reportId, updatedReport)
        ctx.json(ReportModelViewDto.fromModel(report))
    }

    @OpenApi(
        summary = "Delete a report",
        operationId = "deleteReport",
        path = "/api/reports/{id}",
        methods = [HttpMethod.DELETE],
        pathParams = [
            OpenApiParam("id", Long::class, "The report ID")
        ],
        responses = [
            OpenApiResponse("204", description = "Report deleted successfully"),
            OpenApiResponse("400", description = "Invalid report ID"),
            OpenApiResponse("404", description = "Report not found")
        ],
        tags = ["Reports"]
    )
    fun delete(ctx: Context) {
        val reportId = ctx.pathParam("id").toLongOrNull()
            ?: throw BadRequestResponse("Invalid report ID")

        val deleted = reportService.deleteReport(reportId)
        if (deleted) {
            ctx.status(204)
        } else {
            throw NotFoundResponse("Report not found")
        }
    }


    @OpenApi(
        summary = "Get all scheduled reports",
        operationId = "getScheduledReports",
        path = "/api/reports/scheduled",
        methods = [HttpMethod.GET],
        responses = [
            OpenApiResponse("200", description = "Scheduled reports retrieved successfully")
        ],
        tags = ["Reports"]
    )
    fun getScheduledReports(ctx: Context) {
        val scheduledReports = reportService.getScheduledReports()
        ctx.json(scheduledReports)
    }

    @OpenApi(
        summary = "Activate a report",
        operationId = "activateReport",
        path = "/api/reports/{id}/activate",
        methods = [HttpMethod.POST],
        pathParams = [
            OpenApiParam("id", Long::class, "The report ID")
        ],
        responses = [
            OpenApiResponse("200", description = "Report activated successfully"),
            OpenApiResponse("400", description = "Invalid report ID"),
            OpenApiResponse("404", description = "Report not found")
        ],
        tags = ["Reports"]
    )
    fun activate(ctx: Context) {
        val reportId = ctx.pathParam("id").toLongOrNull()
            ?: throw BadRequestResponse("Invalid report ID")

        val report = reportService.getReport(reportId)
        report.isActive = true
        val updatedReport = reportService.updateReport(reportId, report)

        ctx.json(mapOf("message" to "Report activated successfully", "report" to updatedReport))
    }

    @OpenApi(
        summary = "Deactivate a report",
        operationId = "deactivateReport",
        path = "/api/reports/{id}/deactivate",
        methods = [HttpMethod.POST],
        pathParams = [
            OpenApiParam("id", Long::class, "The report ID")
        ],
        responses = [
            OpenApiResponse("200", description = "Report deactivated successfully"),
            OpenApiResponse("400", description = "Invalid report ID"),
            OpenApiResponse("404", description = "Report not found")
        ],
        tags = ["Reports"]
    )
    fun deactivate(ctx: Context) {
        val reportId = ctx.pathParam("id").toLongOrNull()
            ?: throw BadRequestResponse("Invalid report ID")

        val report = reportService.getReport(reportId)
        report.isActive = false
        val updatedReport = reportService.updateReport(reportId, report)

        ctx.json(mapOf("message" to "Report deactivated successfully", "report" to updatedReport))
    }

    @OpenApi(
        summary = "Validate SQL query",
        operationId = "validateSql",
        path = "/api/reports/validate-sql",
        methods = [HttpMethod.POST],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(from = SqlValidationRequest::class)]),
        responses = [
            OpenApiResponse("200", description = "SQL validation result")
        ],
        tags = ["Reports"]
    )
    fun validateSql(ctx: Context) {
        val request = ctx.bodyAsClass(SqlValidationRequest::class.java)

        try {


            //todo: validate SQL

            ctx.json(
                mapOf(
                    "valid" to true,
                    "message" to "SQL is valid"
                )
            )
        } catch (e: Exception) {
            ctx.json(
                mapOf(
                    "valid" to false,
                    "message" to (e.message ?: "Unknown validation error")
                )
            )
        }
    }

    // Job-based execution endpoints
    @OpenApi(
        summary = "Submit a report execution job",
        operationId = "submitReportJob",
        path = "/api/reports/jobs",
        methods = [HttpMethod.POST],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(from = ReportExecutionRequest::class)]),
        responses = [
            OpenApiResponse("201", description = "Job submitted successfully")
        ],
        tags = ["Report Jobs"]
    )
    fun submitJob(ctx: Context) {
        val request = ctx.bodyAsClass(ReportExecutionRequest::class.java)
        val response = reportService.submitReportJob(request)
        ctx.status(201).json(response)
    }

    @OpenApi(
        summary = "Get job status",
        operationId = "getJobStatus",
        path = "/api/reports/jobs/{jobId}/status",
        methods = [HttpMethod.GET],
        pathParams = [
            OpenApiParam("jobId", String::class, "The job ID")
        ],
        responses = [
            OpenApiResponse("200", description = "Job status retrieved successfully")
        ],
        tags = ["Report Jobs"]
    )
    fun getJobStatus(ctx: Context) {
        val jobId = ctx.pathParam("jobId")
        val status = reportService.getJobStatus(jobId)
        ctx.json(status)
    }

    @OpenApi(
        summary = "Cancel a report job",
        operationId = "cancelJob",
        path = "/api/reports/jobs/{jobId}/cancel",
        methods = [HttpMethod.POST],
        pathParams = [
            OpenApiParam("jobId", String::class, "The job ID")
        ],
        responses = [
            OpenApiResponse("200", description = "Job cancelled successfully"),
            OpenApiResponse("400", description = "Failed to cancel job")
        ],
        tags = ["Report Jobs"]
    )
    fun cancelJob(ctx: Context) {
        val jobId = ctx.pathParam("jobId")
        val cancelled = reportService.cancelJob(jobId)

        if (cancelled) {
            ctx.json(mapOf("message" to "Job cancelled successfully", "jobId" to jobId))
        } else {
            throw BadRequestResponse("Failed to cancel job")
        }
    }


    @OpenApi(
        summary = "Get report execution history",
        operationId = "getExecutionHistory",
        path = "/api/reports/{id}/history",
        methods = [HttpMethod.GET],
        pathParams = [
            OpenApiParam("id", Long::class, "The report ID")
        ],
        queryParams = [
            OpenApiParam("page", Int::class, "Page number (default: 1)", required = false),
            OpenApiParam("pageSize", Int::class, "Page size (default: 20)", required = false)
        ],
        responses = [
            OpenApiResponse("200", description = "Execution history retrieved successfully"),
            OpenApiResponse("400", description = "Invalid report ID")
        ],
        tags = ["Report Jobs"]
    )
    fun getExecutionHistory(ctx: Context) {
        val reportId = ctx.pathParam("id").toLongOrNull()
            ?: throw BadRequestResponse("Invalid report ID")

        val page = ctx.queryParam("page")?.toIntOrNull() ?: 1
        val pageSize = ctx.queryParam("pageSize")?.toIntOrNull() ?: 20

        val history = reportService.getExecutionHistory(reportId, page, pageSize)
        ctx.json(history)
    }

    @OpenApi(
        summary = "Download report job result file",
        operationId = "downloadResult",
        path = "/api/reports/jobs/{jobId}/download",
        methods = [HttpMethod.GET],
        pathParams = [
            OpenApiParam("jobId", String::class, "The job ID")
        ],
        responses = [
            OpenApiResponse("200", description = "File download"),
            OpenApiResponse("404", description = "Result file not found or not available"),
            OpenApiResponse("400", description = "Failed to download file")
        ],
        tags = ["Report Jobs"]
    )
    fun downloadResult(ctx: Context) {
        val jobId = ctx.pathParam("jobId")

        val downloadInfo = reportService.getJobDownloadInfo(jobId)
        if (!downloadInfo.isAvailable) {
            throw NotFoundResponse("Result file not available")
        }

        val file = JobRunnerService.downloadResultFile(jobId)
            ?: throw NotFoundResponse("Result file not found")

        try {
            ctx.res().apply {
                contentType = downloadInfo.contentType
                setHeader("Content-Disposition", "attachment; filename=\"${downloadInfo.fileName}\"")
                setHeader("Content-Length", file.length().toString())
            }

            val inputStream = FileInputStream(file)
            try {
                inputStream.copyTo(ctx.res().outputStream)
            } finally {
                inputStream.close()
            }

            // Clean up temp file if it was downloaded from S3
            if (file.absolutePath.contains("temp")) {
                file.delete()
            }

        } catch (e: Exception) {
            throw BadRequestResponse("Failed to download file: ${e.message}")
        }
    }
}

data class SqlValidationRequest(
    val sql: String
)