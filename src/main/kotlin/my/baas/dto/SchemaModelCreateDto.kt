package my.baas.dto

import my.baas.models.SchemaModel
import my.baas.services.LifecycleEvent

/**
 * DTO for creating a new SchemaModel
 * Generated from SchemaModel, excluding internal/system fields
 */
data class SchemaModelCreateDto(
    val entityName: String,
    val jsonSchema: Map<String, Any>,
    val versionName: String,
    val uniqueIdentifierFormatter: String,
    val indexedJsonPaths: Map<String, Boolean> = emptyMap(),
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
        )
    }
}