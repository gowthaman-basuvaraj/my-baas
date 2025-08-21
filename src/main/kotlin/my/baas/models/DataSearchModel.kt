package my.baas.models

import io.ebean.annotation.DbJsonB
import io.ebean.annotation.Index
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.time.Instant

enum class JsonValueType {
    STRING,
    NUMBER,
    BOOLEAN,
    OBJECT,
    ARRAY,
    NULL;

    fun dbTypeCast() = when (this) {
        STRING -> "::text"
        NUMBER -> "::numeric"
        BOOLEAN -> "::boolean"
        else -> "::text"
    }

    companion object {

        fun determineJsonValueType(value: Any?): JsonValueType {
            return when (value) {
                null -> NULL
                is String -> STRING
                is Number -> NUMBER
                is Boolean -> BOOLEAN
                is List<*> -> ARRAY
                is Map<*, *> -> OBJECT
                else -> STRING // Fallback for unknown types, convert to string
            }
        }
    }
}

@Entity
@Index(
    name = "search_value_numeric",
    definition = "CREATE INDEX search_value_numeric ON data_search_model USING BTREE (((value->'value')::numeric)) where value_type = 'NUMBER'"
)
@Index(
    name = "search_value_text",
    definition = "CREATE INDEX search_value_text ON data_search_model USING BTREE ((value->>'value')) where value_type = 'STRING'"
)
@Index(
    name = "search_value_boolean",
    definition = "CREATE INDEX search_value_text ON data_search_model USING BTREE (((value->'value')::boolean)) where value_type = 'BOOLEAN'"
)
@Index(
    name = "search_value_gin",
    definition = "CREATE INDEX search_value_gin ON data_search_model USING GIN((value->'value'))"
)
@Index(
    name = "search_gin",
    definition = "CREATE INDEX search_gin ON data_search_model USING GIN(value)"
)
class DataSearchModel(

    @Index
    var entityName: String,

    var uniqueIdentifier: String,

    var jsonPath: String,

    @DbJsonB
    var value: Map<String, Any>,

    @Enumerated(EnumType.STRING)
    var valueType: JsonValueType,

    var arrayIdx: Int? = null,

    var dataCreatedAt: Instant? = null,

    var dataModifiedAt: Instant? = null

) : BaseTenantModel()