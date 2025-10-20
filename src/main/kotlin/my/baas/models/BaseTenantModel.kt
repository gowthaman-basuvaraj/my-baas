package my.baas.models

import io.ebean.annotation.Index
import io.ebean.annotation.TenantId
import jakarta.persistence.ManyToOne
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PrePersist
import my.baas.auth.CurrentUser

@MappedSuperclass
abstract class BaseTenantModel : BaseModel() {
    
    @TenantId
    @Index
    @ManyToOne
    lateinit var tenant: TenantModel

    @PrePersist
    fun setTenantId() {
        tenant = CurrentUser.getTenant() ?: throw IllegalStateException("No tenant in context")
    }

}