package my.baas.models

import com.fasterxml.jackson.annotation.JsonIgnore
import io.ebean.annotation.DbJsonB
import io.ebean.annotation.Index
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import my.baas.annotations.GenerateDto
import my.baas.services.LifecycleEvent
import org.apache.commons.lang3.RandomStringUtils
import org.slf4j.LoggerFactory
import java.util.UUID


enum class PartitionBy {
    YEAR, MONTH
}

@Entity
@Index(columnNames = ["entity_name", "version_name", "tenant_id", "application_id"], unique = true)
@GenerateDto(
    createDto = true,
    viewDto = true,
    excludeFromView = ["tenant_id", "application_id", "unique_id"] // Additional exclusion for view DTO
)
class SchemaModel(

    var entityName: String,

    @DbJsonB
    var jsonSchema: Map<String, Any>,

    var versionName: String,

    var uniqueIdentifierFormatter: String,

    @DbJsonB
    var indexedJsonPaths: List<String> = emptyList(),

    @DbJsonB
    var lifecycleScripts: Map<LifecycleEvent, String> = emptyMap(),

    var isValidationEnabled: Boolean = true,

    @Enumerated(EnumType.STRING)
    var partitionBy: PartitionBy = PartitionBy.YEAR,

    //fixme: add a trigger to prevent updating this column
    val uniqueId: String = RandomStringUtils.secure().nextAlphanumeric(5),
) : BaseAppModel() {
    @JsonIgnore
    fun generateTableName(suffix: String): String {
        val tbl =  "data_${uniqueId}_${tenant.name.trim()}_${application.applicationName.trim()}_${entityName.trim()}_$suffix"
            .lowercase()
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .replace(Regex("_{2,}"), "_")
            .trimEnd('_','-')

        logger.info("Generated table name: $tbl")
        return "\"$tbl\""
    }

    companion object{
        private val logger = LoggerFactory.getLogger(SchemaModel::class.java)
    }

}
