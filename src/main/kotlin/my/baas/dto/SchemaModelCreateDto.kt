package my.baas.dto

import my.baas.models.SchemaModel
import my.baas.services.LifecycleEvent
import java.util.UUID

/**
 * DTO for creating a new SchemaModel
 * Generated from SchemaModel, excluding internal/system fields
 */
data class SchemaModelUpdateDto(
    val id: UUID? = null,
    val jsonSchema: Map<String, Any>,
    val versionName: String,
    val indexedJsonPaths: List<String> = emptyList(),
    val lifecycleScripts: Map<LifecycleEvent, String> = emptyMap(),
    val isValidationEnabled: Boolean = true
)
data class SchemaModelCreateDto(
    val id: UUID? = null,
    val entityName: String,
    val jsonSchema: Map<String, Any>,
    val versionName: String,
    val uniqueIdentifierFormatter: String,
    val indexedJsonPaths: List<String> = emptyList(),
    val lifecycleScripts: Map<LifecycleEvent, String> = emptyMap(),
    val isValidationEnabled: Boolean = true
) {
    fun toModel(): SchemaModel {
        return SchemaModel(
            entityName = this.entityName,
            jsonSchema = this.jsonSchema,
            versionName = this.versionName,
            uniqueIdentifierFormatter = this.uniqueIdentifierFormatter,
            indexedJsonPaths = this.indexedJsonPaths,
            lifecycleScripts = this.lifecycleScripts,
            isValidationEnabled = this.isValidationEnabled
        ).apply {
            this.id = this@SchemaModelCreateDto.id ?: UUID.randomUUID()
        }
    }
}