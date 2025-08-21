package my.baas.models

import my.baas.services.LifecycleEvent
import io.ebean.annotation.DbJsonB
import io.ebean.annotation.Index
import jakarta.persistence.Entity


@Entity
@Index(columnNames = ["entity_name", "version_name"], unique = true)
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
