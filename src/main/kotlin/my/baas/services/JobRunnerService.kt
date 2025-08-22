package my.baas.services

import com.opencsv.CSVWriter
import io.ebean.SqlQuery
import io.minio.*
import my.baas.config.AppContext
import my.baas.config.ReportConfig
import my.baas.models.ReportExecutionLog
import my.baas.models.ReportModel
import my.baas.repositories.ReportExecutionRepository
import my.baas.repositories.ReportExecutionRepositoryImpl
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
                val client = MinioClient.builder().endpoint(config.minioConfig.endpoint)
                    .credentials(config.minioConfig.accessKey, config.minioConfig.secretKey).build()

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

            val pendingJobs = executionRepository.findPendingJobs().take(availableSlots)

            pendingJobs.forEach { job ->
                CompletableFuture.runAsync(
                    { processJob(job) }, executor
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

            // Update execution log to use report's file format
            executionLog.fileFormat = executionLog.report.fileFormat

            // Execute SQL query and generate file in streaming manner
            val (rowCount, localFile) = generateResultFileStreamingly(executionLog, maxExecutionTimeMs)
            val executionTime = System.currentTimeMillis() - startTime

            executionLog.rowCount = rowCount
            executionLog.localFilePath = localFile.absolutePath
            executionLog.fileSizeBytes = localFile.length()
            executionLog.executionTimeMs = executionTime
            executionLog.storageLocation = ReportExecutionLog.StorageLocation.LOCAL

            // Upload to MinIO if configured
            if (config.enableMinioUpload && config.minioConfig != null && minioClient != null) {
                val (s3BucketName, s3ObjectKey) = uploadToMinio(executionLog, localFile)
                executionLog.s3BucketName = s3BucketName
                executionLog.s3ObjectKey = s3ObjectKey
            }

            // Execute completion actions (note: we can't pass results anymore due to streaming)
            executeCompletionActions(executionLog)

            // Mark as completed
            executionLog.status = ReportExecutionLog.JobStatus.COMPLETED
            executionLog.completedAt = Instant.now()
            executionRepository.update(executionLog)

            logger.info("Job completed successfully: ${executionLog.jobId}, rows: ${executionLog.rowCount}, time: ${executionTime}ms")

        } catch (e: Exception) {
            logger.error("Job execution failed: ${executionLog.jobId}", e)

            executionLog.status = ReportExecutionLog.JobStatus.FAILED
            executionLog.completedAt = Instant.now()
            executionLog.errorMessage = e.message ?: "Unknown error occurred"
            executionRepository.update(executionLog)
        }
    }

    private fun generateResultFileStreamingly(
        executionLog: ReportExecutionLog,
        maxExecutionTimeMs: Long
    ): Pair<Int, File> {
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(Instant.now())
        val fileExtension = when (executionLog.report.fileFormat) {
            ReportModel.FileFormat.CSV -> "csv"
            ReportModel.FileFormat.JSON -> "json"
            ReportModel.FileFormat.XLS -> "xls"
            ReportModel.FileFormat.XLSX -> "xlsx"
        }
        val fileName = "${executionLog.report.name}_${timestamp}_${executionLog.jobId}.$fileExtension"
        val zipFileName = "${executionLog.report.name}_${timestamp}_${executionLog.jobId}.zip"
        val zipFile = File(config.getLocalStoragePathAsPath().toFile(), zipFileName)

        return ZipOutputStream(FileOutputStream(zipFile)).use { zipOutputStream ->
            zipOutputStream.putNextEntry(ZipEntry(fileName))
            val sqlQuery = AppContext.db.sqlQuery(
                """
                        SET statement_timeout TO '${maxExecutionTimeMs / (60 * 1000)}min';
                        ${executionLog.report.sql}
                    """.trimIndent()
            )
            when (executionLog.report.fileFormat) {
                ReportModel.FileFormat.CSV -> generateCsvFileStreamingly(zipOutputStream, sqlQuery)
                ReportModel.FileFormat.JSON -> generateJsonFileStreamingly(zipOutputStream, sqlQuery)
                ReportModel.FileFormat.XLS -> generateExcelFileStreamingly(
                    zipOutputStream,
                    sqlQuery,
                    false,
                    executionLog.report.name
                )

                ReportModel.FileFormat.XLSX -> generateExcelFileStreamingly(
                    zipOutputStream,
                    sqlQuery,
                    true,
                    executionLog.report.name
                )
            }

        } to zipFile


    }

    private fun generateCsvFileStreamingly(outputStream: OutputStream, sqlQuery: SqlQuery): Int {
        var rowCount = 0
        var headers: List<String>? = null

        OutputStreamWriter(outputStream).use { writer ->
            CSVWriter(writer).use { csvWriter ->

                sqlQuery.findEach { sqlRow ->
                    if (headers == null) {
                        // Write header on first row
                        headers = sqlRow.keys().asSequence().toList()
                        csvWriter.writeNext(headers.toTypedArray())
                    }

                    // Write data row
                    val values = headers.map { header ->
                        sqlRow.get(header)?.toString() ?: ""
                    }.toTypedArray()
                    csvWriter.writeNext(values)
                    rowCount++
                }
            }
        }
        return rowCount - 1 //exclude header
    }

    private fun generateJsonFileStreamingly(outputStream: OutputStream, sqlQuery: SqlQuery): Int {
        var rowCount = 0

        val jsonGenerator = AppContext.objectMapper.factory.createGenerator(outputStream)

        jsonGenerator.writeStartObject()
        jsonGenerator.writeStringField("generatedAt", Instant.now().toString())
        jsonGenerator.writeArrayFieldStart("data")

        sqlQuery.findEach { sqlRow ->
            jsonGenerator.writeStartObject()
            sqlRow.keys().forEach { columnName ->
                when (val value = sqlRow.get(columnName)) {
                    is String -> jsonGenerator.writeStringField(columnName, value)
                    is Number -> jsonGenerator.writeNumberField(columnName, value.toDouble())
                    is Boolean -> jsonGenerator.writeBooleanField(columnName, value)
                    //todo: handle date/time
                    null -> jsonGenerator.writeNullField(columnName)
                    else -> jsonGenerator.writeStringField(columnName, value.toString())
                }
            }
            jsonGenerator.writeEndObject()
            rowCount++
        }

        jsonGenerator.writeEndArray()
        jsonGenerator.writeNumberField("count", rowCount)
        jsonGenerator.writeEndObject()
        jsonGenerator.close()

        return rowCount
    }

    private fun generateExcelFileStreamingly(
        outputStream: OutputStream,
        sqlQuery: SqlQuery,
        useXlsx: Boolean,
        reportName: String
    ): Int {
        var rowIndex = 0
        var headers: List<String>? = null
        (if (useXlsx) XSSFWorkbook() else HSSFWorkbook())
            .use { workbook ->
                val sheet = workbook.createSheet(reportName)
                val style = workbook.createCellStyle().apply {
                    dataFormat = workbook.createDataFormat().getFormat("######.###")
                }
                val styleInt = workbook.createCellStyle().apply {
                    dataFormat = workbook.createDataFormat().getFormat("######")
                }

                sqlQuery.findEach { sqlRow ->
                    if (headers == null) {
                        // Create header row
                        headers = sqlRow.keys().asSequence().toList()
                        val headerRow = sheet.createRow(rowIndex++)
                        headers.forEachIndexed { index, header ->
                            headerRow.createCell(index).setCellValue(header)
                        }
                    }

                    // Create data row
                    val dataRow = sheet.createRow(rowIndex++)
                    headers.forEachIndexed { index, header ->
                        val cell = dataRow.createCell(index)
                        when (val value = sqlRow.get(header)) {
                            is String -> cell.setCellValue(value)
                            is Number -> cell.apply {
                                setCellValue(value.toDouble())
                                if (value is Int) {
                                    cellStyle = styleInt
                                } else {
                                    cellStyle = style
                                }
                            }
                            //todo: handle date? and time?
                            is Boolean -> cell.setCellValue(value)
                            null -> cell.setCellValue("")
                            else -> cell.setCellValue(value.toString())
                        }
                    }
                }

                // Auto-size columns
                headers?.forEachIndexed { index, _ ->
                    sheet.autoSizeColumn(index)
                }


                workbook.write(outputStream)


            }

        return rowIndex - 1 //exclude header
    }

    private fun uploadToMinio(executionLog: ReportExecutionLog, file: File): Pair<String?, String?> {
        if (minioClient == null || config.minioConfig == null) return null to null

        return try {
            val objectKey =
                "${config.minioConfig.prefix}${executionLog.tenant.domain}/${executionLog.jobId}/${file.name}"

            val putObjectArgs = PutObjectArgs.builder()
                .bucket(config.minioConfig.bucketName)
                .`object`(objectKey)
                .contentType(getContentType(executionLog.fileFormat)).build()

            minioClient.putObject(putObjectArgs)

            logger.info("File uploaded to MinIO: ${config.minioConfig.endpoint}/${config.minioConfig.bucketName}/$objectKey")

            config.minioConfig.bucketName to objectKey
        } catch (e: Exception) {
            logger.error("Failed to upload file to MinIO for job: ${executionLog.jobId}", e)
            null to null
        }

    }

    private fun getContentType(fileFormat: ReportModel.FileFormat): String {
        return when (fileFormat) {
            ReportModel.FileFormat.CSV -> "text/csv"
            ReportModel.FileFormat.JSON -> "application/json"
            ReportModel.FileFormat.XLS -> "application/vnd.ms-excel"
            ReportModel.FileFormat.XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        }
    }

    private fun executeCompletionActions(executionLog: ReportExecutionLog) {
        executionLog.report.completionActions.forEach { action ->
            try {
                when (action) {
                    is ReportModel.CompletionAction.S3Upload -> {
                        // Custom MinIO/S3 upload with user's credentials
                        uploadToCustomMinio(action, executionLog)
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

    private fun uploadToCustomMinio(action: ReportModel.CompletionAction.S3Upload, executionLog: ReportExecutionLog) {
        try {
            // For S3Upload action, we'll assume it's MinIO-compatible
            // The endpoint should be provided in the region field or use a default MinIO endpoint
            val endpoint = if (action.region?.startsWith("http") == true) {
                action.region
            } else {
                "http://localhost:9000" // Default MinIO endpoint
            }

            val customMinioClient =
                MinioClient.builder().endpoint(endpoint).credentials(action.accessKey, action.secretKey).build()

            val localFile = File(executionLog.localFilePath!!)
            val objectKey = action.filePath ?: "${executionLog.jobId}/${localFile.name}"

            // Ensure bucket exists
            if (!customMinioClient.bucketExists(BucketExistsArgs.builder().bucket(action.bucketName).build())) {
                customMinioClient.makeBucket(MakeBucketArgs.builder().bucket(action.bucketName).build())
            }

            val putObjectArgs = PutObjectArgs.builder().bucket(action.bucketName).`object`(objectKey)
                .contentType(getContentType(executionLog.fileFormat)).build()

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
            val allCompletedJobs = AppContext.db.find(ReportExecutionLog::class.java).where()
                .`in`("status", ReportExecutionLog.JobStatus.COMPLETED) //only completed jobs
                .ne("storageLocation", ReportExecutionLog.StorageLocation.NONE) //clean till this reaches none
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
                            if (job.storageLocation == ReportExecutionLog.StorageLocation.S3 || job.storageLocation == ReportExecutionLog.StorageLocation.BOTH) {
                                val deleted = deleteFromMinio(job)
                                if (deleted) {
                                    //only if delete succeeded then we shall make it null, else it'll be dnagling records
                                    job.s3BucketName = null
                                    job.s3ObjectKey = null

                                    executionRepository.update(job)
                                    totalCleaned++
                                }
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

    private fun deleteFromMinio(executionLog: ReportExecutionLog): Boolean {
        if (minioClient == null) {
            //if we have an uploaded file, but no minio client, then deleted is false
            return !(executionLog.s3ObjectKey != null && executionLog.s3BucketName != null)
        }

        return try {
            minioClient.removeObject(
                RemoveObjectArgs.builder().bucket(executionLog.s3BucketName).`object`(executionLog.s3ObjectKey).build()
            )

            logger.debug("Deleted old MinIO file: ${executionLog.s3BucketName}/${executionLog.s3ObjectKey}")
            true
        } catch (e: Exception) {
            logger.warn("Failed to delete old MinIO file for job: ${executionLog.jobId}", e)
            false
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
                executionLog.localFilePath?.let { File(it) }?.takeIf { it.exists() } ?: downloadFromMinio(executionLog)
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

            val getObjectArgs =
                GetObjectArgs.builder().bucket(executionLog.s3BucketName).`object`(executionLog.s3ObjectKey).build()

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
}

object JobRunnerServiceHolder {
    val instance: JobRunnerService by lazy { JobRunnerService() }
}