package my.baas.dto

import com.fasterxml.jackson.annotation.JsonIgnore

data class FilterDto(
    val jsonPath: String,
    val operator: FilterOperator,
    val value: Any
) {
    @JsonIgnore
    fun getListValue(): List<*> {
        return value as? List<*> ?: listOf(value)
    }
}

enum class FilterOperator {
    // Simple operators
    EQ,         // equals
    NE,         // not equals
    LT,         // less than
    LE,         // less than or equal
    GT,         // greater than
    GE,         // greater than or equal
    IN,         // in list
    NOT_IN,     // not in list
    
    // PostgreSQL JSONB operators
    CONTAINS,       // @>
    CONTAINED_BY,   // <@
    HAS_KEY,        // ?
    HAS_ANY_KEYS,   // ?|
    HAS_ALL_KEYS,   // ?&
    PATH_EXISTS,    // @?
    PATH_MATCH;     // @@
    
    fun getOperatorSymbol(): String {
        return when (this) {
            EQ -> "="
            NE -> "!="
            LT -> "<"
            LE -> "<="
            GT -> ">"
            GE -> ">="
            IN -> "IN"
            NOT_IN -> "NOT IN"
            CONTAINS -> "@>"
            CONTAINED_BY -> "<@"
            HAS_KEY -> "?"
            HAS_ANY_KEYS -> "?|"
            HAS_ALL_KEYS -> "?&"
            PATH_EXISTS -> "@?"
            PATH_MATCH -> "@@"
        }
    }
    
    fun requiresNumericCasting(): Boolean {
        return this in setOf(LT, LE, GT, GE)
    }
    
    fun isListOperator(): Boolean {
        return this in setOf(IN, NOT_IN, HAS_ANY_KEYS, HAS_ALL_KEYS)
    }
    
    fun requiresJsonSerialization(): Boolean {
        return this in setOf(CONTAINS, CONTAINED_BY)
    }
    
    fun usesDataRoot(): Boolean {
        return this in setOf(PATH_EXISTS, PATH_MATCH)
    }
}

data class SearchRequestDto(
    val filters: List<FilterDto>,
    val pageNo: Int = 1,
    val pageSize: Int = 100
)