package my.baas.repositories

import io.ebean.PagedList
import my.baas.config.AppContext
import my.baas.models.ReportModel


object ReportRepository {

    fun save(report: ReportModel): ReportModel {
        AppContext.db.insert(report)
        return report
    }

    fun findById(id: Long): ReportModel? {
        return AppContext.db.find(ReportModel::class.java, id)
    }

    fun findAll(pageNo: Int, pageSize: Int): PagedList<ReportModel> {
        return AppContext.db.find(ReportModel::class.java)
            .setFirstRow((pageNo - 1) * pageSize)
            .setMaxRows(pageSize)
            .findPagedList()
    }

    fun findByName(name: String): ReportModel? {
        return AppContext.db.find(ReportModel::class.java)
            .where()
            .eq("name", name)
            .findOne()
    }

    fun update(report: ReportModel): ReportModel {
        report.update()
        return report
    }

    fun deleteById(id: Long): Boolean {
        return AppContext.db.find(ReportModel::class.java, id)?.delete() ?: false
    }

    fun findScheduledReports(): List<ReportModel> {
        return AppContext.db.find(ReportModel::class.java)
            .where()
            .`in`("executionType", ReportModel.ExecutionType.SCHEDULED, ReportModel.ExecutionType.BOTH)
            .eq("isActive", true)
            .isNotNull("cronSchedule")
            .findList()
    }

    fun findActiveReports(): List<ReportModel> {
        return AppContext.db.find(ReportModel::class.java)
            .where()
            .eq("isActive", true)
            .findList()
    }
}