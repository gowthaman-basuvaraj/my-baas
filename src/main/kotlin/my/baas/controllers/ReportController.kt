package my.baas.controllers

import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import my.baas.dto.ReportModelCreateDto
import my.baas.dto.ReportModelViewDto
import my.baas.models.ReportExecutionRequest
import my.baas.services.JobRunnerService
import my.baas.services.ReportService
import java.io.FileInputStream

object ReportController {

    private val reportService = ReportService()

    fun create(ctx: Context) {
        val reportCreateDto = ctx.bodyAsClass(ReportModelCreateDto::class.java)
        val report = reportCreateDto.toModel()
        val createdReport = reportService.createReport(report)
        ctx.status(201).json(ReportModelViewDto.fromModel(createdReport))
    }

    fun getOne(ctx: Context) {
        val reportId = ctx.pathParam("id").toLongOrNull()
            ?: throw BadRequestResponse("Invalid report ID")

        val report = reportService.getReport(reportId)
        ctx.json(ReportModelViewDto.fromModel(report))
    }

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

    fun update(ctx: Context) {
        val reportId = ctx.pathParam("id").toLongOrNull()
            ?: throw BadRequestResponse("Invalid report ID")

        val reportCreateDto = ctx.bodyAsClass(ReportModelCreateDto::class.java)
        val updatedReport = reportCreateDto.toModel()
        val report = reportService.updateReport(reportId, updatedReport)
        ctx.json(ReportModelViewDto.fromModel(report))
    }

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


    fun getScheduledReports(ctx: Context) {
        val scheduledReports = reportService.getScheduledReports()
        ctx.json(scheduledReports)
    }

    fun activate(ctx: Context) {
        val reportId = ctx.pathParam("id").toLongOrNull()
            ?: throw BadRequestResponse("Invalid report ID")

        val report = reportService.getReport(reportId)
        report.isActive = true
        val updatedReport = reportService.updateReport(reportId, report)

        ctx.json(mapOf("message" to "Report activated successfully", "report" to updatedReport))
    }

    fun deactivate(ctx: Context) {
        val reportId = ctx.pathParam("id").toLongOrNull()
            ?: throw BadRequestResponse("Invalid report ID")

        val report = reportService.getReport(reportId)
        report.isActive = false
        val updatedReport = reportService.updateReport(reportId, report)

        ctx.json(mapOf("message" to "Report deactivated successfully", "report" to updatedReport))
    }

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
    fun submitJob(ctx: Context) {
        val request = ctx.bodyAsClass(ReportExecutionRequest::class.java)
        val response = reportService.submitReportJob(request)
        ctx.status(202).json(response)
    }

    fun getJobStatus(ctx: Context) {
        val jobId = ctx.pathParam("jobId")
        val status = reportService.getJobStatus(jobId)
        ctx.json(status)
    }

    fun cancelJob(ctx: Context) {
        val jobId = ctx.pathParam("jobId")
        val cancelled = reportService.cancelJob(jobId)

        if (cancelled) {
            ctx.json(mapOf("message" to "Job cancelled successfully", "jobId" to jobId))
        } else {
            throw BadRequestResponse("Failed to cancel job")
        }
    }


    fun getExecutionHistory(ctx: Context) {
        val reportId = ctx.pathParam("id").toLongOrNull()
            ?: throw BadRequestResponse("Invalid report ID")

        val page = ctx.queryParam("page")?.toIntOrNull() ?: 1
        val pageSize = ctx.queryParam("pageSize")?.toIntOrNull() ?: 20

        val history = reportService.getExecutionHistory(reportId, page, pageSize)
        ctx.json(history)
    }

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