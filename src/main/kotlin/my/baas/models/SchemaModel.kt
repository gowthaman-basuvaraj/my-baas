package my.baas.models

import my.baas.annotations.GenerateDto
import my.baas.services.LifecycleEvent
import io.ebean.annotation.DbJsonB
import io.ebean.annotation.Index
import jakarta.persistence.Entity


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
    var indexedJsonPaths: Map<String, Boolean> = emptyMap(),

    @DbJsonB
    var lifecycleScripts: Map<LifecycleEvent, String> = emptyMap(),

    var isValidationEnabled: Boolean = true

) : BaseTenantModel()
