package my.baas.repositories

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ebean.ExpressionList
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
            .eq("application_id", CurrentUser.get().applicationId ?: throw BadRequestResponse())
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

        if (filters.isEmpty()) {
            return findAllByEntityName(entityName, null, pageNo, pageSize)
        }

        val q = AppContext.db.find(DataModel::class.java)
            .where()
            .eq("entityName", entityName)
            .eq("tenant_id", tenantId)
            .eq("application_id", applicationId)

        // Build filter conditions functionally
        filters.forEach { filter ->
            buildFilterCondition(filter, q)
        }


        return q
            .setFirstRow((pageNo - 1) * pageSize)
            .setMaxRows(pageSize)
            .findPagedList()
    }

    private fun buildFilterCondition(filter: FilterDto, q: ExpressionList<DataModel>) {
        val castType = if (filter.value is Number) "numeric" else "text"
        val jsonPath = parseJsonPathToChain(filter.jsonPath)
        val jsonPathChain = "($jsonPath)::$castType"

        when(filter.operator){
            FilterOperator.EQ -> q.raw("$jsonPathChain = ?", filter.value)
            FilterOperator.NE -> q.raw("$jsonPathChain <> ?", filter.value)
            FilterOperator.LT -> q.raw("$jsonPathChain < ?", filter.value)
            FilterOperator.LE -> q.raw("$jsonPathChain <= ?", filter.value)
            FilterOperator.GT -> q.raw("$jsonPathChain > ?", filter.value)
            FilterOperator.GE -> q.raw("$jsonPathChain >= ?", filter.value)
            FilterOperator.IN -> q.`in`(jsonPathChain, filter.value)
            FilterOperator.NOT_IN -> q.notIn(jsonPathChain, filter.value)
            FilterOperator.ARRAY_CONTAINS -> TODO()
            FilterOperator.CONTAINS -> TODO()
            FilterOperator.CONTAINED_BY -> TODO()
            FilterOperator.HAS_KEY -> q.raw("$jsonPathChain ?", filter.value) //fixme
            FilterOperator.HAS_ANY_KEYS ->  q.raw("$jsonPathChain ?|", filter.value) //fixme
            FilterOperator.HAS_ALL_KEYS -> q.raw("$jsonPathChain ?&", filter.value) //fixme
            FilterOperator.PATH_EXISTS -> q.jsonExists("data", jsonPathChain)
            FilterOperator.PATH_MATCH -> q.raw("$jsonPathChain @@ ?", filter.value)
        }

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
            .eq("tenant_id", tenantId)
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
            .eq("tenant_id", tenantId)
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