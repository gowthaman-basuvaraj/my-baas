package my.baas.models

import io.ebean.annotation.DbJsonB
import io.ebean.annotation.Index
import jakarta.persistence.Entity
import my.baas.annotations.GenerateDto
import my.baas.services.LifecycleEvent


@Entity
@Index(columnNames = ["entity_name", "version_name", "tenant_id"], unique = true)
@GenerateDto(
    createDto = true,
    viewDto = true,
    excludeFromView = ["tenant_id"] // Additional exclusion for view DTO
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

    var isValidationEnabled: Boolean = true

) : BaseTenantModel() {

    fun generateTableName(tenantId: Long): String {
        return generateTableName(tenantId, entityName)
    }
    
    companion object {
        fun generateTableName(tenantId: Long, entityName: String): String {
            return "data_model_${tenantId}_${entityName.lowercase()}"
        }
    }
}
