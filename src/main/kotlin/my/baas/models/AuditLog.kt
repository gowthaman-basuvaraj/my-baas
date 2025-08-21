package my.baas.models

import io.ebean.annotation.DbJsonB
import io.ebean.annotation.Index
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.EnumType
import java.time.Instant

enum class AuditAction {
    CREATE,
    UPDATE,
    PATCH,
    DELETE,
    MIGRATE,
    RE_INDEX
}

@Entity
@Index(columnNames = ["tenant_id", "entity_name", "action", "created_at", "unique_identifier", "user_id"])
class AuditLog(
    
    @Column(nullable = false)
    var entityName: String,
    
    @Column(nullable = false)
    var uniqueIdentifier: String,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var action: AuditAction,
    
    @Column(nullable = false)
    var userId: String,
    
    @Column(nullable = false)
    var clientIp: String,
    
    @Column(nullable = false)
    var createdAt: Instant = Instant.now(),
    
    @DbJsonB
    @Column(nullable = true)
    var oldData: Map<String, Any>? = null,
    
    @DbJsonB
    @Column(nullable = true)
    var newData: Map<String, Any>? = null,
    
    @Column(nullable = true)
    var notes: String? = null

) : BaseTenantModel()