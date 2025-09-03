package my.baas.models

import com.fasterxml.jackson.annotation.JsonIgnore
import io.ebean.annotation.DbJsonB
import io.ebean.annotation.Sql
import jakarta.persistence.Entity
import jakarta.persistence.ManyToOne
import my.baas.config.AppContext

@Entity
@Sql
class DataModel(

    var schemaId: Long,

    @DbJsonB
    var data: Map<String, Any>,

    var uniqueIdentifier: String,

    var entityName: String,

    var versionName: String

) : BaseTenantModel() {

    @JsonIgnore
    fun loadSchema(): SchemaModel {
        return AppContext.db.find(SchemaModel::class.java, schemaId)!!
    }
}