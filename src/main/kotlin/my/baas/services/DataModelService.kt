package my.baas.services

import com.fasterxml.jackson.databind.JsonNode
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import io.ebean.PagedList
import io.javalin.http.BadRequestResponse
import io.javalin.http.NotFoundResponse
import my.baas.auth.CurrentUser
import my.baas.config.AppContext
import my.baas.config.AppContext.objectMapper
import my.baas.controllers.SearchRequest
import my.baas.models.*
import my.baas.repositories.DataRepository
import my.baas.repositories.DataRepositoryImpl
import my.baas.repositories.DataSearchRepository
import my.baas.repositories.DataSearchRepositoryImpl
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*

enum class SearchType {
    EQ,    // Equal
    HAS,   // Contains
    LT,    // Less than
    LE,    // Less than or equal
    GT,    // Greater than
    GE     // Greater than or equal
}

class DataModelService(
    private val repository: DataRepository = DataRepositoryImpl(),
    private val searchRepository: DataSearchRepository = DataSearchRepositoryImpl(),
    private val jsonPathExtractor: JsonPathExtractor = JsonPathExtractor,
    private val jsExecutionService: JavaScriptExecutionService = JavaScriptExecutionService,
    private val eventManager: WebSocketEventManager = WebSocketEventManager,
    private val redisPublisher: RedisEventPublisher = RedisEventPublisher
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)

    fun create(entityName: String, versionName: String, data: Map<String, Any>): DataModel {
        val schema = loadSchemaByEntityAndVersion(entityName, versionName)
        val uniqueIdentifier = generateUniqueIdentifier(schema, data, entityName)

        val dataModel = DataModel(
            schema = schema,
            data = data,
            uniqueIdentifier = uniqueIdentifier,
            entityName = entityName,
            versionName = versionName
        )

        validateDataAgainstSchema(dataModel)

        // Execute beforeSave hook
        jsExecutionService.executeLifecycleScript(schema, LifecycleEvent.BEFORE_SAVE, dataModel)

        val savedDataModel = repository.save(dataModel)
        indexDataModel(savedDataModel)

        // Execute afterSave hook
        jsExecutionService.executeLifecycleScript(schema, LifecycleEvent.AFTER_SAVE, savedDataModel)

        // Log audit trail
        AuditService.logDataModelAction(AuditAction.CREATE, savedDataModel)

        // Publish WebSocket event
        publishEvent(EventType.CREATED, entityName, savedDataModel.uniqueIdentifier, versionName, savedDataModel.data)

        return savedDataModel
    }

    fun findAllByEntityName(
        entityName: String,
        versionName: String?,
        pageNo: Int,
        pageSize: Int
    ): PagedList<DataModel> {
        return repository.findAllByEntityName(entityName, versionName, pageNo, pageSize)
    }

    fun searchWithFilters(entityName: String, searchRequest: SearchRequest): List<DataModel> {
        val uniqueIdentifiers = searchRepository.searchWithMultipleFilters(entityName, searchRequest)

        if (uniqueIdentifiers.isEmpty()) {
            return emptyList()
        }

        return repository.findByUniqueIdentifiers(entityName, uniqueIdentifiers)
    }

    fun findByUniqueIdentifier(entityName: String, uniqueIdentifier: String): DataModel? {
        val dataModel = repository.findByUniqueIdentifier(entityName, uniqueIdentifier)

        // Execute afterLoad hook if dataModel exists
        dataModel?.let { dm ->
            jsExecutionService.executeLifecycleScript(dm.schema, LifecycleEvent.AFTER_LOAD, dm)
        }

        return dataModel
    }

    fun update(uniqueIdentifier: String, entityName: String, versionName: String, data: Map<String, Any>): DataModel? {
        val existingDataModel = repository.findByUniqueIdentifier(entityName, uniqueIdentifier) ?: return null
        val schema = loadSchemaByEntityAndVersion(entityName, versionName)
        val oldData = existingDataModel.data

        existingDataModel.schema = schema
        existingDataModel.data = data
        existingDataModel.entityName = entityName
        existingDataModel.versionName = versionName

        validateDataAgainstSchema(existingDataModel)

        // Execute beforeSave hook
        jsExecutionService.executeLifecycleScript(schema, LifecycleEvent.BEFORE_SAVE, existingDataModel, oldData)

        // Remove old indexed data
        searchRepository.deleteByEntityNameAndUniqueIdentifier(entityName, uniqueIdentifier)

        val updatedDataModel = repository.update(existingDataModel)
        indexDataModel(updatedDataModel)

        // Execute afterSave hook
        jsExecutionService.executeLifecycleScript(schema, LifecycleEvent.AFTER_SAVE, updatedDataModel, oldData)

        // Log audit trail
        AuditService.logDataModelAction(AuditAction.UPDATE, updatedDataModel, oldData)

        // Publish WebSocket event
        publishEvent(
            EventType.UPDATED,
            entityName,
            updatedDataModel.uniqueIdentifier,
            versionName,
            updatedDataModel.data
        )

        return updatedDataModel
    }

    fun patch(
        uniqueIdentifier: String,
        entityName: String,
        versionName: String,
        patchData: Map<String, Any>
    ): DataModel? {
        val existingDataModel = repository.findByUniqueIdentifier(entityName, uniqueIdentifier) ?: return null
        val schema = loadSchemaByEntityAndVersion(entityName, versionName)
        val oldData = existingDataModel.data

        // Merge existing data with patch data
        val mergedData = deepMergeData(existingDataModel.data, patchData)

        existingDataModel.schema = schema
        existingDataModel.data = mergedData
        existingDataModel.entityName = entityName
        existingDataModel.versionName = versionName

        validateDataAgainstSchema(existingDataModel)

        // Execute beforeSave hook
        jsExecutionService.executeLifecycleScript(schema, LifecycleEvent.BEFORE_SAVE, existingDataModel, oldData)

        // Remove old indexed data
        searchRepository.deleteByEntityNameAndUniqueIdentifier(entityName, uniqueIdentifier)

        val updatedDataModel = repository.update(existingDataModel)
        indexDataModel(updatedDataModel)

        // Execute afterSave hook
        jsExecutionService.executeLifecycleScript(schema, LifecycleEvent.AFTER_SAVE, updatedDataModel, oldData)

        // Log audit trail
        AuditService.logDataModelAction(AuditAction.PATCH, updatedDataModel, oldData)

        // Publish WebSocket event
        publishEvent(
            EventType.PATCHED,
            entityName,
            updatedDataModel.uniqueIdentifier,
            versionName,
            updatedDataModel.data
        )

        return updatedDataModel
    }

    fun deleteByUniqueIdentifier(entityName: String, uniqueIdentifier: String): Boolean {
        val dataModel = repository.findByUniqueIdentifier(entityName, uniqueIdentifier) ?: return false
        // Execute beforeDelete hook
        jsExecutionService.executeLifecycleScript(dataModel.schema, LifecycleEvent.BEFORE_DELETE, dataModel)

        // Remove indexed data
        searchRepository.deleteByEntityNameAndUniqueIdentifier(dataModel.entityName, uniqueIdentifier)

        val deleted = repository.deleteByUniqueIdentifier(entityName, uniqueIdentifier)

        if (deleted) {
            // Execute afterDelete hook
            jsExecutionService.executeLifecycleScript(dataModel.schema, LifecycleEvent.AFTER_DELETE, dataModel)

            // Log audit trail
            AuditService.logDataModelAction(AuditAction.DELETE, dataModel, dataModel.data)

            // Publish WebSocket event
            publishEvent(EventType.DELETED, entityName, uniqueIdentifier, dataModel.versionName, null)
        }

        return deleted
    }

    fun getSchema(entityName: String, versionName: String): Map<String, Any> {
        val schema = loadSchemaByEntityAndVersion(entityName, versionName)
        return schema.jsonSchema
    }

    fun validatePayload(entityName: String, versionName: String, payload: Map<String, Any>): Map<String, Any> {
        val schema = loadSchemaByEntityAndVersion(entityName, versionName)

        // If validation is disabled for this schema, return valid without checking
        if (!schema.isValidationEnabled) {
            return mapOf(
                "valid" to true,
                "errors" to emptyList<String>(),
                "validationSkipped" to true,
                "reason" to "Schema validation is disabled for this entity/version"
            )
        }

        val schemaNode: JsonNode = objectMapper.valueToTree(schema.jsonSchema)
        val dataNode: JsonNode = objectMapper.valueToTree(payload)

        val jsonSchema = schemaFactory.getSchema(schemaNode)
        val validationResult: Set<ValidationMessage> = jsonSchema.validate(dataNode)

        return if (validationResult.isEmpty()) {
            mapOf(
                "valid" to true,
                "errors" to emptyList<String>()
            )
        } else {
            mapOf(
                "valid" to false,
                "errors" to validationResult.map { error ->
                    mapOf(
                        "path" to error.path,
                        "message" to error.message,
                        "type" to error.type
                    )
                }
            )
        }
    }

    fun migrateVersion(entityName: String, uniqueIdentifier: String, destinationVersion: String): DataModel {
        // Check if destination version exists
        validateSchemaExistsForEntityAndVersion(entityName, destinationVersion)

        // Load the existing data model
        val existingDataModel = repository.findByUniqueIdentifier(entityName, uniqueIdentifier)
            ?: throw NotFoundResponse("DataModel not found for entity: '$entityName', uniqueIdentifier: '$uniqueIdentifier'")

        // Load the destination schema
        val destinationSchema = loadSchemaByEntityAndVersion(entityName, destinationVersion)

        // Store the old version and data for the migration script
        val oldVersionName = existingDataModel.versionName
        val oldData = existingDataModel.data.toMutableMap()

        // Execute the MIGRATE_VERSION lifecycle script if it exists
        // The script can modify the data during migration
        val migrationResult = jsExecutionService.executeLifecycleScript(
            destinationSchema,
            LifecycleEvent.MIGRATE_VERSION,
            existingDataModel,
            mapOf(
                "oldVersion" to oldVersionName,
                "newVersion" to destinationVersion,
                "data" to oldData
            )
        )

        // If migration script returned modified data, use it
        val migratedData = when (migrationResult) {
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                migrationResult as Map<String, Any>
            }

            else -> existingDataModel.data
        }

        // Update the data model with new version and potentially modified data
        existingDataModel.schema = destinationSchema
        existingDataModel.versionName = destinationVersion
        existingDataModel.data = migratedData

        // Validate the migrated data against the new schema
        validateDataAgainstSchema(existingDataModel)

        // Remove old indexed data
        searchRepository.deleteByEntityNameAndUniqueIdentifier(entityName, uniqueIdentifier)

        // Save the updated model
        val updatedDataModel = repository.update(existingDataModel)

        // Re-index with new schema's indexed paths
        indexDataModel(updatedDataModel)

        // Log audit trail
        AuditService.logDataModelAction(
            AuditAction.MIGRATE,
            updatedDataModel,
            oldData,
            "Migrated from version $oldVersionName to $destinationVersion"
        )

        // Publish WebSocket event
        publishEvent(
            EventType.MIGRATED,
            entityName,
            updatedDataModel.uniqueIdentifier,
            destinationVersion,
            updatedDataModel.data
        )

        return updatedDataModel
    }

    fun reindexDataModels(entityName: String?, modifiedAfter: Instant): Map<String, Any?> {
        val startTime = System.currentTimeMillis()

        try {
            val query = AppContext.db.find(DataModel::class.java).where()

            // Filter by entity name if provided
            entityName?.let { query.eq("entityName", it) }

            // Filter by modification date
            query.ge("whenModified", modifiedAfter)

            val dataModels = query.findList()

            var reindexedCount = 0
            val errors = mutableListOf<String>()

            dataModels.forEach { dataModel ->
                try {
                    // Remove old indexed data first
                    searchRepository.deleteByEntityNameAndUniqueIdentifier(
                        dataModel.entityName,
                        dataModel.uniqueIdentifier
                    )

                    // Re-index the data model
                    indexDataModel(dataModel)
                    reindexedCount++

                    // Log audit trail for re-indexing
                    AuditService.logAction(
                        AuditAction.RE_INDEX,
                        dataModel.entityName,
                        dataModel.uniqueIdentifier,
                        notes = "Re-indexed data model modified after $modifiedAfter"
                    )

                } catch (e: Exception) {
                    val error = "Failed to re-index ${dataModel.entityName}/${dataModel.uniqueIdentifier}: ${e.message}"
                    errors.add(error)
                    logger.error(error, e)
                }
            }

            val duration = System.currentTimeMillis() - startTime

            return mapOf(
                "success" to true,
                "totalFound" to dataModels.size,
                "reindexedCount" to reindexedCount,
                "errors" to errors,
                "durationMs" to duration,
                "entityName" to entityName,
                "modifiedAfter" to modifiedAfter.toString()
            )

        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            logger.error("Re-indexing operation failed", e)

            return mapOf(
                "success" to false,
                "error" to e.message,
                "durationMs" to duration,
                "entityName" to entityName,
                "modifiedAfter" to modifiedAfter.toString()
            )
        }
    }

    private fun validateDataAgainstSchema(dataModel: DataModel) {
        // Skip validation if disabled for this schema
        if (!dataModel.schema.isValidationEnabled) {
            return
        }

        val schemaNode: JsonNode = objectMapper.valueToTree(dataModel.schema.jsonSchema)
        val dataNode: JsonNode = objectMapper.valueToTree(dataModel.data)

        val jsonSchema = schemaFactory.getSchema(schemaNode)
        val validationResult: Set<ValidationMessage> = jsonSchema.validate(dataNode)

        if (validationResult.isNotEmpty()) {
            val errorMessages = validationResult.joinToString("; ") { it.message }
            throw BadRequestResponse("Data validation failed: $errorMessages")
        }
    }

    fun validateSchemaExistsForEntity(entityName: String) {
        return validateSchemaExistsForEntityAndVersion(entityName)
    }

    fun validateSchemaExistsForEntityAndVersion(entityName: String, versionName: String? = null) {
        val schemaExists = AppContext.db.find(SchemaModel::class.java)
            .where()
            .eq("entityName", entityName)
            .apply {
                if (versionName != null) {
                    eq("versionName", versionName)
                }
            }
            .exists()

        if (!schemaExists) {
            throw NotFoundResponse("Schema not found for entity: '$entityName', version: '$versionName'")
        }
    }

    private fun loadSchemaByEntityAndVersion(entityName: String, versionName: String): SchemaModel {
        return AppContext.db.find(SchemaModel::class.java)
            .where()
            .eq("entityName", entityName)
            .eq("versionName", versionName)
            .findOne()
            ?: throw NotFoundResponse("Schema not found for entity: '$entityName', version: '$versionName'")
    }

    private fun generateUniqueIdentifier(schema: SchemaModel, data: Map<String, Any>, entityName: String): String {
        val formatter = schema.uniqueIdentifierFormatter.replace("{entity}", entityName)
        var identifier = formatter

        // Replace system placeholders
        identifier = identifier.replace("{timestamp}", System.currentTimeMillis().toString())
        identifier = identifier.replace("{uuid}", UUID.randomUUID().toString())
        identifier = identifier.replace("{date}", SimpleDateFormat("yyyyMMdd").format(Date()))
        identifier = identifier.replace("{datetime}", SimpleDateFormat("yyyyMMdd_HHmmss").format(Date()))

        // Replace placeholders in the formatter with actual data values
        data.forEach { (key, value) ->
            identifier = identifier.replace("{$key}", value.toString())
        }

        return identifier.replace(Regex("[^a-zA-Z0-9_\\-\\/]"), "").uppercase()
    }

    private fun publishEvent(
        eventType: EventType,
        entityName: String,
        uniqueIdentifier: String,
        versionName: String,
        data: Map<String, Any>?
    ) {
        // Get current tenant ID from context
        val tenantId = CurrentUser.getTenant()?.id ?: return // Skip if no tenant in context

        val event = DataChangeEvent(
            eventType = eventType,
            entityName = entityName,
            uniqueIdentifier = uniqueIdentifier,
            versionName = versionName,
            tenantId = tenantId,
            data = data
        )

        if (redisPublisher.isRedisEnabled()) {
            // Publish to Redis - it will distribute to all instances
            redisPublisher.publishEvent(event)
        } else {
            // Publish directly to local WebSocket clients
            eventManager.publishEventToLocalClients(event)
        }
    }

    private fun indexDataModel(dataModel: DataModel) {
        val indexedPaths = dataModel.schema.indexedJsonPaths
        if (indexedPaths.isEmpty()) return

        val searchEntities = mutableListOf<DataSearchModel>()

        indexedPaths.forEach { (jsonPathStr, shouldIndex) ->
            if (shouldIndex) {
                val extractedValues = jsonPathExtractor.extractValueWithArrayIndex(dataModel.data, jsonPathStr)

                extractedValues.forEach { extractedValue ->
                    val valueMap = jsonPathExtractor.createValueMap(extractedValue.value)
                    val jsonValueType = JsonValueType.determineJsonValueType(extractedValue.value)

                    val searchEntity = DataSearchModel(
                        entityName = dataModel.entityName,
                        uniqueIdentifier = dataModel.uniqueIdentifier,
                        jsonPath = jsonPathStr,
                        value = valueMap,
                        valueType = jsonValueType,
                        arrayIdx = extractedValue.arrayIndex,
                        dataCreatedAt = dataModel.whenCreated,
                        dataModifiedAt = dataModel.whenModified
                    )

                    // Set tenant information for the search entity
                    searchEntity.tenant = dataModel.tenant
                    searchEntity.tenantId = dataModel.tenantId

                    searchEntities.add(searchEntity)
                }
            }
        }

        if (searchEntities.isNotEmpty()) {
            searchRepository.saveAll(searchEntities)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun deepMergeData(existing: Map<String, Any>, patch: Map<String, Any>): Map<String, Any> {
        val result = existing.toMutableMap()

        patch.forEach { (key, patchValue) ->
            when {
                // If the key doesn't exist in existing, add it
                !result.containsKey(key) -> {
                    result[key] = patchValue
                }
                // If both values are Maps, merge them recursively
                result[key] is Map<*, *> && patchValue is Map<*, *> -> {
                    result[key] = deepMergeData(
                        result[key] as Map<String, Any>,
                        patchValue as Map<String, Any>
                    )
                }
                // If both values are Lists, replace the existing list with patch list
                result[key] is List<*> && patchValue is List<*> -> {
                    result[key] = patchValue
                }
                // For all other cases (primitives, null, type mismatches), replace with patch value
                else -> {
                    result[key] = patchValue
                }
            }
        }

        return result
    }

}