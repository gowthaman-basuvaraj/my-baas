package my.baas.repositories

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ebean.PagedList
import io.ebean.RawSql
import io.ebean.RawSqlBuilder
import my.baas.auth.CurrentUser
import my.baas.config.AppContext
import my.baas.dto.FilterDto
import my.baas.dto.FilterOperator
import my.baas.models.DataModel
import my.baas.models.SchemaModel
import my.baas.services.TableManagementService.parseJsonPathToChain
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant

interface DataRepository {
    fun save(dataModel: DataModel): DataModel
    fun findById(id: Long): DataModel?
    fun findAll(): List<DataModel>
    fun findAllByEntityName(entityName: String, versionName: String?, pageNo: Int, pageSize: Int): PagedList<DataModel>
    fun update(dataModel: DataModel): DataModel
    fun deleteById(id: Long): Boolean
    fun findByUniqueIdentifier(entityName: String, uniqueIdentifier: String): DataModel?
    fun findByUniqueIdentifiers(entityName: String, uniqueIdentifiers: List<String>): List<DataModel>
    fun deleteByUniqueIdentifier(entityName: String, uniqueIdentifier: String): Boolean
    fun search(entityName: String, filters: List<FilterDto>, pageNo: Int, pageSize: Int): PagedList<DataModel>
    fun findSchemaByEntityAndVersion(entityName: String, versionName: String): SchemaModel?
    fun validateSchemaExistsForEntityAndVersion(entityName: String, versionName: String? = null)
}

