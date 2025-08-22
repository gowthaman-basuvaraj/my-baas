package my.baas.repositories

import io.ebean.PagedList
import my.baas.config.AppContext
import my.baas.models.ReportExecutionLog
import java.time.Instant

interface ReportExecutionRepository {
    fun save(executionLog: ReportExecutionLog): ReportExecutionLog
    fun findByJobId(jobId: String): ReportExecutionLog?
    fun findById(id: Long): ReportExecutionLog?
    fun findByReportId(reportId: Long, pageNo: Int = 1, pageSize: Int = 20): PagedList<ReportExecutionLog>
    fun findByStatus(status: ReportExecutionLog.JobStatus, pageNo: Int = 1, pageSize: Int = 20): PagedList<ReportExecutionLog>
    fun findPendingJobs(): List<ReportExecutionLog>
    fun findRunningJobs(): List<ReportExecutionLog>
    fun findCompletedJobsOlderThan(cutoffDate: Instant): List<ReportExecutionLog>
    fun findByUserAndStatus(userId: String, status: ReportExecutionLog.JobStatus? = null): List<ReportExecutionLog>
    fun update(executionLog: ReportExecutionLog): ReportExecutionLog
    fun deleteById(id: Long): Boolean
    fun countByReportIdAndStatus(reportId: Long, status: ReportExecutionLog.JobStatus): Long
    fun findRecentExecutions(reportId: Long, limit: Int = 10): List<ReportExecutionLog>
}

class ReportExecutionRepositoryImpl : ReportExecutionRepository {

    override fun save(executionLog: ReportExecutionLog): ReportExecutionLog {
        executionLog.save()
        return executionLog
    }

    override fun findByJobId(jobId: String): ReportExecutionLog? {
        return AppContext.db.find(ReportExecutionLog::class.java)
            .where()
            .eq("jobId", jobId)
            .findOne()
    }

    override fun findById(id: Long): ReportExecutionLog? {
        return AppContext.db.find(ReportExecutionLog::class.java, id)
    }

    override fun findByReportId(reportId: Long, pageNo: Int, pageSize: Int): PagedList<ReportExecutionLog> {
        return AppContext.db.find(ReportExecutionLog::class.java)
            .where()
            .eq("report.id", reportId)
            .orderBy("whenCreated desc")
            .setFirstRow((pageNo - 1) * pageSize)
            .setMaxRows(pageSize)
            .findPagedList()
    }

    override fun findByStatus(status: ReportExecutionLog.JobStatus, pageNo: Int, pageSize: Int): PagedList<ReportExecutionLog> {
        return AppContext.db.find(ReportExecutionLog::class.java)
            .where()
            .eq("status", status)
            .orderBy("whenCreated desc")
            .setFirstRow((pageNo - 1) * pageSize)
            .setMaxRows(pageSize)
            .findPagedList()
    }

    override fun findPendingJobs(): List<ReportExecutionLog> {
        return AppContext.db.find(ReportExecutionLog::class.java)
            .where()
            .eq("status", ReportExecutionLog.JobStatus.PENDING)
            .orderBy("whenCreated asc")
            .findList()
    }

    override fun findRunningJobs(): List<ReportExecutionLog> {
        return AppContext.db.find(ReportExecutionLog::class.java)
            .where()
            .eq("status", ReportExecutionLog.JobStatus.RUNNING)
            .findList()
    }

    override fun findCompletedJobsOlderThan(cutoffDate: Instant): List<ReportExecutionLog> {
        return AppContext.db.find(ReportExecutionLog::class.java)
            .where()
            .`in`("status", ReportExecutionLog.JobStatus.COMPLETED, ReportExecutionLog.JobStatus.FAILED)
            .lt("completedAt", cutoffDate)
            .findList()
    }

    override fun findByUserAndStatus(userId: String, status: ReportExecutionLog.JobStatus?): List<ReportExecutionLog> {
        val query = AppContext.db.find(ReportExecutionLog::class.java)
            .where()
            .eq("requestedBy", userId)

        status?.let { query.eq("status", it) }

        return query.orderBy("whenCreated desc").findList()
    }

    override fun update(executionLog: ReportExecutionLog): ReportExecutionLog {
        executionLog.update()
        return executionLog
    }

    override fun deleteById(id: Long): Boolean {
        return AppContext.db.find(ReportExecutionLog::class.java, id)?.delete() ?: false
    }

    override fun countByReportIdAndStatus(reportId: Long, status: ReportExecutionLog.JobStatus): Long {
        return AppContext.db.find(ReportExecutionLog::class.java)
            .where()
            .eq("report.id", reportId)
            .eq("status", status)
            .findCount().toLong()
    }

    override fun findRecentExecutions(reportId: Long, limit: Int): List<ReportExecutionLog> {
        return AppContext.db.find(ReportExecutionLog::class.java)
            .where()
            .eq("report.id", reportId)
            .orderBy("whenCreated desc")
            .setMaxRows(limit)
            .findList()
    }
}