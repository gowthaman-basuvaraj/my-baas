package my.baas.controllers

import com.fasterxml.jackson.annotation.JsonIgnore
import my.baas.models.JsonValueType
import my.baas.services.SearchType
import java.time.Instant

data class SearchFilter(
    val jsonPath: String,
    val value: Any,
    val searchType: SearchType = SearchType.EQ,
    val arrayIdx: Int? = null,
    val createdFrom: Instant? = null,
    val createdTo: Instant? = null,
    val modifiedFrom: Instant? = null,
    val modifiedTo: Instant? = null,
) {
    @JsonIgnore
    fun jsonType() = JsonValueType.determineJsonValueType(value)
}

data class SearchRequest(
    val filters: List<SearchFilter> = emptyList(),
    val logicalOperator: LogicalOperator = LogicalOperator.AND,
    val limit: Int? = null,
    val offset: Int? = null
)

enum class LogicalOperator {
    AND,
    OR
}