class DataRepositoryImpl : DataRepository {
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
            .columnMapping("when_created", "whenCreated")
            .columnMapping("when_modified", "whenModified")
            .columnMapping("version", "version")
            .create()
    }

    override fun save(dataModel: DataModel): DataModel {
        val tenantId = dataModel.tenantId
        val tableName = SchemaModel.generateTableName(tenantId, dataModel.entityName)

        val dataJson = objectMapper.writeValueAsString(dataModel.data)
        val now = Timestamp.from(Instant.now())

        // Use JDBC connection for raw SQL with RETURNING clause
        AppContext.db.dataSource().connection.use { conn ->
            val sql = """
                INSERT INTO $tableName (unique_identifier, entity_name, version_name, data, tenant_id, when_created, when_modified, version)
                VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, 1)
                RETURNING id, when_created, when_modified, version
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, dataModel.uniqueIdentifier)
                stmt.setString(2, dataModel.entityName)
                stmt.setString(3, dataModel.versionName)
                stmt.setString(4, dataJson)
                stmt.setLong(5, tenantId)
                stmt.setTimestamp(6, now)
                stmt.setTimestamp(7, now)

                val rs = stmt.executeQuery()
                if (rs.next()) {
                    dataModel.id = rs.getLong("id")
                    dataModel.whenCreated = rs.getTimestamp("when_created").toInstant()
                    dataModel.whenModified = rs.getTimestamp("when_modified").toInstant()
                    dataModel.version = rs.getInt("version")
                    dataModel.tenantId = tenantId
                    return dataModel
                } else {
                    throw RuntimeException("Failed to save DataModel")
                }
            }
        }
    }

    override fun findById(id: Long): DataModel? {
        // This method would need tenant context to determine the correct table
        // For now, we'll use the legacy data_model table
        return AppContext.db.find(DataModel::class.java, id)
    }

    override fun findAll(): List<DataModel> {
        // This method would need tenant context to determine the correct table
        // For now, we'll use the legacy data_model table
        return AppContext.db.find(DataModel::class.java).findList()
    }

    override fun findAllByEntityName(
        entityName: String,
        versionName: String?,
        pageNo: Int,
        pageSize: Int
    ): PagedList<DataModel> {
        val tenantId = CurrentUser.getTenant()?.id
            ?: throw IllegalStateException("No tenant in context")
        val tableName = SchemaModel.generateTableName(tenantId, entityName)

        val sql = if (versionName != null) {
            """
                SELECT * FROM $tableName 
                WHERE entity_name = :entityName 
                AND tenant_id = :tenantId
                AND version_name = :versionName
                ORDER BY when_created DESC
            """.trimIndent()
        } else {
            """
                SELECT * FROM $tableName 
                WHERE entity_name = :entityName 
                AND tenant_id = :tenantId
                ORDER BY when_created DESC
            """.trimIndent()
        }

        val rawSql = createDataModelRawSql(sql)

        val query = AppContext.db.find(DataModel::class.java)
            .setRawSql(rawSql)
            .setParameter("entityName", entityName)
            .setParameter("tenantId", tenantId)

        if (versionName != null) {
            query.setParameter("versionName", versionName)
        }

        return query
            .setFirstRow((pageNo - 1) * pageSize)
            .setMaxRows(pageSize)
            .findPagedList()
    }

    override fun update(dataModel: DataModel): DataModel {
        val tenantId = dataModel.tenantId
        val tableName = SchemaModel.generateTableName(tenantId, dataModel.entityName)

        val dataJson = objectMapper.writeValueAsString(dataModel.data)
        val now = Timestamp.from(Instant.now())
        val sql = """
                UPDATE $tableName 
                SET data = cast(:data as jsonb), version_name = :versionName, when_modified = whenModified, version = version + 1
                WHERE unique_identifier = :uniqueIdentifier AND entity_name = :entityName AND tenant_id = :tenantId
                RETURNING id, when_modified, version
            """.trimIndent()


        AppContext.db.dataSource().connection.use { conn ->

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, dataJson)
                stmt.setString(2, dataModel.versionName)
                stmt.setTimestamp(3, now)
                stmt.setString(4, dataModel.uniqueIdentifier)
                stmt.setString(5, dataModel.entityName)
                stmt.setLong(6, tenantId)

                val rs = stmt.executeQuery()
                if (rs.next()) {
                    dataModel.whenModified = rs.getTimestamp("when_modified").toInstant()
                    dataModel.version = rs.getInt("version")
                    return dataModel
                } else {
                    throw RuntimeException("Failed to update DataModel")
                }
            }
        }
    }

    override fun deleteById(id: Long): Boolean {
        // This method would need tenant context to determine the correct table
        val dataModel = findById(id)
        return if (dataModel != null) {
            deleteByUniqueIdentifier(dataModel.entityName, dataModel.uniqueIdentifier)
        } else {
            false
        }
    }

    override fun findByUniqueIdentifier(entityName: String, uniqueIdentifier: String): DataModel? {
        val tenantId = CurrentUser.getTenant()?.id
            ?: throw IllegalStateException("No tenant in context")
        val tableName = SchemaModel.generateTableName(tenantId, entityName)

        val sql = """
            SELECT * FROM $tableName 
            WHERE unique_identifier = :uniqueIdentifier 
            AND entity_name = :entityName 
            AND tenant_id = :tenantId
        """.trimIndent()

        val rawSql = createDataModelRawSql(sql)

        return AppContext.db.find(DataModel::class.java)
            .setRawSql(rawSql)
            .setParameter("uniqueIdentifier", uniqueIdentifier)
            .setParameter("entityName", entityName)
            .setParameter("tenantId", tenantId)
            .findOne()
    }

    override fun findByUniqueIdentifiers(
        entityName: String,
        uniqueIdentifiers: List<String>
    ): List<DataModel> {
        if (uniqueIdentifiers.isEmpty()) return emptyList()

        val tenantId = CurrentUser.getTenant()?.id
            ?: throw IllegalStateException("No tenant in context")
        val tableName = SchemaModel.generateTableName(tenantId, entityName)

        val placeholders = uniqueIdentifiers.joinToString(",") { ":uid${uniqueIdentifiers.indexOf(it)}" }
        val sql = """
            SELECT * FROM $tableName 
            WHERE entity_name = :entityName 
            AND tenant_id = :tenantId 
            AND unique_identifier IN ($placeholders)
        """.trimIndent()

        val rawSql = createDataModelRawSql(sql)

        val query = AppContext.db.find(DataModel::class.java)
            .setRawSql(rawSql)
            .setParameter("entityName", entityName)
            .setParameter("tenantId", tenantId)
            .setParameters()

        uniqueIdentifiers.forEachIndexed { index, identifier ->
            query.setParameter("uid$index", identifier)
        }

        return query.findList()
    }

    override fun deleteByUniqueIdentifier(entityName: String, uniqueIdentifier: String): Boolean {
        val tenantId = CurrentUser.getTenant()?.id
            ?: throw IllegalStateException("No tenant in context")
        val tableName = SchemaModel.generateTableName(tenantId, entityName)
        val sql = """
                DELETE FROM $tableName 
                WHERE unique_identifier = :uniqueIdentifier AND entity_name = :entityName AND tenant_id = :tenantId
            """.trimIndent()
        return AppContext.db.sqlUpdate(sql)
            .setParameter("uniqueIdentifier", uniqueIdentifier)
            .setParameter("entityName", entityName)
            .setParameter("tenantId", tenantId)
            .execute() > 0

    }

    override fun search(
        entityName: String,
        filters: List<FilterDto>,
        pageNo: Int,
        pageSize: Int
    ): PagedList<DataModel> {
        val tenantId = CurrentUser.getTenant()?.id
            ?: throw IllegalStateException("No tenant in context")
        val tableName = SchemaModel.generateTableName(tenantId, entityName)

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
            "tenant_id = :tenantId" to mapOf("tenantId" to tenantId)
        )

        // Combine all conditions
        val allConditions = baseConditions + filterConditions
        val whereConditions = allConditions.map { it.first }
        val allParameters = allConditions.flatMap { it.second.toList() }.toMap()

        val sql = """
            SELECT * FROM $tableName 
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
    
    private fun buildSimpleOperatorCondition(filter: FilterDto, paramKey: String, operator: String): Pair<String, Map<String, Any>> {
        val jsonPathChain = parseJsonPathToChain(filter.jsonPath)
        val castType = if (filter.operator.requiresNumericCasting()) "numeric" else "text"
        return "($jsonPathChain)::$castType $operator :$paramKey" to mapOf(paramKey to filter.value)
    }
    
    private fun buildListOperatorCondition(filter: FilterDto, paramKey: String, operator: String): Pair<String, Map<String, Any>> {
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
    
    private fun buildJsonOperatorCondition(filter: FilterDto, paramKey: String, operator: String): Pair<String, Map<String, Any>> {
        val jsonPathChain = parseJsonPathToChain(filter.jsonPath)
        val serializedValue = objectMapper.writeValueAsString(filter.value)
        return "$jsonPathChain $operator cast(:$paramKey as jsonb)" to mapOf(paramKey to serializedValue)
    }
    
    private fun buildDataRootOperatorCondition(filter: FilterDto, paramKey: String, operator: String): Pair<String, Map<String, Any>> {
        return "data $operator :$paramKey" to mapOf(paramKey to filter.value.toString())
    }

    override fun findSchemaByEntityAndVersion(entityName: String, versionName: String): SchemaModel? {
        val tenantId = CurrentUser.getTenant()?.id
            ?: throw IllegalStateException("No tenant in context")
        
        return AppContext.db.find(SchemaModel::class.java)
            .where()
            .eq("entityName", entityName)
            .eq("versionName", versionName)
            .eq("tenantId", tenantId)
            .findOne()
    }

    override fun validateSchemaExistsForEntityAndVersion(entityName: String, versionName: String?) {
        val tenantId = CurrentUser.getTenant()?.id
            ?: throw IllegalStateException("No tenant in context")
        
        val query = AppContext.db.find(SchemaModel::class.java)
            .where()
            .eq("entityName", entityName)
            .eq("tenantId", tenantId)

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