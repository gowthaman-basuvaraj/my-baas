package my.baas.dto

import my.baas.models.SchemaModel
import my.baas.services.LifecycleEvent
import java.time.Instant

/**
 * DTO for viewing a SchemaModel
 * Generated from SchemaModel, excluding sensitive/internal fields
 */
data class SchemaModelViewDto(
    val id: Long,
    val entityName: String,
    val jsonSchema: Map<String, Any>,
    val versionName: String,
    val uniqueIdentifierFormatter: String,
    val indexedJsonPaths: List<String>,
    val lifecycleScripts: Map<LifecycleEvent, String>,
    val isValidationEnabled: Boolean,
    val whenCreated: Instant,
    val whenModified: Instant
) {
    companion object {
        fun fromModel(model: SchemaModel): SchemaModelViewDto {
            return SchemaModelViewDto(
                id = model.id,
                entityName = model.entityName,
                jsonSchema = model.jsonSchema,
                versionName = model.versionName,
                uniqueIdentifierFormatter = model.uniqueIdentifierFormatter,
                indexedJsonPaths = model.indexedJsonPaths,
                lifecycleScripts = model.lifecycleScripts,
                isValidationEnabled = model.isValidationEnabled,
                whenCreated = model.whenCreated,
                whenModified = model.whenModified
            )
        }
    }
}