package my.baas.models

import io.ebean.annotation.Index
import io.ebean.annotation.TenantId
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PrePersist
import my.baas.auth.CurrentUser

@MappedSuperclass
abstract class BaseTenantModel : BaseModel() {
    
    @TenantId
    @Index
    var tenantId: Long = 0

    @PrePersist
    fun setTenantId() {
        tenantId = CurrentUser.getTenant()?.id ?: throw IllegalStateException("No tenant in context")
    }

}