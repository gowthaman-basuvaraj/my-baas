package my.baas.repositories

import io.ebean.ExpressionList
import io.ebean.PagedList
import io.javalin.http.BadRequestResponse
import my.baas.auth.CurrentUser
import my.baas.config.AppContext
import my.baas.config.AppContext.objectMapper as om
import my.baas.dto.FilterDto
import my.baas.dto.FilterOperator
import my.baas.models.DataModel
import my.baas.models.SchemaModel
import my.baas.services.TableManagementService.parseJsonPathToChain

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
        val firstValue = filter.getListValue().first()!!
        val castType = if (firstValue is Number) "numeric" else "text"
        val jsonPath = parseJsonPathToChain(filter.jsonPath)
        val jsonPathChain = "($jsonPath)::$castType"

        when (filter.operator) {
            FilterOperator.STRING_CONTAINS -> q.raw("$jsonPath::text ilike ?", "%${filter.value}%")
            FilterOperator.EQ -> q.raw("$jsonPath = cast(? as jsonb)", om.writeValueAsString(filter.value))
            FilterOperator.NE -> q.raw("$jsonPath <> cast(? as jsonb)", om.writeValueAsString(filter.value))
            FilterOperator.LT -> q.raw("$jsonPath < cast(? as jsonb)", om.writeValueAsString(filter.value))
            FilterOperator.LE -> q.raw("$jsonPath <= cast(? as jsonb)", om.writeValueAsString(filter.value))
            FilterOperator.GT -> q.raw("$jsonPath > cast(? as jsonb)", om.writeValueAsString(filter.value))
            FilterOperator.GE -> q.raw("$jsonPath >= cast(? as jsonb)", om.writeValueAsString(filter.value))
            FilterOperator.IN -> q.`in`(jsonPathChain, filter.value)
            FilterOperator.NOT_IN -> q.notIn(jsonPathChain, filter.value)
            FilterOperator.ARRAY_CONTAINS -> q.raw("$jsonPath ?? ?", firstValue)
            FilterOperator.CONTAINS -> q.raw(
                "$jsonPath @> cast(? as jsonb)",
                om.writeValueAsString(filter.value)
            )

            FilterOperator.CONTAINED_BY -> q.raw(
                "$jsonPath <@ cast(? as jsonb)",
                om.writeValueAsString(filter.value)
            )

            FilterOperator.HAS_KEY -> q.raw("$jsonPath ?? ?", firstValue)
            FilterOperator.HAS_ANY_KEYS -> q.raw("$jsonPath ??| ?", filter.getListValue())
            FilterOperator.HAS_ALL_KEYS -> q.raw("$jsonPath ??& ?", filter.getListValue())
            FilterOperator.PATH_EXISTS -> q.raw("$jsonPath @?? cast(? as jsonpath)", filter.value)
            FilterOperator.PATH_MATCH -> q.raw("$jsonPath @@ cast(? as jsonpath)", filter.value)
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