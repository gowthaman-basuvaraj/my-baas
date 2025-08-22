package my.baas.services.completion

import io.minio.BucketExistsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import my.baas.models.ReportExecutionLog
import my.baas.models.ReportModel
import org.slf4j.LoggerFactory
import java.io.File

class S3UploadProcessor : CompletionActionProcessor<ReportModel.CompletionAction.S3Upload> {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun process(action: ReportModel.CompletionAction.S3Upload, executionLog: ReportExecutionLog) {
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
                .contentType(executionLog.fileFormat.getContentType())
                .stream(localFile.inputStream(), localFile.length(), -1)
                .build()

            customMinioClient.putObject(putObjectArgs)

            logger.info("File uploaded to custom MinIO: $endpoint/${action.bucketName}/$objectKey")

        } catch (e: Exception) {
            logger.error("Failed to upload to custom MinIO for job: ${executionLog.jobId}", e)
            throw e
        }
    }

    override fun getActionType(): ReportModel.ActionType = ReportModel.ActionType.S3
}