package my.baas.repositories

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ebean.PagedList
import io.ebean.RawSql
import io.ebean.RawSqlBuilder
import io.javalin.http.BadRequestResponse
import my.baas.auth.CurrentUser
import my.baas.config.AppContext
import my.baas.dto.FilterDto
import my.baas.dto.FilterOperator
import my.baas.models.DataModel
import my.baas.models.SchemaModel
import my.baas.services.TableManagementService.parseJsonPathToChain
import org.slf4j.LoggerFactory

object DataRepository {
    private val objectMapper = jacksonObjectMapper()
    private val logger = LoggerFactory.getLogger("DataRepo")

    private fun createDataModelRawSql(sql: String): RawSql {
        return RawSqlBuilder.parse(sql)
            .columnMapping("id", "id")
            .columnMapping("unique_identifier", "uniqueIdentifier")
            .columnMapping("entity_name", "entityName")
            .columnMapping("version_name", "versionName")
            .columnMapping("data", "data")
            .columnMapping("tenant_id", "tenantId")
            .columnMapping("schema_id", "schemaId")
            .columnMapping("when_created", "whenCreated")
            .columnMapping("when_modified", "whenModified")
            .columnMapping("who_created", "whoCreated")
            .columnMapping("who_modified", "whoModified")
            .create()
    }

    fun save(dataModel: DataModel): DataModel {
        AppContext.db.insert(dataModel)
        return dataModel
    }


    fun findAllByEntityName(
        entityName: String,
        versionName: String?,
        pageNo: Int,
        pageSize: Int
    ): PagedList<DataModel> {
        return AppContext.db.find(DataModel::class.java)
            .where()
            .apply {
                if (versionName != null) {
                    eq("versionName", versionName)
                }
            }
            .eq("applicationId", CurrentUser.get().applicationId ?: throw BadRequestResponse())

            .eq("entityName", entityName)
            .setMaxRows(pageSize)
            .setFirstRow((pageNo - 1) * pageSize)
            .findPagedList()
    }

    fun update(dataModel: DataModel): DataModel {

        AppContext.db.update(dataModel)

        return dataModel
    }


    fun findByUniqueIdentifier(entityName: String, uniqueIdentifier: String): DataModel? {


        return AppContext.db.find(DataModel::class.java)
            .where()
            .eq("uniqueIdentifier", uniqueIdentifier)
            .eq("entityName", entityName)
            .findOne()
    }


    fun deleteByUniqueIdentifier(
        entityName: String,
        uniqueIdentifier: String,
        reallyDelete: Boolean = false
    ): Boolean {
        return (AppContext.db.find(DataModel::class.java)
            .where()
            .eq("uniqueIdentifier", uniqueIdentifier)
            .eq("applicationId", CurrentUser.get().applicationId ?: throw BadRequestResponse())
            .eq("entityName", entityName)
            .findOne()?.let {
                if (reallyDelete) it.deletePermanent()
                else it.delete()
            }) ?: false
    }

    fun search(
        entityName: String,
        filters: List<FilterDto>,
        pageNo: Int,
        pageSize: Int
    ): PagedList<DataModel> {
        val tenantId = CurrentUser.getTenant()?.id
            ?: throw IllegalStateException("No tenant in context")
        val applicationId = CurrentUser.getApplicationId() ?: throw IllegalStateException("No application in context")
        val tableName = SchemaModel.generateTableName(tenantId, applicationId, entityName)

        if (filters.isEmpty()) {
            return findAllByEntityName(entityName, null, pageNo, pageSize)
        }

        // Build filter conditions functionally
        val filterConditions = filters.mapIndexed { index, filter ->
            buildFilterCondition(filter, index)
        }

        // Base conditions
        val baseConditions = listOf(
            "entity_name = :entityName" to mapOf("entityName" to entityName),
            "tenant_id = :tenantId" to mapOf("tenantId" to tenantId),
            "application_id = :applicationId" to mapOf("applicationId" to applicationId)
        )

        // Combine all conditions
        val allConditions = baseConditions + filterConditions
        val whereConditions = allConditions.map { it.first }
        val allParameters = allConditions.flatMap { it.second.toList() }.toMap()

        val sql = """
            SELECT id, data, unique_identifier, entity_name, version_name, tenant_id, when_created, when_modified, who_created, who_modified, schema_id FROM $tableName 
            WHERE ${whereConditions.joinToString(" AND ")}
            ORDER BY when_created DESC
        """.trimIndent()

        logger.warn("Constructed Query [$sql] from $filters")

        val rawSql = createDataModelRawSql(sql)

        val query = AppContext.db.find(DataModel::class.java)
            .setRawSql(rawSql)

        // Set all parameters functionally
        allParameters.forEach { (key, value) ->
            query.setParameter(key, value)
        }

        return query
            .setFirstRow((pageNo - 1) * pageSize)
            .setMaxRows(pageSize)
            .findPagedList()
    }

