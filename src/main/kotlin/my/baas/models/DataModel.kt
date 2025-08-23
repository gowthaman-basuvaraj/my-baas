package my.baas.models

import io.ebean.annotation.DbJsonB
import io.ebean.annotation.Index
import jakarta.persistence.Entity
import jakarta.persistence.ManyToOne

@Entity
@Index(columnNames = ["unique_identifier", "entity_name", "version_name", "tenant_id"], unique = true)
@Index(columnNames = ["data"], definition = "CREATE INDEX search_gin_data ON data_model USING GIN(data jsonb_path_ops)")
class DataModel(

    @ManyToOne
    var schema: SchemaModel,

    @DbJsonB
    var data: Map<String, Any>,

    var uniqueIdentifier: String,

    var entityName: String,

    var versionName: String

) : BaseTenantModel()