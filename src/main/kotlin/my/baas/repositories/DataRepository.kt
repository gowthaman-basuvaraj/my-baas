package my.baas.repositories

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ebean.PagedList
import io.ebean.RawSql
import io.ebean.RawSqlBuilder
import my.baas.auth.CurrentUser
import my.baas.config.AppContext
import my.baas.models.DataModel
import my.baas.models.SchemaModel
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
}

class DataRepositoryImpl : DataRepository {
    private val objectMapper = jacksonObjectMapper()

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
}