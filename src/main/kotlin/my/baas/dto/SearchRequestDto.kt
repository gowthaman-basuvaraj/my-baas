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
/*
oprname |                 function
---------+------------------------------------------
 ->      | json_object_field(json, text)
 ->>     | json_object_field_text(json, text)
 ->      | json_array_element(json, integer)
 ->>     | json_array_element_text(json, integer)
 #>      | json_extract_path(json, text[])
 #>>     | json_extract_path_text(json, text[])
 ->      | jsonb_object_field(jsonb, text)
 ->>     | jsonb_object_field_text(jsonb, text)
 ->      | jsonb_array_element(jsonb, integer)
 ->>     | jsonb_array_element_text(jsonb, integer)
 #>      | jsonb_extract_path(jsonb, text[])
 #>>     | jsonb_extract_path_text(jsonb, text[])
 =       | jsonb_eq(jsonb, jsonb)
 <>      | jsonb_ne(jsonb, jsonb)
 <       | jsonb_lt(jsonb, jsonb)
 >       | jsonb_gt(jsonb, jsonb)
 <=      | jsonb_le(jsonb, jsonb)
 >=      | jsonb_ge(jsonb, jsonb)
 @>      | jsonb_contains(jsonb, jsonb)
 ?       | jsonb_exists(jsonb, text)
 ?|      | jsonb_exists_any(jsonb, text[])
 ?&      | jsonb_exists_all(jsonb, text[])
 <@      | jsonb_contained(jsonb, jsonb)
 ||      | jsonb_concat(jsonb, jsonb)
 #-      | jsonb_delete_path(jsonb, text[])
 @?      | jsonb_path_exists_opr(jsonb, jsonpath)
 @@      | jsonb_path_match_opr(jsonb, jsonpath)
*/

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

    ARRAY_CONTAINS, // ?, Array Contains
    CONTAINS,       // @>
    CONTAINED_BY,   // <@
    HAS_KEY,        // ?
    HAS_ANY_KEYS,   // ?|
    HAS_ALL_KEYS,   // ?&
    PATH_EXISTS,    // @?
    PATH_MATCH;     // @@

}

data class SearchRequestDto(
    val filters: List<FilterDto>,
    val pageNo: Int = 1,
    val pageSize: Int = 100
)