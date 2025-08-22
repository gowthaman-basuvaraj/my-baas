package my.baas.services

import com.fasterxml.jackson.databind.ObjectMapper
import io.ebean.PagedList
import io.javalin.http.BadRequestResponse
import io.javalin.http.NotFoundResponse
import my.baas.auth.CurrentUser
import my.baas.config.AppContext
import my.baas.models.*
import my.baas.repositories.ReportExecutionRepository
import my.baas.repositories.ReportExecutionRepositoryImpl
import my.baas.repositories.ReportRepository
import my.baas.repositories.ReportRepositoryImpl
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*

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
    private val executionRepository: ReportExecutionRepository = ReportExecutionRepositoryImpl(),
    private val objectMapper: ObjectMapper = AppContext.objectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun createReport(report: ReportModel): ReportModel {
        // Validate tenant limits before creating report
        TenantLimitService.validateReportCreation()

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
        existingReport.fileFormat = updatedReport.fileFormat
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


    fun getScheduledReports(): List<ReportModel> {
        return repository.findScheduledReports()
    }


    // New job-based execution methods
    fun submitReportJob(request: ReportExecutionRequest): JobSubmissionResponse {
        val report = repository.findById(request.reportId)
            ?: throw NotFoundResponse("Report not found with id: ${request.reportId}")

        if (!report.isActive) {
            throw BadRequestResponse("Report '${report.name}' is not active")
        }

        val jobId = UUID.randomUUID().toString()
        val currentUser = CurrentUser.get()

        val executionLog = ReportExecutionLog().apply {
            this.jobId = jobId
            this.report = report
            this.status = ReportExecutionLog.JobStatus.PENDING
            this.executionType = ReportExecutionLog.ExecutionTrigger.API_REQUEST
            this.requestedBy = currentUser.userId
            this.tenantId = CurrentUser.get().tenant?.id ?: throw BadRequestResponse("tenant not found")
            this.fileFormat = request.outputFormat
            this.executionMetadata = mapOf(
                "parameters" to request.parameters,
                "priority" to request.priority.name,
                "submittedAt" to Instant.now().toString()
            )
        }

        executionRepository.save(executionLog)

        return JobSubmissionResponse(
            jobId = jobId,
            reportId = report.id,
            reportName = report.name,
            status = ReportExecutionLog.JobStatus.PENDING,
            submittedAt = executionLog.whenCreated
        )
    }

    fun getJobStatus(jobId: String): JobStatusResponse {
        val executionLog = executionRepository.findByJobId(jobId)
            ?: throw NotFoundResponse("Job not found with id: $jobId")

        return JobStatusResponse(
            jobId = executionLog.jobId,
            reportId = executionLog.report.id,
            reportName = executionLog.report.name,
            status = executionLog.status,
            executionType = executionLog.executionType,
            submittedAt = executionLog.whenCreated,
            startedAt = executionLog.startedAt,
            completedAt = executionLog.completedAt,
            executionTimeMs = executionLog.executionTimeMs,
            rowCount = executionLog.rowCount,
            errorMessage = executionLog.errorMessage,
            resultAvailable = executionLog.isCompleted() && executionLog.storageLocation != ReportExecutionLog.StorageLocation.NONE,
            resultUrl = executionLog.getResultUrl(),
            fileSizeBytes = executionLog.fileSizeBytes,
            fileFormat = executionLog.fileFormat
        )
    }

    fun getExecutionHistory(reportId: Long, pageNo: Int = 1, pageSize: Int = 20): PagedList<ReportExecutionLog> {
        return executionRepository.findByReportId(reportId, pageNo, pageSize)
    }


    fun cancelJob(jobId: String): Boolean {
        val executionLog = executionRepository.findByJobId(jobId)
            ?: throw NotFoundResponse("Job not found with id: $jobId")

        if (executionLog.status !in listOf(
                ReportExecutionLog.JobStatus.PENDING,
                ReportExecutionLog.JobStatus.RUNNING
            )
        ) {
            throw BadRequestResponse("Cannot cancel job with status: ${executionLog.status}")
        }

        executionLog.status = ReportExecutionLog.JobStatus.CANCELLED
        executionLog.completedAt = Instant.now()
        executionRepository.update(executionLog)

        return true
    }

    fun getJobDownloadInfo(jobId: String): ReportResultDownloadInfo {
        val executionLog = executionRepository.findByJobId(jobId)
            ?: throw NotFoundResponse("Job not found with id: $jobId")

        if (!executionLog.isCompleted()) {
            throw BadRequestResponse("Job is not completed yet")
        }

        val fileName = "${executionLog.report.name}_${executionLog.jobId}.${executionLog.fileFormat.fileExtension()}"


        return ReportResultDownloadInfo(
            jobId = executionLog.jobId,
            fileName = fileName,
            contentType = executionLog.fileFormat.getContentType(),
            fileSizeBytes = executionLog.fileSizeBytes ?: 0,
            isAvailable = executionLog.getResultUrl() != null,
            downloadUrl = executionLog.getResultUrl()
        )
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


    private fun isValidEmail(email: String): Boolean {
        return email.matches(Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"))
    }
}