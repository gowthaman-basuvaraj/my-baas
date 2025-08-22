package my.baas.dto

import my.baas.models.ReportModel
import java.time.Instant

/**
 * DTO for viewing a ReportModel
 * Generated from ReportModel, excluding sensitive/internal fields
 */
data class ReportModelViewDto(
    val id: Long,
    val name: String,
    val sql: String,
    val executionType: ReportModel.ExecutionType,
    val cronSchedule: String?,
    val fileFormat: ReportModel.FileFormat,
    val completionActions: List<ReportModel.CompletionAction>,
    val isActive: Boolean,
    val whenCreated: Instant,
    val whenModified: Instant
) {
    companion object {
        fun fromModel(model: ReportModel): ReportModelViewDto {
            return ReportModelViewDto(
                id = model.id,
                name = model.name,
                sql = model.sql,
                executionType = model.executionType,
                cronSchedule = model.cronSchedule,
                fileFormat = model.fileFormat,
                completionActions = model.completionActions,
                isActive = model.isActive,
                whenCreated = model.whenCreated,
                whenModified = model.whenModified
            )
        }
    }
}