package my.baas.repositories

import io.ebean.PagedList
import my.baas.config.AppContext
import my.baas.models.ReportModel

interface ReportRepository {
    fun save(report: ReportModel): ReportModel
    fun findById(id: Long): ReportModel?
    fun findAll(pageNo: Int = 1, pageSize: Int = 20): PagedList<ReportModel>
    fun findByName(name: String): ReportModel?
    fun update(report: ReportModel): ReportModel
    fun deleteById(id: Long): Boolean
    fun findScheduledReports(): List<ReportModel>
    fun findActiveReports(): List<ReportModel>
}

class ReportRepositoryImpl : ReportRepository {

    override fun save(report: ReportModel): ReportModel {
        report.save()
        return report
    }

    override fun findById(id: Long): ReportModel? {
        return AppContext.db.find(ReportModel::class.java, id)
    }

    override fun findAll(pageNo: Int, pageSize: Int): PagedList<ReportModel> {
        return AppContext.db.find(ReportModel::class.java)
            .setFirstRow((pageNo - 1) * pageSize)
            .setMaxRows(pageSize)
            .findPagedList()
    }

    override fun findByName(name: String): ReportModel? {
        return AppContext.db.find(ReportModel::class.java)
            .where()
            .eq("name", name)
            .findOne()
    }

    override fun update(report: ReportModel): ReportModel {
        report.update()
        return report
    }

    override fun deleteById(id: Long): Boolean {
        return AppContext.db.find(ReportModel::class.java, id)?.delete() ?: false
    }

    override fun findScheduledReports(): List<ReportModel> {
        return AppContext.db.find(ReportModel::class.java)
            .where()
            .`in`("executionType", ReportModel.ExecutionType.SCHEDULED, ReportModel.ExecutionType.BOTH)
            .eq("isActive", true)
            .isNotNull("cronSchedule")
            .findList()
    }

    override fun findActiveReports(): List<ReportModel> {
        return AppContext.db.find(ReportModel::class.java)
            .where()
            .eq("isActive", true)
            .findList()
    }
}