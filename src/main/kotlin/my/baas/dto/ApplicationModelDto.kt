package my.baas.dto

import my.baas.models.ApplicationModel

data class ApplicationModelCreateDto(
    val applicationName: String,
    val description: String? = null,
    val isActive: Boolean = true
) {
    fun toModel(): ApplicationModel {
        return ApplicationModel(
            applicationName = applicationName,
            description = description,
            isActive = isActive
        )
    }
}

data class ApplicationModelViewDto(
    val id: Long?,
    val applicationName: String,
    val description: String?,
    val isActive: Boolean,
    val whenCreated: java.time.Instant?,
    val whenModified: java.time.Instant?,
    val whoCreated: String?,
    val whoModified: String?,
    val version: Int?
) {
    companion object {
        fun fromModel(model: ApplicationModel): ApplicationModelViewDto {
            return ApplicationModelViewDto(
                id = model.id,
                applicationName = model.applicationName,
                description = model.description,
                isActive = model.isActive,
                whenCreated = model.whenCreated,
                whenModified = model.whenModified,
                whoCreated = model.whoCreated,
                whoModified = model.whoModified,
                version = model.version
            )
        }
    }
}