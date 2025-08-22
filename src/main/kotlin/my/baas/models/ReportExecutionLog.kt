package my.baas.models

import io.ebean.annotation.DbJson
import io.ebean.annotation.Index
import jakarta.persistence.*
import java.time.Instant

@Entity
@Index(name = "report_execution_job_id_idx", columnNames = ["job_id"], unique = true)
@Index(name = "report_execution_report_id_idx", columnNames = ["report_id"])
@Index(name = "report_execution_status_idx", columnNames = ["status", "storage_location"])
class ReportExecutionLog : BaseTenantModel() {

    lateinit var jobId: String

    lateinit var report: ReportModel

    @Enumerated(EnumType.STRING)
    var status: JobStatus = JobStatus.PENDING

    @Enumerated(EnumType.STRING)
    lateinit var executionType: ExecutionTrigger

    var startedAt: Instant? = null
    var completedAt: Instant? = null
    var executionTimeMs: Long? = null

    @Column(columnDefinition = "TEXT")
    var errorMessage: String? = null

    var rowCount: Int? = null

    var localFilePath: String? = null

    var s3BucketName: String? = null

    var s3ObjectKey: String? = null

    var fileSizeBytes: Long? = null

    @Enumerated(EnumType.STRING)
    var fileFormat: ReportModel.FileFormat = ReportModel.FileFormat.JSON

    @Enumerated(EnumType.STRING)
    var storageLocation: StorageLocation = StorageLocation.NONE

    @DbJson
    var executionMetadata: Map<String, Any> = emptyMap()

    var requestedBy: String? = null
    var schedulerJobId: String? = null

    enum class JobStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    enum class ExecutionTrigger {
        MANUAL,
        SCHEDULED,
        API_REQUEST
    }

    enum class StorageLocation {
        NONE,      // No files stored
        LOCAL,     // Only local file exists
        S3,        // Only S3/MinIO file exists  
        BOTH       // Both local and S3/MinIO files exist
    }

    @PrePersist
    @PreUpdate
    private fun updateStorageLocation() {
        storageLocation = when {
            localFilePath != null && s3ObjectKey != null -> StorageLocation.BOTH
            s3ObjectKey != null -> StorageLocation.S3
            localFilePath != null -> StorageLocation.LOCAL
            else -> StorageLocation.NONE
        }
    }

    fun isCompleted(): Boolean = status == JobStatus.COMPLETED
    fun isFailed(): Boolean = status == JobStatus.FAILED
    fun isRunning(): Boolean = status == JobStatus.RUNNING
    fun isPending(): Boolean = status == JobStatus.PENDING

    fun getResultUrl(): String? {
        return when (storageLocation) {
            StorageLocation.S3, StorageLocation.BOTH ->
                s3ObjectKey?.let { "s3://$s3BucketName/$it" }

            StorageLocation.LOCAL -> localFilePath
            StorageLocation.NONE -> null
        }
    }
}