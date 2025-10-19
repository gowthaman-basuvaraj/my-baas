package my.baas.services.completion

import jakarta.activation.DataHandler
import jakarta.activation.FileDataSource
import jakarta.mail.*
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import my.baas.config.EmailConfig
import my.baas.models.ReportExecutionLog
import my.baas.models.ReportModel
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

class EmailProcessor(
    private val emailConfig: EmailConfig?
) : CompletionActionProcessor<ReportModel.CompletionAction.Email> {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun process(action: ReportModel.CompletionAction.Email, executionLog: ReportExecutionLog) {
        if (emailConfig is EmailConfig.Present) {


            try {
                val properties = Properties().apply {
                    put("mail.smtp.host", emailConfig.smtpHost)
                    put("mail.smtp.port", emailConfig.smtpPort.toString())
                    put("mail.smtp.auth", emailConfig.auth.toString())
                    put("mail.smtp.starttls.enable", emailConfig.startTlsEnable.toString())
                }

                val session = if (emailConfig.auth && emailConfig.username != null && emailConfig.password != null) {
                    Session.getInstance(properties, object : Authenticator() {
                        override fun getPasswordAuthentication(): PasswordAuthentication {
                            return PasswordAuthentication(emailConfig.username, emailConfig.password)
                        }
                    })
                } else {
                    Session.getInstance(properties)
                }

                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(emailConfig.fromAddress, emailConfig.fromName))

                    // Set recipients
                    setRecipients(Message.RecipientType.TO, action.to.joinToString(","))
                    if (action.cc.isNotEmpty()) {
                        setRecipients(Message.RecipientType.CC, action.cc.joinToString(","))
                    }
                    if (action.bcc.isNotEmpty()) {
                        setRecipients(Message.RecipientType.BCC, action.bcc.joinToString(","))
                    }

                    subject = action.subject

                    if (action.attachFile && executionLog.localFilePath != null) {
                        // Create multipart message with attachment
                        val multipart = MimeMultipart()

                        // Add text part
                        val textPart = MimeBodyPart().apply {
                            setText(
                                action.body
                                    ?: "Report execution completed successfully.\n\nReport: ${executionLog.report.name}\nRows: ${executionLog.rowCount}\nExecution Time: ${executionLog.executionTimeMs}ms"
                            )
                        }
                        multipart.addBodyPart(textPart)

                        // Add attachment
                        val attachmentPart = MimeBodyPart().apply {
                            val localFile = File(executionLog.localFilePath!!)
                            dataHandler = DataHandler(FileDataSource(localFile))
                            fileName = localFile.name
                        }
                        multipart.addBodyPart(attachmentPart)

                        setContent(multipart)
                    } else {
                        // Simple text message
                        setText(
                            action.body
                                ?: "Report execution completed successfully.\n\nReport: ${executionLog.report.name}\nRows: ${executionLog.rowCount}\nExecution Time: ${executionLog.executionTimeMs}ms"
                        )
                    }
                }

                Transport.send(message)

                logger.info("Email sent successfully for job: ${executionLog.jobId} to ${action.to.joinToString()}")

            } catch (e: Exception) {
                logger.error("Failed to send email for job: ${executionLog.jobId}", e)
                throw e
            }
        } else {
            logger.warn("Email configuration not available, skipping email for job: ${executionLog.jobId}")
            return
        }
    }

    override fun getActionType(): ReportModel.ActionType = ReportModel.ActionType.EMAIL
}