package my.baas.models.json

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import my.baas.models.ReportModel

class CompletionActionDeserializer : JsonDeserializer<ReportModel.CompletionAction>() {

    override fun deserialize(parser: JsonParser, context: DeserializationContext): ReportModel.CompletionAction {
        val mapper = parser.codec as ObjectMapper
        val node: JsonNode = mapper.readTree(parser)

        val actionType = node.get("actionType")?.asText()
            ?: throw IllegalArgumentException("Missing actionType field")

        return when (ReportModel.ActionType.valueOf(actionType)) {
            ReportModel.ActionType.S3 -> {
                ReportModel.CompletionAction.S3Upload(
                    actionType = ReportModel.ActionType.S3,
                    bucketName = node.get("bucketName")?.asText() ?: "",
                    accessKey = node.get("accessKey")?.asText() ?: "",
                    secretKey = node.get("secretKey")?.asText() ?: "",
                    region = node.get("region")?.asText(),
                    filePath = node.get("filePath")?.asText()
                )
            }
            ReportModel.ActionType.SFTP -> {
                ReportModel.CompletionAction.SftpUpload(
                    actionType = ReportModel.ActionType.SFTP,
                    host = node.get("host")?.asText() ?: "",
                    port = node.get("port")?.asInt() ?: 22,
                    username = node.get("username")?.asText() ?: "",
                    password = node.get("password")?.asText(),
                    sshKey = node.get("sshKey")?.asText(),
                    remotePath = node.get("remotePath")?.asText() ?: ""
                )
            }
            ReportModel.ActionType.EMAIL -> {
                val toList = node.get("to")?.let { toNode ->
                    if (toNode.isArray) {
                        toNode.map { it.asText() }
                    } else {
                        listOf(toNode.asText())
                    }
                } ?: emptyList()

                val ccList = node.get("cc")?.let { ccNode ->
                    if (ccNode.isArray) {
                        ccNode.map { it.asText() }
                    } else {
                        listOf(ccNode.asText())
                    }
                } ?: emptyList()

                val bccList = node.get("bcc")?.let { bccNode ->
                    if (bccNode.isArray) {
                        bccNode.map { it.asText() }
                    } else {
                        listOf(bccNode.asText())
                    }
                } ?: emptyList()

                ReportModel.CompletionAction.Email(
                    actionType = ReportModel.ActionType.EMAIL,
                    to = toList,
                    cc = ccList,
                    bcc = bccList,
                    subject = node.get("subject")?.asText() ?: "",
                    body = node.get("body")?.asText(),
                    attachFile = node.get("attachFile")?.asBoolean() ?: true
                )
            }
        }
    }
}