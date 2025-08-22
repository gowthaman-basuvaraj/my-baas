package my.baas.controllers

import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import my.baas.models.ReportModel
import my.baas.services.ReportService

object ReportController {

    private val reportService = ReportService()

    fun create(ctx: Context) {
        val report = ctx.bodyAsClass(ReportModel::class.java)
        val createdReport = reportService.createReport(report)
        ctx.status(201).json(createdReport)
    }

    fun getOne(ctx: Context) {
        val reportId = ctx.pathParam("id").toLongOrNull()
            ?: throw BadRequestResponse("Invalid report ID")

        val report = reportService.getReport(reportId)
        ctx.json(report)
    }

    fun getAll(ctx: Context) {
        val page = ctx.queryParam("page")?.toIntOrNull() ?: 1
        val pageSize = ctx.queryParam("pageSize")?.toIntOrNull() ?: 20

        val reports = reportService.getAllReports(page, pageSize)


        ctx.json(reports)
    }

    fun update(ctx: Context) {
        val reportId = ctx.pathParam("id").toLongOrNull()
            ?: throw BadRequestResponse("Invalid report ID")

        val updatedReport = ctx.bodyAsClass(ReportModel::class.java)
        val report = reportService.updateReport(reportId, updatedReport)
        ctx.json(report)
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

    fun execute(ctx: Context) {
        val reportId = ctx.pathParam("id").toLongOrNull()
            ?: throw BadRequestResponse("Invalid report ID")

        val result = reportService.executeReport(reportId)
        ctx.json(result)
    }

    fun executeByName(ctx: Context) {
        val reportName = ctx.pathParam("name")
        val result = reportService.executeReportByName(reportName)
        ctx.json(result)
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
            // Create a temporary report to validate SQL
            val tempReport = ReportModel().apply {
                name = "temp"
                sql = request.sql
                executionType = ReportModel.ExecutionType.ADHOC
                isActive = true
            }

            // This will validate the SQL through the service
            reportService.executeReport(tempReport.id)

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
}

data class SqlValidationRequest(
    val sql: String
)