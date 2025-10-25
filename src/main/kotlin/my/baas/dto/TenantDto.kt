package my.baas.dto

data class TenantConfigurationUpdateDto(val jwksUri: String? = null)
open class TenantUpdateDto(
    val name: String,
    val configuration: TenantConfigurationUpdateDto,
    val settings: Map<String, Any>,
    val allowedIps: List<String>
)