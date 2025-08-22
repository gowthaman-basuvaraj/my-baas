package my.baas.services

import com.opencsv.CSVWriter
import my.baas.config.AppContext
import my.baas.config.ReportConfig
import my.baas.models.ReportExecutionLog
import my.baas.repositories.ReportExecutionRepository
import my.baas.repositories.ReportExecutionRepositoryImpl
import my.baas.services.TenantLimitService
import org.slf4j.LoggerFactory
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.GetObjectArgs
import io.minio.MakeBucketArgs
import io.minio.BucketExistsArgs
import io.minio.RemoveObjectArgs
import my.baas.models.ReportModel
import java.io.File
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class JobRunnerService(
    private val executionRepository: ReportExecutionRepository = ReportExecutionRepositoryImpl()
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val config = ReportConfig.fromAppConfig(AppContext.appConfig)
    private val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(config.maxConcurrentJobs)
    private val minioClient: MinioClient? = initMinioClient()
    
    init {
        // Ensure local storage directory exists
        Files.createDirectories(config.getLocalStoragePathAsPath())
        
        // Start job processor
        executor.scheduleWithFixedDelay(::processPendingJobs, 0, 5, TimeUnit.SECONDS)
        
        // Start cleanup task
        executor.scheduleWithFixedDelay(::cleanupOldResults, 1, 24, TimeUnit.HOURS)
        
        logger.info("JobRunnerService initialized with ${config.maxConcurrentJobs} max concurrent jobs")
    }

    private fun initMinioClient(): MinioClient? {
        return if (config.enableMinioUpload && config.minioConfig != null) {
            try {
                val client = MinioClient.builder()
                    .endpoint(config.minioConfig.endpoint)
                    .credentials(config.minioConfig.accessKey, config.minioConfig.secretKey)
                    .build()
                
                // Ensure bucket exists
                if (!client.bucketExists(BucketExistsArgs.builder().bucket(config.minioConfig.bucketName).build())) {
                    client.makeBucket(MakeBucketArgs.builder().bucket(config.minioConfig.bucketName).build())
                    logger.info("Created MinIO bucket: ${config.minioConfig.bucketName}")
                }
                
                client
            } catch (e: Exception) {
                logger.error("Failed to initialize MinIO client", e)
                null
            }
        } else null
    }

    private fun processPendingJobs() {
        try {
            val runningJobs = executionRepository.findRunningJobs()
            val availableSlots = config.maxConcurrentJobs - runningJobs.size
            
            if (availableSlots <= 0) {
                return
            }

            val pendingJobs = executionRepository.findPendingJobs()
                .take(availableSlots)

            pendingJobs.forEach { job ->
                CompletableFuture.runAsync(
                    { processJob(job) },
                    executor
                )
            }
        } catch (e: Exception) {
            logger.error("Error processing pending jobs", e)
        }
    }

    private fun processJob(executionLog: ReportExecutionLog) {
        logger.info("Starting job execution: ${executionLog.jobId}")
        
        try {
            // Update status to running
            executionLog.status = ReportExecutionLog.JobStatus.RUNNING
            executionLog.startedAt = Instant.now()
            executionRepository.update(executionLog)

            val startTime = System.currentTimeMillis()

            // Get tenant-specific max execution time
            val maxExecutionTimeMs = executionLog.tenant.config.maxReportExecutionTimeMinutes * 60 * 1000

            // Execute the SQL query with timeout check
            val results = executeSqlQueryWithTimeout(executionLog.report.sql, maxExecutionTimeMs)
            val executionTime = System.currentTimeMillis() - startTime

            // Generate result file
            val localFile = generateResultFile(executionLog, results)
            val fileSize = localFile.length()

            // Update execution log with local file info
            executionLog.localFilePath = localFile.absolutePath
            executionLog.fileSizeBytes = fileSize
            executionLog.rowCount = results.size
            executionLog.executionTimeMs = executionTime
            executionLog.storageLocation = ReportExecutionLog.StorageLocation.LOCAL

            // Upload to MinIO if configured
            if (config.enableMinioUpload && config.minioConfig != null && minioClient != null) {
                uploadToMinio(executionLog, localFile)
            }

            // Execute completion actions
            executeCompletionActions(executionLog, results)

            // Mark as completed
            executionLog.status = ReportExecutionLog.JobStatus.COMPLETED
            executionLog.completedAt = Instant.now()
            executionRepository.update(executionLog)

            logger.info("Job completed successfully: ${executionLog.jobId}, rows: ${results.size}, time: ${executionTime}ms")

        } catch (e: Exception) {
            logger.error("Job execution failed: ${executionLog.jobId}", e)
            
            executionLog.status = ReportExecutionLog.JobStatus.FAILED
            executionLog.completedAt = Instant.now()
            executionLog.errorMessage = e.message ?: "Unknown error occurred"
            executionRepository.update(executionLog)
        }
    }
    
    private fun executeSqlQueryWithTimeout(sql: String, maxExecutionTimeMs: Long): List<Map<String, Any?>> {
        val results = mutableListOf<Map<String, Any?>>()
        
        //todo: set statement_timeout to 60000; commit;
        val sqlQuery = AppContext.db.sqlQuery(sql)

        sqlQuery.findList().forEach { sqlRow ->

            val row = mutableMapOf<String, Any?>()
            sqlRow.keys().forEach { columnName ->
                row[columnName] = sqlRow.get(columnName)
            }
            results.add(row)
        }
        
        return results
    }

    private fun generateResultFile(executionLog: ReportExecutionLog, results: List<Map<String, Any?>>): File {
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(Instant.now())
        val fileName = "${executionLog.report.name}_${timestamp}_${executionLog.jobId}.${executionLog.fileFormat.lowercase()}"
        val file = File(config.getLocalStoragePathAsPath().toFile(), fileName)

        when (executionLog.fileFormat.uppercase()) {
            "CSV" -> generateCsvFile(file, results)
            "JSON" -> generateJsonFile(file, results)
            else -> generateCsvFile(file, results) // Default to CSV
        }

        return file
    }

    private fun generateCsvFile(file: File, results: List<Map<String, Any?>>) {
        if (results.isEmpty()) {
            file.writeText("")
            return
        }

        FileWriter(file).use { writer ->
            CSVWriter(writer).use { csvWriter ->
                // Write header
                val headers = results.first().keys.toTypedArray()
                csvWriter.writeNext(headers)

                // Write data rows
                results.forEach { row ->
                    val values = headers.map { header ->
                        row[header]?.toString() ?: ""
                    }.toTypedArray()
                    csvWriter.writeNext(values)
                }
            }
        }
    }

    private fun generateJsonFile(file: File, results: List<Map<String, Any?>>) {
        val json = AppContext.objectMapper.writeValueAsString(
            mapOf(
                "data" to results,
                "count" to results.size,
                "generatedAt" to Instant.now().toString()
            )
        )
        file.writeText(json)
    }

    private fun uploadToMinio(executionLog: ReportExecutionLog, file: File) {
        if (minioClient == null || config.minioConfig == null) return

        try {
            val objectKey = "${config.minioConfig.prefix}${executionLog.tenant.domain}/${executionLog.jobId}/${file.name}"
            
            val putObjectArgs = PutObjectArgs.builder()
                .bucket(config.minioConfig.bucketName)
                .`object`(objectKey)

                .contentType(getContentType(executionLog.fileFormat))
                .build()

            minioClient.putObject(putObjectArgs)

            executionLog.s3BucketName = config.minioConfig.bucketName
            executionLog.s3ObjectKey = objectKey

            logger.info("File uploaded to MinIO: ${config.minioConfig.endpoint}/${config.minioConfig.bucketName}/$objectKey")

        } catch (e: Exception) {
            logger.error("Failed to upload file to MinIO for job: ${executionLog.jobId}", e)
        }
    }

    private fun getContentType(fileFormat: String): String {
        return when (fileFormat.uppercase()) {
            "CSV" -> "text/csv"
            "JSON" -> "application/json"
            else -> "application/octet-stream"
        }
    }

    private fun executeCompletionActions(executionLog: ReportExecutionLog, results: List<Map<String, Any?>>) {
        executionLog.report.completionActions.forEach { action ->
            try {
                when (action) {
                    is ReportModel.CompletionAction.S3Upload -> {
                        // Custom MinIO/S3 upload with user's credentials
                        uploadToCustomMinio(action, executionLog, results)
                    }
                    is ReportModel.CompletionAction.SftpUpload -> {
                        // TODO: Implement SFTP upload
                        logger.info("SFTP upload not yet implemented for job: ${executionLog.jobId}")
                    }
                    is ReportModel.CompletionAction.Email -> {
                        // TODO: Implement email sending
                        logger.info("Email sending not yet implemented for job: ${executionLog.jobId}")
                    }
                }
            } catch (e: Exception) {
                logger.error("Error executing completion action for job: ${executionLog.jobId}", e)
            }
        }
    }

    private fun uploadToCustomMinio(action: ReportModel.CompletionAction.S3Upload, executionLog: ReportExecutionLog, results: List<Map<String, Any?>>) {
        try {
            // For S3Upload action, we'll assume it's MinIO-compatible
            // The endpoint should be provided in the region field or use a default MinIO endpoint
            val endpoint = if (action.region?.startsWith("http") == true) {
                action.region
            } else {
                "http://localhost:9000" // Default MinIO endpoint
            }
            
            val customMinioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(action.accessKey, action.secretKey)
                .build()

            val localFile = File(executionLog.localFilePath!!)
            val objectKey = action.filePath ?: "${executionLog.jobId}/${localFile.name}"

            // Ensure bucket exists
            if (!customMinioClient.bucketExists(BucketExistsArgs.builder().bucket(action.bucketName).build())) {
                customMinioClient.makeBucket(MakeBucketArgs.builder().bucket(action.bucketName).build())
            }

            val putObjectArgs = PutObjectArgs.builder()
                .bucket(action.bucketName)
                .`object`(objectKey)
                .contentType(getContentType(executionLog.fileFormat))
                .build()

            customMinioClient.putObject(putObjectArgs)

            logger.info("File uploaded to custom MinIO: $endpoint/${action.bucketName}/$objectKey")

        } catch (e: Exception) {
            logger.error("Failed to upload to custom MinIO for job: ${executionLog.jobId}", e)
        }
    }

    private fun cleanupOldResults() {
        try {
            // Group jobs by tenant to apply their specific retention periods
            val tenantJobs = mutableMapOf<Long, MutableList<ReportExecutionLog>>()
            
            // Get all completed jobs
            val allCompletedJobs = AppContext.db.find(ReportExecutionLog::class.java)
                .where()
                .`in`("status", ReportExecutionLog.JobStatus.COMPLETED, ReportExecutionLog.JobStatus.FAILED)
                .findList()

            // Group by tenant
            allCompletedJobs.forEach { job ->
                tenantJobs.getOrPut(job.tenantId) { mutableListOf() }.add(job)
            }

            var totalCleaned = 0
            
            tenantJobs.forEach { (tenantId, jobs) ->
                try {
                    // Get tenant-specific retention period
                    val tenant = AppContext.db.find(my.baas.models.TenantModel::class.java, tenantId)
                    val retentionDays = tenant?.config?.jobRetentionDays ?: config.resultRetentionDays

                    //let's give leeway of 2 days to account for the timezone differences
                    val cutoffDate = Instant.now().minusSeconds((retentionDays + 2).toLong() * 24 * 60 * 60)
                    val oldJobs = jobs.filter { job -> 
                        job.completedAt != null && job.completedAt!!.isBefore(cutoffDate)
                    }

                    // Handle cleanup for jobs past cutoff date (S3 files only)
                    oldJobs.forEach { job ->
                        try {
                            // Only delete S3 files for jobs past cutoff date
                            if (job.storageLocation == ReportExecutionLog.StorageLocation.S3 || 
                                job.storageLocation == ReportExecutionLog.StorageLocation.BOTH) {
                                deleteFromMinio(job)
                                job.s3BucketName = null
                                job.s3ObjectKey = null

                                executionRepository.update(job)
                                totalCleaned++
                            }

                        } catch (e: Exception) {
                            logger.error("Error cleaning up job: ${job.jobId}", e)
                        }
                    }
                    
                    // Always delete local files if S3 version exists (regardless of cutoff date)
                    val jobsWithBothStorage = jobs.filter { job -> 
                        job.storageLocation == ReportExecutionLog.StorageLocation.BOTH
                    }
                    
                    jobsWithBothStorage.forEach { job ->
                        try {
                            job.localFilePath?.let { path ->
                                val file = File(path)
                                if (file.exists() && file.delete()) {
                                    logger.debug("Deleted local file (S3 version exists): $path")
                                    job.localFilePath = null
                                    executionRepository.update(job)
                                    totalCleaned++
                                }
                            }
                        } catch (e: Exception) {
                            logger.error("Error cleaning up local file for job: ${job.jobId}", e)
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Error processing tenant $tenantId during cleanup", e)
                }
            }

            if (totalCleaned > 0) {
                logger.info("Cleaned up $totalCleaned old result files across all tenants")
            }

        } catch (e: Exception) {
            logger.error("Error during cleanup process", e)
        }
    }
    
    private fun deleteFromMinio(executionLog: ReportExecutionLog) {
        if (minioClient == null || executionLog.s3ObjectKey == null || executionLog.s3BucketName == null) {
            return
        }

        try {
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(executionLog.s3BucketName)
                    .`object`(executionLog.s3ObjectKey)
                    .build()
            )
            
            logger.debug("Deleted old MinIO file: ${executionLog.s3BucketName}/${executionLog.s3ObjectKey}")

        } catch (e: Exception) {
            logger.warn("Failed to delete old MinIO file for job: ${executionLog.jobId}", e)
        }
    }

    fun downloadResultFile(jobId: String): File? {
        val executionLog = executionRepository.findByJobId(jobId) ?: return null

        return when (executionLog.storageLocation) {
            ReportExecutionLog.StorageLocation.LOCAL -> {
                executionLog.localFilePath?.let { File(it) }?.takeIf { it.exists() }
            }
            ReportExecutionLog.StorageLocation.S3 -> {
                downloadFromMinio(executionLog)
            }
            ReportExecutionLog.StorageLocation.BOTH -> {
                // Prefer local file if available
                executionLog.localFilePath?.let { File(it) }?.takeIf { it.exists() }
                    ?: downloadFromMinio(executionLog)
            }
            ReportExecutionLog.StorageLocation.NONE -> null
        }
    }

    private fun downloadFromMinio(executionLog: ReportExecutionLog): File? {
        if (minioClient == null || executionLog.s3ObjectKey == null || executionLog.s3BucketName == null) {
            return null
        }

        try {
            val tempFile = File.createTempFile("report_${executionLog.jobId}", ".tmp")
            
            val getObjectArgs = GetObjectArgs.builder()
                .bucket(executionLog.s3BucketName)
                .`object`(executionLog.s3ObjectKey)
                .build()

            minioClient.getObject(getObjectArgs).use { inputStream ->
                tempFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            return tempFile

        } catch (e: Exception) {
            logger.error("Failed to download file from MinIO for job: ${executionLog.jobId}", e)
            return null
        }
    }

    fun shutdown() {
        logger.info("Shutting down JobRunnerService...")
        executor.shutdown()
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
        }
        // MinioClient doesn't need explicit closing
    }
}

object JobRunnerServiceHolder {
    val instance: JobRunnerService by lazy { JobRunnerService() }
}