    private fun buildFilterCondition(filter: FilterDto, index: Int): Pair<String, Map<String, Any>> {
        val paramKey = "filter$index"
        val operator = filter.operator.getOperatorSymbol()

        return when {
            filter.operator.isListOperator() -> {
                buildListOperatorCondition(filter, paramKey, operator)
            }

            filter.operator.requiresJsonSerialization() -> {
                buildJsonOperatorCondition(filter, paramKey, operator)
            }

            filter.operator.usesDataRoot() -> {
                buildDataRootOperatorCondition(filter, paramKey, operator)
            }

            filter.operator == FilterOperator.HAS_KEY -> {
                val jsonPathChain = parseJsonPathToChain(filter.jsonPath)
                "$jsonPathChain $operator :$paramKey" to mapOf(paramKey to filter.value.toString())
            }

            else -> {
                buildSimpleOperatorCondition(filter, paramKey, operator)
            }
        }
    }

    private fun buildSimpleOperatorCondition(
        filter: FilterDto,
        paramKey: String,
        operator: String
    ): Pair<String, Map<String, Any>> {
        val jsonPathChain = parseJsonPathToChain(filter.jsonPath)
        val castType = if (filter.operator.requiresNumericCasting()) "numeric" else "text"
        return "($jsonPathChain)::$castType $operator :$paramKey" to mapOf(paramKey to filter.value)
    }

    private fun buildListOperatorCondition(
        filter: FilterDto,
        paramKey: String,
        operator: String
    ): Pair<String, Map<String, Any>> {
        val jsonPathChain = parseJsonPathToChain(filter.jsonPath)
        val values = filter.getListValue()

        return when (filter.operator) {
            FilterOperator.IN, FilterOperator.NOT_IN -> {
                val placeholders = values.mapIndexed { i, _ -> ":${paramKey}_$i" }.joinToString(",")
                val parameters = values.mapIndexed { i, value -> "${paramKey}_$i" to (value ?: "") }.toMap()
                "($jsonPathChain)::text $operator ($placeholders)" to parameters
            }

            FilterOperator.HAS_ANY_KEYS, FilterOperator.HAS_ALL_KEYS -> {
                val keys = values.map { it.toString() }.toTypedArray()
                "$jsonPathChain $operator :$paramKey" to mapOf(paramKey to keys)
            }

            else -> throw IllegalArgumentException("Unsupported list operator: ${filter.operator}")
        }
    }

    private fun buildJsonOperatorCondition(
        filter: FilterDto,
        paramKey: String,
        operator: String
    ): Pair<String, Map<String, Any>> {
        val jsonPathChain = parseJsonPathToChain(filter.jsonPath)
        val serializedValue = objectMapper.writeValueAsString(filter.value)
        return "$jsonPathChain $operator cast(:$paramKey as jsonb)" to mapOf(paramKey to serializedValue)
    }

    private fun buildDataRootOperatorCondition(
        filter: FilterDto,
        paramKey: String,
        operator: String
    ): Pair<String, Map<String, Any>> {
        return "data $operator :$paramKey" to mapOf(paramKey to filter.value.toString())
    }

    fun findSchemaByEntityAndVersion(entityName: String, versionName: String): SchemaModel? {
        val tenantId = CurrentUser.getTenant()?.id
            ?: throw IllegalStateException("No tenant in context")
        val applicationId = CurrentUser.getApplicationId()
            ?: throw IllegalStateException("No application in context")

        return AppContext.db.find(SchemaModel::class.java)
            .where()
            .eq("entityName", entityName)
            .eq("versionName", versionName)
            .eq("tenantId", tenantId)
            .eq("application_id", applicationId)
            .findOne()
    }

    fun validateSchemaExistsForEntityAndVersion(entityName: String, versionName: String? = null) {
        val tenantId = CurrentUser.getTenant()?.id
            ?: throw IllegalStateException("No tenant in context")
        val applicationId = CurrentUser.getApplicationId()
            ?: throw IllegalStateException("No application in context")

        val query = AppContext.db.find(SchemaModel::class.java)
            .where()
            .eq("entityName", entityName)
            .eq("tenantId", tenantId)
            .eq("application_id", applicationId)

        if (versionName != null) {
            query.eq("versionName", versionName)
        }

        val schemaExists = query.exists()

        if (!schemaExists) {
            val versionInfo = if (versionName != null) ", version: '$versionName'" else ""
            throw io.javalin.http.NotFoundResponse("Schema not found for entity: '$entityName'$versionInfo")
        }
    }
}