package my.baas.services

import com.jayway.jsonpath.JsonPath
import my.baas.config.AppContext
import my.baas.models.PartitionBy
import my.baas.models.SchemaModel
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.Executors

object TableManagementService {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val indexExecutor = Executors.newSingleThreadExecutor()

    fun createDataModelTable(schema: SchemaModel) {
        //tenant_id, application_id, schema_id, when_created
        val tenantId = schema.tenant.id
        val applicationId = schema.application.id
        val schemaId = schema.id
        val beginOf1900 = "1900-01-01"
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))


        AppContext.db.sqlUpdate(
            """
            CREATE TABLE ${schema.generateTableName("till_$now")} PARTITION OF data_model
            FOR VALUES FROM ('$tenantId','$applicationId', '$schemaId', '$beginOf1900') TO ('$tenantId','$applicationId', '$schemaId', '$now');
            """
        ).execute()


        if (schema.partitionBy == PartitionBy.MONTH) {
            val nextMonthDate = LocalDate.now()
                .plusMonths(1)
                .withDayOfMonth(1)

            val nextMonthSuffix = nextMonthDate
                .format(DateTimeFormatter.ofPattern("yyyy-MM"))

            val nextMonthTimeFmt = nextMonthDate
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

            AppContext.db.sqlUpdate(
                """
            CREATE TABLE ${schema.generateTableName(nextMonthSuffix)} PARTITION OF data_model
            FOR VALUES FROM ('$tenantId','$applicationId', '$schemaId', '$now') TO ('$tenantId','$applicationId', '$schemaId', '$nextMonthTimeFmt');
            """
            ).execute()
        } else {
            val nextYearDt = LocalDate.now().plusYears(1)
                .withDayOfMonth(1)
                .withMonth(1)

            val nextYearSuffix = nextYearDt
                .format(DateTimeFormatter.ofPattern("yyyy"))

            val nextYearTimeFmt = nextYearDt
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

            AppContext.db.sqlUpdate(
                """
            CREATE TABLE ${schema.generateTableName(nextYearSuffix)} PARTITION OF data_model
            FOR VALUES FROM ('$tenantId','$applicationId', '$schemaId', '$now') TO ('$tenantId','$applicationId', '$schemaId', '$nextYearTimeFmt');
            """
            ).execute()
        }

    }

    fun dropDataModelTable(tenantId: UUID, applicationId: UUID, entityName: String) {
        //fixme: detach partition
    }

    fun updateIndexes(
        schema: SchemaModel,
        oldIndexedPaths: List<String>,
        newIndexedPaths: List<String>,
        tenantId: UUID
    ) {
        val applicationId = schema.application.id
        val tableName = "data_model"
        //fixme: add a where clause for index creation and make it concurrent

        // Calculate differences
        val removedPaths = oldIndexedPaths - newIndexedPaths.toSet()
        val addedPaths = newIndexedPaths - oldIndexedPaths.toSet()

        logger.info("Updating indexes for table [$tableName]: [${removedPaths.size}] to remove, [${addedPaths.size}] to add")

        // Submit an async task for index management
        indexExecutor.submit {
            try {
                // Drop removed indexes first (synchronously)
                removedPaths.forEach { jsonPath ->
                    dropIndexForPath(tableName, jsonPath)
                }

                // Create new indexes concurrently
                addedPaths.forEach { jsonPath ->
                    createIndexForPath(tableName, jsonPath)
                }

                logger.info("Index update completed for table [$tableName]")
            } catch (e: Exception) {
                logger.error("Failed to update indexes for table [$tableName]", e)
            }
        }
    }

    private fun dropIndexForPath(tableName: String, jsonPath: String) {
        try {
            val cleanPath = jsonPath.replace(Regex("[^a-zA-Z0-9_]"), "_")
            val indexName = "${tableName}_${cleanPath}_gin_idx"

            val dropSql = "DROP INDEX IF EXISTS $indexName"
            AppContext.db.sqlUpdate(dropSql).execute()

            logger.info("Dropped index [$indexName] for path [$jsonPath]")
        } catch (e: Exception) {
            logger.warn("Failed to drop index for path [$jsonPath]: ${e.message}", e)
        }
    }

    private fun createIndexForPath(tableName: String, jsonPath: String) {
        try {
            val cleanPath = jsonPath.replace(Regex("[^a-zA-Z0-9_]"), "_")
            val indexName = "${tableName}_${cleanPath}_gin_idx"

            val pathChain = parseJsonPathToChain(jsonPath)
            if (pathChain.isNotEmpty()) {
                val pathIndexSql = """
                CREATE INDEX IF NOT EXISTS $indexName 
                ON $tableName USING GIN (($pathChain))
            """.trimIndent()

                AppContext.db.sqlUpdate(pathIndexSql).execute()
                logger.info("Created index [$indexName] for path [$jsonPath]")
            } else {
                logger.warn("Failed to create index for path [$jsonPath] => [$pathChain]")
            }
        } catch (e: Exception) {
            logger.warn("Failed to create index for path [$jsonPath]: ${e.message}", e)
        }
    }


    /**
     * Parses a JSON path like "user.profile.name" or "items[*].value" into a PostgreSQL -> operator chain
     * for use with GIN indexes on specific JSONB paths.
     * Examples:
     * - "user.name" -> "data -> 'user' -> 'name'"
     * - "items[*].value" -> "data -> 'items'" (indexes the array container for wildcard access)
     * - "items[0].value" -> "data -> 'items' -> 0 -> 'value'" (specific array index)
     * - "nested.array[*].field" -> "data -> 'nested' -> 'array'" (indexes the array container)
     */
    fun parseJsonPathToChain(jsonPath: String): String {
        // Validate JSON path using JsonPath library
        if (jsonPath.isBlank()) {
            throw IllegalArgumentException("JSON path cannot be empty or blank")
        }

        try {
            val openBrack = jsonPath.count { it == '[' }
            val closeBrack = jsonPath.count { it == ']' }
            if (openBrack != closeBrack) throw IllegalArgumentException("Brackets do not match Open = $openBrack, Close = $closeBrack")
            JsonPath.compile("$.$jsonPath")
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid JSON path: '$jsonPath' - ${e.message}", e)
        }

        // Use JsonPath's internal path structure to build PostgreSQL query
        return buildPostgresPath(jsonPath)
    }

    private fun buildPostgresPath(originalPath: String): String {
        // For PostgreSQL JSONB queries, we need to convert JsonPath to -> operator chain
        // Handle common patterns:

        // Check for wildcard arrays - if path contains [*], index only up to the array
        if (originalPath.contains("[*]")) {
            val wildcardIndex = originalPath.indexOf("[*]")
            val pathBeforeWildcard = originalPath.take(wildcardIndex)
            return if (pathBeforeWildcard.isEmpty()) {
                "data"
            } else {
                buildSimplePath(pathBeforeWildcard)
            }
        }

        // For regular paths, build the full chain
        return buildSimplePath(originalPath)
    }

    private fun buildSimplePath(path: String): String {
        val parts = mutableListOf<String>()
        var current = ""
        var inBrackets = false

        for (char in path) {
            when (char) {
                '.' if !inBrackets -> {
                    if (current.isNotEmpty()) {
                        parts.add("'$current'")
                        current = ""
                    }
                }
                '[' -> {
                    if (current.isNotEmpty()) {
                        parts.add("'$current'")
                        current = ""
                    }
                    inBrackets = true
                }
                ']' -> {
                    if (current.isNotEmpty()) {
                        // Array index should be unquoted (numeric)
                        parts.add(current)
                        current = ""
                    }
                    inBrackets = false
                }
                else -> {
                    current += char
                }
            }
        }

        // Add remaining part
        if (current.isNotEmpty()) {
            parts.add("'$current'")
        }

        return if (parts.isEmpty()) "data" else "data -> ${parts.joinToString(" -> ")}"
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("Testing parseJsonPathToChain function:")
        println("=".repeat(50))

        val validTestCases = listOf(
            // Basic field access
            "user" to "data -> 'user'",
            "user.name" to "data -> 'user' -> 'name'",
            "user.profile.email" to "data -> 'user' -> 'profile' -> 'email'",

            // Specific array indices
            "items[0]" to "data -> 'items' -> 0",
            "items[0].value" to "data -> 'items' -> 0 -> 'value'",
            "orders[1].status" to "data -> 'orders' -> 1 -> 'status'",
            "nested[5].array[2].field" to "data -> 'nested' -> 5 -> 'array' -> 2 -> 'field'",

            // Wildcard arrays (should index container only)
            "items[*]" to "data -> 'items'",
            "items[*].value" to "data -> 'items'",
            "orders[*].status" to "data -> 'orders'",
            "nested.array[*]" to "data -> 'nested' -> 'array'",
            "nested.array[*].field" to "data -> 'nested' -> 'array'",
            "products[*].details.price" to "data -> 'products'",

            // Complex nested cases
            "company.employees[*].department" to "data -> 'company' -> 'employees'",
            "settings.notifications[0].enabled" to "data -> 'settings' -> 'notifications' -> 0 -> 'enabled'",
            "config.servers[*].config.port" to "data -> 'config' -> 'servers'",

            // Valid edge cases
            "single" to "data -> 'single'",
            "array[10]" to "data -> 'array' -> 10"
        )

        val invalidTestCases = listOf(
            "",              // Empty path
            "   ",           // Whitespace  
            "user[0",        // Unclosed bracket
            "user0]",        // Unopened bracket
            "user[[0]]",     // Nested brackets
            "user[0.5]"      // Invalid array index
        )

        var passed = 0
        var failed = 0

        // Test valid cases
        println("Testing valid JSON paths:")
        println("-".repeat(30))
        validTestCases.forEach { (input, expected) ->
            try {
                val result = parseJsonPathToChain(input)
                if (result == expected) {
                    println("✓ PASS: '$input' -> '$result'")
                    passed++
                } else {
                    println("✗ FAIL: '$input'")
                    println("  Expected: '$expected'")
                    println("  Got:      '$result'")
                    failed++
                }
            } catch (e: Exception) {
                println("✗ ERROR: '$input' threw exception: ${e.message}")
                failed++
            }
        }

        println()
        println("Testing invalid JSON paths (should throw exceptions):")
        println("-".repeat(50))
        invalidTestCases.forEach { input ->
            try {
                val result = parseJsonPathToChain(input)
                println("✗ FAIL: '$input' should have thrown exception but got: '$result'")
                failed++
            } catch (e: IllegalArgumentException) {
                println("✓ PASS: '$input' correctly threw: ${e.message}")
                passed++
            } catch (e: Exception) {
                println("✗ FAIL: '$input' threw unexpected exception: ${e.javaClass.simpleName}: ${e.message}")
                failed++
            }
        }

        println("=".repeat(50))
        println("Test Summary: $passed passed, $failed failed")

        if (failed == 0) {
            println("🎉 All tests passed!")
        } else {
            println("❌ Some tests failed. Please check the implementation.")
        }
    }
}