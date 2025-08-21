package my.baas.services

import com.jayway.jsonpath.JsonPath
import com.fasterxml.jackson.databind.ObjectMapper
import my.baas.config.AppContext.objectMapper

data class ExtractedValue(
    val value: Any?,
    val arrayIndex: Int? = null
)

object JsonPathExtractor {

    fun extractValueWithArrayIndex(data: Map<String, Any>, jsonPath: String): List<ExtractedValue> {
        return try {
            val jsonString = objectMapper.writeValueAsString(data)
            val result = JsonPath.read<Any>(jsonString, jsonPath)
            
            when (result) {
                is List<*> -> {
                    // If the result is a list, create ExtractedValue for each element with its index
                    result.mapIndexed { index, value ->
                        ExtractedValue(value, index)
                    }
                }
                else -> {
                    // If it's a single value, create one ExtractedValue without array index
                    listOf(ExtractedValue(result, null))
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun createValueMap(value: Any?): Map<String, Any> {
        return if (value != null) {
            mapOf(
                "type" to value::class.java.simpleName,
                "value" to value
            )
        } else {
            mapOf(
                "type" to "null",
                "value" to "null"
            )
        }
    }
}