package my.baas.repositories

import io.ebean.DB
import my.baas.config.AppContext
import my.baas.controllers.SearchRequest
import my.baas.models.DataSearchModel
import my.baas.models.JsonValueType
import my.baas.services.SearchType
import org.slf4j.LoggerFactory

interface DataSearchRepository {
    fun save(dataSearchModel: DataSearchModel): DataSearchModel
    fun saveAll(dataSearchEntities: List<DataSearchModel>): List<DataSearchModel>
    fun deleteByEntityNameAndUniqueIdentifier(entityName: String, uniqueIdentifier: String): Boolean
    fun findByEntityNameAndUniqueIdentifier(entityName: String, uniqueIdentifier: String): List<DataSearchModel>
    fun searchWithMultipleFilters(entityName: String, searchRequest: SearchRequest): List<String>
}

class DataSearchRepositoryImpl : DataSearchRepository {

    override fun save(dataSearchModel: DataSearchModel): DataSearchModel {
        dataSearchModel.save()
        return dataSearchModel
    }

    override fun saveAll(dataSearchEntities: List<DataSearchModel>): List<DataSearchModel> {
        dataSearchEntities.forEach { it.save() }
        return dataSearchEntities
    }

    override fun deleteByEntityNameAndUniqueIdentifier(entityName: String, uniqueIdentifier: String): Boolean {
        val entities = findByEntityNameAndUniqueIdentifier(entityName, uniqueIdentifier)
        entities.forEach { it.delete() }
        return entities.isNotEmpty()
    }

    override fun findByEntityNameAndUniqueIdentifier(
        entityName: String,
        uniqueIdentifier: String
    ): List<DataSearchModel> {
        return DB.find(DataSearchModel::class.java)
            .where()
            .eq("entityName", entityName)
            .eq("uniqueIdentifier", uniqueIdentifier)
            .findList()
    }

    private val logger = LoggerFactory.getLogger("DataSearch")

    data class Q(val jsonPath: String, val valueType: JsonValueType, val entityName: String, val value: Any)

    override fun searchWithMultipleFilters(entityName: String, searchRequest: SearchRequest): List<String> {
        if (searchRequest.filters.isEmpty()) {
            return emptyList()
        }

        val queryAndParam = searchRequest.filters.mapNotNull { filter ->

            val qStart =
                "select unique_identifier from data_search_model where json_path = ? and entity_name = ? and value_type = ?"
            val valueType = filter.jsonType()
            val typeCast = valueType.dbTypeCast()
            val dbKey = "{value->'value'}$typeCast"
            val type = filter.searchType


            val qp = if (type == SearchType.EQ) {
                Pair("$dbKey = ?", Q(filter.jsonPath, valueType, entityName, filter.value))
            } else if (type == SearchType.HAS && valueType == JsonValueType.STRING) {
                Pair("$dbKey ilike ?", Q(filter.jsonPath, valueType, entityName, "%${filter.value}%"))
            } else if (valueType == JsonValueType.NUMBER) {
                when (type) {
                    SearchType.LT -> {
                        Pair("$dbKey < ?", Q(filter.jsonPath, valueType, entityName, filter.value))
                    }

                    SearchType.LE -> {
                        Pair("$dbKey <= ?", Q(filter.jsonPath, valueType, entityName, filter.value))
                    }

                    SearchType.GT -> {
                        Pair("$dbKey > ?", Q(filter.jsonPath, valueType, entityName, filter.value))
                    }

                    SearchType.GE -> {
                        Pair("$dbKey >= ?", Q(filter.jsonPath, valueType, entityName, filter.value))
                    }

                    else -> {
                        logger.warn("unknown value type $type for $valueType")
                        null
                    }
                }
            } else {
                logger.warn("Unknown search type: $type")
                null
            }

            if (qp == null) {
                null
            } else {
                "$qStart and ${qp.first}" to qp.second
            }
        }

        if (queryAndParam.isEmpty()) {
            return emptyList()
        }

        val finalQuery = queryAndParam.joinToString(separator = " INTERSECT ")

        val q = AppContext.db.sqlQuery(finalQuery)
        queryAndParam.forEachIndexed { index0, query ->
            val index = index0 * 4
            q.setParameter(index + 1, query.second.jsonPath)
            q.setParameter(index + 2, query.second.entityName)
            q.setParameter(index + 3, query.second.valueType)
            q.setParameter(index + 4, query.second.value)
        }
        return q.findList()
            .map {
                it.getString("unique_identifier")
            }

    }

}