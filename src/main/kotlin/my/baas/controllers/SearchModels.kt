package my.baas.controllers

import my.baas.services.SearchType

data class SearchFilter(
    val jsonPath: String,
    val value: Any,
    val searchType: SearchType = SearchType.EQ,
    val arrayIdx: Int? = null
)

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