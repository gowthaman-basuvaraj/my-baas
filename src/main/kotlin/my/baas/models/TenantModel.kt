package my.baas.models

import io.ebean.annotation.DbJsonB
import io.ebean.annotation.Index
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "tenants")
@Index(columnNames = ["domain"], unique = true)
@Index(columnNames = ["name"], unique = true)
class TenantModel(
    
    @Column(nullable = false, unique = true)
    var name: String,
    
    @Column(nullable = false, unique = true)
    var domain: String,
    
    @Column(nullable = true)
    var description: String? = null,
    
    @Column(nullable = false)
    var isActive: Boolean = true,
    
    @DbJsonB
    @Column(nullable = true)
    var settings: Map<String, Any>? = null,
    
    @DbJsonB
    @Column(nullable = true)
    var allowedIps: List<String>? = null
    
) : BaseModel()