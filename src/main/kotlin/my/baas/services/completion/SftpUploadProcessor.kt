package my.baas.services.completion

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.SftpException
import my.baas.models.ReportExecutionLog
import my.baas.models.ReportModel
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

class SftpUploadProcessor : CompletionActionProcessor<ReportModel.CompletionAction.SftpUpload> {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun process(action: ReportModel.CompletionAction.SftpUpload, executionLog: ReportExecutionLog) {
        try {
            val localFile = File(executionLog.localFilePath!!)

            val jsch = JSch()

            // Setup SSH key if provided
            if (!action.sshKey.isNullOrBlank()) {
                jsch.addIdentity("temp_key", action.sshKey.toByteArray(), null, null)
            }

            val session = jsch.getSession(action.username, action.host, action.port)

            // Set password if provided
            if (!action.password.isNullOrBlank()) {
                session.setPassword(action.password)
            }

            // Skip host key verification
            val config = Properties()
            config["StrictHostKeyChecking"] = "no"
            session.setConfig(config)

            session.connect()

            val channelSftp = session.openChannel("sftp") as ChannelSftp
            channelSftp.connect()

            // Create remote directories if they don't exist
            val remotePath = action.remotePath.trimEnd('/')
            val remoteFile = "$remotePath/${localFile.name}"

            try {
                // Try to create directory path
                val dirs = remotePath.split("/").filter { it.isNotBlank() }
                var currentPath = ""
                for (dir in dirs) {
                    currentPath = if (currentPath.isEmpty()) "/$dir" else "$currentPath/$dir"
                    try {
                        channelSftp.mkdir(currentPath)
                    } catch (e: SftpException) {
                        // Directory might already exist, continue
                    }
                }
            } catch (e: Exception) {
                logger.warn("Could not create remote directories: ${e.message}")
            }

            // Upload the file
            channelSftp.put(localFile.absolutePath, remoteFile)

            channelSftp.disconnect()
            session.disconnect()

            logger.info("File uploaded to SFTP: ${action.host}:$remoteFile")

        } catch (e: Exception) {
            logger.error("Failed to upload to SFTP for job: ${executionLog.jobId}", e)
            throw e
        }
    }

    override fun getActionType(): ReportModel.ActionType = ReportModel.ActionType.SFTP
}