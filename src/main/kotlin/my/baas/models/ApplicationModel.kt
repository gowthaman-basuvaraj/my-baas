package my.baas.models

import io.ebean.annotation.Index
import io.ebean.annotation.Length
import jakarta.persistence.Column
import jakarta.persistence.Entity
import my.baas.annotations.GenerateDto

@Entity
@Index(columnNames = ["application_name", "tenant_id"], unique = true)
@GenerateDto(
    createDto = true,
    viewDto = true,
    excludeFromView = ["tenant_id"]
)
class ApplicationModel(
    var applicationName: String,
    var description: String? = null,
    var isActive: Boolean = true
) : BaseTenantModel()