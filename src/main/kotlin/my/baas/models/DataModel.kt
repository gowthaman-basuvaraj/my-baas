package my.baas.models

import io.ebean.annotation.DbJsonB
import io.ebean.annotation.Sql
import jakarta.persistence.ManyToOne

@Sql
class DataModel(

    @ManyToOne
    var schema: SchemaModel,

    @DbJsonB
    var data: Map<String, Any>,

    var uniqueIdentifier: String,

    var entityName: String,

    var versionName: String

) : BaseTenantModel()