package my.baas.models

import io.ebean.annotation.Index
import io.ebean.annotation.TenantId
import jakarta.persistence.Column
import jakarta.persistence.ManyToOne
import jakarta.persistence.MappedSuperclass

@MappedSuperclass
abstract class BaseTenantModel : BaseModel() {
    
    @TenantId
    @Column(nullable = false)
    @Index
    var tenantId: Long = 0
    
    @ManyToOne(optional = false)
    lateinit var tenant: TenantModel
}