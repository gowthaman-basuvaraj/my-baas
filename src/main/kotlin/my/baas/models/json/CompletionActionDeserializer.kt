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
                mapper.treeToValue(node, ReportModel.CompletionAction.S3Upload::class.java)
            }

            ReportModel.ActionType.SFTP -> {
                mapper.treeToValue(node, ReportModel.CompletionAction.SftpUpload::class.java)
            }

            ReportModel.ActionType.EMAIL -> {
                mapper.treeToValue(node, ReportModel.CompletionAction.Email::class.java)
            }
        }
    }
}