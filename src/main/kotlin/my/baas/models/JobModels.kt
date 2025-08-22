package my.baas.models

import java.time.Instant

data class JobSubmissionResponse(
    val jobId: String,
    val reportId: Long,
    val reportName: String,
    val status: ReportExecutionLog.JobStatus,
    val submittedAt: Instant,
    val estimatedCompletionTime: Instant? = null
)

data class JobStatusResponse(
    val jobId: String,
    val reportId: Long,
    val reportName: String,
    val status: ReportExecutionLog.JobStatus,
    val executionType: ReportExecutionLog.ExecutionTrigger,
    val submittedAt: Instant,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val executionTimeMs: Long? = null,
    val rowCount: Int? = null,
    val errorMessage: String? = null,
    val progress: JobProgress? = null,
    val resultAvailable: Boolean = false,
    val resultUrl: String? = null,
    val fileSizeBytes: Long? = null,
    val fileFormat: ReportModel.FileFormat = ReportModel.FileFormat.JSON
)

data class JobProgress(
    val percentage: Int = 0,
    val currentStep: String? = null,
    val totalSteps: Int? = null,
    val processedRows: Long? = null,
    val estimatedTotalRows: Long? = null
)

data class ReportExecutionRequest(
    val reportId: Long,
    val parameters: Map<String, Any> = emptyMap(),
    val outputFormat: ReportModel.FileFormat = ReportModel.FileFormat.JSON,
    val priority: JobPriority = JobPriority.NORMAL
)

data class ScheduledJobInfo(
    val jobId: String,
    val reportId: Long,
    val reportName: String,
    val cronExpression: String,
    val nextExecutionTime: Instant,
    val lastExecutionTime: Instant? = null,
    val lastExecutionStatus: ReportExecutionLog.JobStatus? = null,
    val isActive: Boolean = true
)

enum class JobPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}

data class ReportResultDownloadInfo(
    val jobId: String,
    val fileName: String,
    val contentType: String,
    val fileSizeBytes: Long,
    val isAvailable: Boolean,
    val downloadUrl: String? = null,
    val expiresAt: Instant? = null
)