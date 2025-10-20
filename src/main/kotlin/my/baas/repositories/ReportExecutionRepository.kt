package my.baas.repositories

import io.ebean.PagedList
import my.baas.config.AppContext
import my.baas.models.ReportExecutionLog
import java.time.Instant
import java.util.UUID


object ReportExecutionRepository {

     fun save(executionLog: ReportExecutionLog): ReportExecutionLog {
        executionLog.save()
        return executionLog
    }

     fun findByJobId(jobId: String): ReportExecutionLog? {
        return AppContext.db.find(ReportExecutionLog::class.java)
            .where()
            .eq("jobId", jobId)
            .findOne()
    }

     fun findById(id: UUID): ReportExecutionLog? {
        return AppContext.db.find(ReportExecutionLog::class.java, id)
    }

     fun findByReportId(reportId: UUID, pageNo: Int, pageSize: Int): PagedList<ReportExecutionLog> {
        return AppContext.db.find(ReportExecutionLog::class.java)
            .where()
            .eq("report.id", reportId)
            .orderBy("whenCreated desc")
            .setFirstRow((pageNo - 1) * pageSize)
            .setMaxRows(pageSize)
            .findPagedList()
    }

     fun findByStatus(status: ReportExecutionLog.JobStatus, pageNo: Int, pageSize: Int): PagedList<ReportExecutionLog> {
        return AppContext.db.find(ReportExecutionLog::class.java)
            .where()
            .eq("status", status)
            .orderBy("whenCreated desc")
            .setFirstRow((pageNo - 1) * pageSize)
            .setMaxRows(pageSize)
            .findPagedList()
    }

     fun findPendingJobs(): List<ReportExecutionLog> {
        return AppContext.db.find(ReportExecutionLog::class.java)
            .where()
            .eq("status", ReportExecutionLog.JobStatus.PENDING)
            .orderBy("whenCreated asc")
            .findList()
    }

     fun findRunningJobs(): List<ReportExecutionLog> {
        return AppContext.db.find(ReportExecutionLog::class.java)
            .where()
            .eq("status", ReportExecutionLog.JobStatus.RUNNING)
            .findList()
    }

     fun findCompletedJobsOlderThan(cutoffDate: Instant): List<ReportExecutionLog> {
        return AppContext.db.find(ReportExecutionLog::class.java)
            .where()
            .`in`("status", ReportExecutionLog.JobStatus.COMPLETED, ReportExecutionLog.JobStatus.FAILED)
            .lt("completedAt", cutoffDate)
            .findList()
    }

     fun findByUserAndStatus(userId: String, status: ReportExecutionLog.JobStatus?): List<ReportExecutionLog> {
        val query = AppContext.db.find(ReportExecutionLog::class.java)
            .where()
            .eq("requestedBy", userId)

        status?.let { query.eq("status", it) }

        return query.orderBy("whenCreated desc").findList()
    }

     fun update(executionLog: ReportExecutionLog): ReportExecutionLog {
        executionLog.update()
        return executionLog
    }

     fun deleteById(id: UUID): Boolean {
        return AppContext.db.find(ReportExecutionLog::class.java, id)?.delete() ?: false
    }

     fun countByReportIdAndStatus(reportId: UUID, status: ReportExecutionLog.JobStatus): Long {
        return AppContext.db.find(ReportExecutionLog::class.java)
            .where()
            .eq("report.id", reportId)
            .eq("status", status)
            .findCount().toLong()
    }

     fun findRecentExecutions(reportId: UUID, limit: Int): List<ReportExecutionLog> {
        return AppContext.db.find(ReportExecutionLog::class.java)
            .where()
            .eq("report.id", reportId)
            .orderBy("whenCreated desc")
            .setMaxRows(limit)
            .findList()
    }
}