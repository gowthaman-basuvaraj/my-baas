package my.baas.dto

import my.baas.models.ReportModel

/**
 * DTO for creating a new ReportModel
 * Generated from ReportModel, excluding internal/system fields
 */
data class ReportModelCreateDto(
    val name: String,
    val sql: String,
    val executionType: ReportModel.ExecutionType,
    val cronSchedule: String? = null,
    val fileFormat: ReportModel.FileFormat = ReportModel.FileFormat.CSV,
    val completionActions: List<ReportModel.CompletionAction> = emptyList(),
    val isActive: Boolean = true
) {
    fun toModel(): ReportModel {
        return ReportModel().apply {
            this.name = this@ReportModelCreateDto.name
            this.sql = this@ReportModelCreateDto.sql
            this.executionType = this@ReportModelCreateDto.executionType
            this.cronSchedule = this@ReportModelCreateDto.cronSchedule
            this.fileFormat = this@ReportModelCreateDto.fileFormat
            this.completionActions = this@ReportModelCreateDto.completionActions
            this.isActive = this@ReportModelCreateDto.isActive
        }
    }
}