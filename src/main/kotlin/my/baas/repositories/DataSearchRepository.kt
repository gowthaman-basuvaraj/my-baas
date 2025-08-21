package my.baas.repositories

import io.ebean.DB
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
    override fun searchWithMultipleFilters(entityName: String, searchRequest: SearchRequest): List<String> {
        if (searchRequest.filters.isEmpty()) {
            return emptyList()
        }

        val query = DB.find(DataSearchModel::class.java)
            .where()
            .eq("entityName", entityName)


            .apply {
                or()
                searchRequest.filters.forEach { filter ->

                    val valueType = filter.jsonType()
                    val typeCast = valueType.dbTypeCast()
                    val dbKey = "{value->'value'}$typeCast"
                    val type = filter.searchType

                    and()
                    eq("jsonPath", filter.jsonPath)
                    eq("valueType", valueType)

                    if (type == SearchType.EQ) {
                        raw("$dbKey = ?", filter.value)
                    } else if (type == SearchType.HAS && valueType == JsonValueType.STRING) {
                        raw("$dbKey ilike ?", "%${filter.value}%")
                    } else if (valueType == JsonValueType.NUMBER) {
                        when (type) {
                            SearchType.LT -> {
                                raw("$dbKey < ?", "0")
                            }

                            SearchType.LE -> {
                                raw("$dbKey <= ?", "0")
                            }

                            SearchType.GT -> {
                                raw("$dbKey > ?", "0")
                            }

                            SearchType.GE -> {
                                raw("$dbKey >= ?", "0")
                            }

                            else -> {
                                logger.warn("Unknown search type $type for Numeric")
                            }
                        }
                    } else {
                        logger.warn("Unknown search type: $type")
                    }

                    endAnd()

                }
                endOr()
            }



        return query.select("uniqueIdentifier").findSingleAttributeList()
    }

}