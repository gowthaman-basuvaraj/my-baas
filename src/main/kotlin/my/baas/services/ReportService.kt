package my.baas.services

import com.fasterxml.jackson.databind.ObjectMapper
import io.ebean.PagedList
import io.javalin.http.BadRequestResponse
import io.javalin.http.NotFoundResponse
import my.baas.config.AppContext
import my.baas.models.ReportModel
import my.baas.repositories.ReportRepository
import my.baas.repositories.ReportRepositoryImpl
import org.slf4j.LoggerFactory

data class ReportExecutionResult(
    val reportId: Long,
    val reportName: String,
    val success: Boolean,
    val data: List<Map<String, Any?>>? = null,
    val errorMessage: String? = null,
    val executionTimeMs: Long,
    val rowCount: Int = 0
)

class ReportService(
    private val repository: ReportRepository = ReportRepositoryImpl(),
    private val objectMapper: ObjectMapper = AppContext.objectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun createReport(report: ReportModel): ReportModel {
        // Validate report name uniqueness
        val existingReport = repository.findByName(report.name)
        if (existingReport != null) {
            throw BadRequestResponse("Report with name '${report.name}' already exists")
        }

        // Validate execution type and cron schedule consistency
        validateExecutionTypeAndSchedule(report)

        // Validate SQL syntax (basic check)
        validateSqlSyntax(report.sql)

        // Validate completion actions
        validateCompletionActions(report.completionActions)

        return repository.save(report)
    }

    fun updateReport(id: Long, updatedReport: ReportModel): ReportModel {
        val existingReport = repository.findById(id)
            ?: throw NotFoundResponse("Report not found with id: $id")

        // Check if name is being changed and if new name already exists
        if (existingReport.name != updatedReport.name) {
            val reportWithSameName = repository.findByName(updatedReport.name)
            if (reportWithSameName != null && reportWithSameName.id != id) {
                throw BadRequestResponse("Report with name '${updatedReport.name}' already exists")
            }
        }

        // Validate execution type and cron schedule consistency
        validateExecutionTypeAndSchedule(updatedReport)

        // Validate SQL syntax
        validateSqlSyntax(updatedReport.sql)

        // Validate completion actions
        validateCompletionActions(updatedReport.completionActions)

        // Update existing report fields
        existingReport.name = updatedReport.name
        existingReport.sql = updatedReport.sql
        existingReport.executionType = updatedReport.executionType
        existingReport.cronSchedule = updatedReport.cronSchedule
        existingReport.completionActions = updatedReport.completionActions
        existingReport.isActive = updatedReport.isActive

        return repository.update(existingReport)
    }

    fun getReport(id: Long): ReportModel {
        return repository.findById(id)
            ?: throw NotFoundResponse("Report not found with id: $id")
    }

    fun getAllReports(pageNo: Int = 1, pageSize: Int = 20): PagedList<ReportModel> {
        return repository.findAll(pageNo, pageSize)
    }

    fun deleteReport(id: Long): Boolean {
        return repository.deleteById(id)
    }

    fun executeReport(id: Long): ReportExecutionResult {
        val report = repository.findById(id)
            ?: throw NotFoundResponse("Report not found with id: $id")

        if (!report.isActive) {
            throw BadRequestResponse("Report '${report.name}' is not active")
        }

        val startTime = System.currentTimeMillis()

        try {
            val resultData = executeSqlQuery(report.sql)
            val executionTime = System.currentTimeMillis() - startTime

            val result = ReportExecutionResult(
                reportId = report.id,
                reportName = report.name,
                success = true,
                data = resultData,
                executionTimeMs = executionTime,
                rowCount = resultData.size
            )

            // Execute completion actions if configured
            if (report.completionActions.isNotEmpty()) {
                executeCompletionActions(report, result)
            }

            return result

        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            logger.error("Error executing report ${report.name}", e)

            return ReportExecutionResult(
                reportId = report.id,
                reportName = report.name,
                success = false,
                errorMessage = e.message ?: "Unknown error occurred",
                executionTimeMs = executionTime
            )
        }
    }

    fun getScheduledReports(): List<ReportModel> {
        return repository.findScheduledReports()
    }

    fun executeReportByName(reportName: String): ReportExecutionResult {
        val report = repository.findByName(reportName)
            ?: throw NotFoundResponse("Report not found with name: $reportName")

        return executeReport(report.id)
    }

    private fun validateExecutionTypeAndSchedule(report: ReportModel) {
        when (report.executionType) {
            ReportModel.ExecutionType.SCHEDULED -> {
                if (report.cronSchedule.isNullOrBlank()) {
                    throw BadRequestResponse("Cron schedule is required for SCHEDULED execution type")
                }
            }

            ReportModel.ExecutionType.BOTH -> {
                if (report.cronSchedule.isNullOrBlank()) {
                    throw BadRequestResponse("Cron schedule is required for BOTH execution type")
                }
            }

            ReportModel.ExecutionType.ADHOC -> {
                // Cron schedule is optional for ADHOC
            }
        }

        // Validate cron expression format if provided
        report.cronSchedule?.let { cronExpression ->
            if (!isValidCronExpression(cronExpression)) {
                throw BadRequestResponse("Invalid cron expression format: $cronExpression")
            }
        }
    }

    private fun validateSqlSyntax(sql: String) {
        // Basic SQL validation - check for dangerous operations
        val sqlLower = sql.lowercase().trim()

        // Check for potentially dangerous keywords
        val dangerousKeywords = listOf(
            "drop", "delete", "update", "insert", "alter", "create",
            "truncate", "execute", "exec", "xp_", "sp_"
        )

        if (dangerousKeywords.any { keyword ->
                sqlLower.contains(keyword)
            }) {
            throw BadRequestResponse("SQL Contains Banned Words")
        }
    }

    private fun validateCompletionActions(actions: List<ReportModel.CompletionAction>) {
        actions.forEach { action ->
            when (action) {
                is ReportModel.CompletionAction.S3Upload -> {
                    if (action.bucketName.isBlank()) {
                        throw BadRequestResponse("S3 upload action missing bucket name")
                    }
                    if (action.accessKey.isBlank()) {
                        throw BadRequestResponse("S3 upload action missing access key")
                    }
                    if (action.secretKey.isBlank()) {
                        throw BadRequestResponse("S3 upload action missing secret key")
                    }
                }

                is ReportModel.CompletionAction.SftpUpload -> {
                    if (action.host.isBlank()) {
                        throw BadRequestResponse("SFTP upload action missing host")
                    }
                    if (action.username.isBlank()) {
                        throw BadRequestResponse("SFTP upload action missing username")
                    }
                    if (action.remotePath.isBlank()) {
                        throw BadRequestResponse("SFTP upload action missing remote path")
                    }
                    // Validation for password/sshKey is handled in the data class init block
                }

                is ReportModel.CompletionAction.Email -> {
                    if (action.to.isEmpty()) {
                        throw BadRequestResponse("Email action missing recipients")
                    }
                    if (action.subject.isBlank()) {
                        throw BadRequestResponse("Email action missing subject")
                    }
                    // Validate email addresses
                    (action.to + action.cc + action.bcc).forEach { email ->
                        if (email.isNotBlank() && !isValidEmail(email)) {
                            throw BadRequestResponse("Invalid email address: $email")
                        }
                    }
                }
            }
        }
    }

    private fun isValidCronExpression(cronExpression: String): Boolean {
        val parts = cronExpression.trim().split("\\s+".toRegex())
        return parts.size in 5..6
    }

    private fun executeSqlQuery(sql: String): List<Map<String, Any?>> {
        val results = mutableListOf<Map<String, Any?>>()

        AppContext.db.sqlQuery(sql).findList().forEach { sqlRow ->
            val row = mutableMapOf<String, Any?>()
            sqlRow.keys().forEach { columnName ->
                row[columnName] = sqlRow.get(columnName)
            }
            results.add(row)
        }

        return results
    }

    private fun executeCompletionActions(report: ReportModel, result: ReportExecutionResult) {
        report.completionActions.forEach { action ->
            try {
                when (action) {
                    is ReportModel.CompletionAction.S3Upload -> {
                        // TODO: Implement S3 upload
                        logger.info("S3 upload action executed for report: ${report.name} to bucket: ${action.bucketName}")
                    }

                    is ReportModel.CompletionAction.SftpUpload -> {
                        // TODO: Implement SFTP upload
                        logger.info("SFTP upload action executed for report: ${report.name} to ${action.host}:${action.remotePath}")
                    }

                    is ReportModel.CompletionAction.Email -> {
                        // TODO: Implement email sending
                        logger.info("Email action executed for report: ${report.name} to ${action.to.joinToString()}")
                    }
                }
            } catch (e: Exception) {
                logger.error(
                    "Error executing completion action ${action::class.simpleName} for report ${report.name}",
                    e
                )
            }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return email.matches(Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"))
    }
}