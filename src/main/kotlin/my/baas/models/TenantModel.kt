package my.baas.models

import io.ebean.annotation.DbJsonB
import io.ebean.annotation.Index
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Index(columnNames = ["domain"], unique = true)
class TenantModel(
    
    var name: String,
    
    var domain: String,
    
    var description: String? = null,
    
    var isActive: Boolean = true,
    
    @DbJsonB
    var settings: Map<String, Any> = hashMapOf(),
    
    @DbJsonB
    var config: TenantConfiguration = TenantConfiguration(),
    
    @DbJsonB
    var allowedIps: List<String>? = null
    
) : BaseModel()

data class TenantConfiguration(
    val maxSchemas: Int = 50,
    val maxReports: Int = 100,
    val jobRetentionDays: Int = 30,
    val jwksUri: String? = null,
    val maxReportExecutionTimeMinutes: Long = 60
)