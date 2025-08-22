package my.baas.models

import io.ebean.annotation.Index
import io.ebean.annotation.TenantId
import jakarta.persistence.ManyToOne
import jakarta.persistence.MappedSuperclass

@MappedSuperclass
abstract class BaseTenantModel : BaseModel() {
    
    @TenantId
    @Index
    var tenantId: Long = 0

}