package my.baas.models

import com.fasterxml.jackson.annotation.JsonIgnore
import io.ebean.annotation.DbJsonB
import io.ebean.annotation.Index
import jakarta.persistence.Entity
import jakarta.persistence.ManyToOne

@Entity
@Index(name = "data_model_unique_identifier", unique = true, columnNames = ["unique_identifier", "tenant_id", "application_id", "schema_id"])
class DataModel(

    @ManyToOne
    @Index
    @JsonIgnore
    var schema: SchemaModel,

    @DbJsonB
    var data: Map<String, Any>,

    var uniqueIdentifier: String,

    var entityName: String,

    var versionName: String,

) : BaseAppModel() {

    @JsonIgnore
    fun loadSchema(): SchemaModel {
        return this.schema
    }
}