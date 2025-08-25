package my.baas.models

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.ebean.annotation.DbJsonB
import io.ebean.annotation.Index
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import my.baas.annotations.GenerateDto
import my.baas.models.json.CompletionActionDeserializer

@Entity
@Index(name = "report_name_idx", columnNames = ["name", "tenant_id"], unique = true)
@GenerateDto(
    createDto = true,
    viewDto = true,
    excludeFromView = ["tenant_id"] // Additional exclusion for view DTO
)
class ReportModel : BaseTenantModel() {

    @Column(nullable = false)
    lateinit var name: String

    @Column(nullable = false, columnDefinition = "TEXT")
    lateinit var sql: String

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    lateinit var executionType: ExecutionType

    var cronSchedule: String? = null

    @DbJsonB
    var completionActions: List<CompletionAction> = emptyList()

    var isActive: Boolean = true

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var fileFormat: FileFormat = FileFormat.CSV

    @DbJsonB
    var parameters: List<ReportParameter> = emptyList()

    data class ReportParameter(val name: String, val dynamic: Boolean, val value: String? = null)

    enum class ExecutionType {
        ADHOC,
        SCHEDULED,
        BOTH
    }

    enum class FileFormat {
        CSV, JSON, XLS, XLSX;

        fun getContentType(): String {
            return when (this) {
                CSV -> "text/csv"
                JSON -> "application/json"
                XLS -> "application/vnd.ms-excel"
                XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            }
        }

        fun fileExtension() = when (this) {
            CSV -> "csv"
            JSON -> "json"
            XLS -> "xls"
            XLSX -> "xlsx"
        }
    }

    enum class ActionType {
        S3, SFTP, EMAIL
    }

    @JsonDeserialize(using = CompletionActionDeserializer::class)
    sealed class CompletionAction(open val actionType: ActionType) {

        data class S3Upload(
            override val actionType: ActionType,
            val bucketName: String,
            val accessKey: String,
            val secretKey: String,
            val region: String? = null,
            val filePath: String? = null
        ) : CompletionAction(actionType)

        data class SftpUpload(
            override val actionType: ActionType,
            val host: String,
            val port: Int = 22,
            val username: String,
            val password: String? = null,
            val sshKey: String? = null,
            val remotePath: String
        ) : CompletionAction(actionType) {
            init {
                require(password != null || sshKey != null) {
                    "Either password or SSH key must be provided for SFTP upload"
                }
            }
        }

        data class Email(
            override val actionType: ActionType,
            val to: List<String>,
            val cc: List<String> = emptyList(),
            val bcc: List<String> = emptyList(),
            val subject: String,
            val body: String? = null,
            val attachFile: Boolean = true
        ) : CompletionAction(actionType)
    }
}