package my.baas.services

import delight.nashornsandbox.NashornSandbox
import delight.nashornsandbox.NashornSandboxes
import my.baas.config.AppContext.objectMapper
import my.baas.models.DataModel
import my.baas.models.SchemaModel
import org.slf4j.LoggerFactory

enum class LifecycleEvent {
    BEFORE_SAVE,
    AFTER_SAVE,
    AFTER_LOAD,
    MIGRATE_VERSION,
    BEFORE_DELETE,
    AFTER_DELETE
}

object JavaScriptExecutionService {

    private val logger = LoggerFactory.getLogger(JavaScriptExecutionService::class.java)
    private val sandbox: NashornSandbox = NashornSandboxes.create()

    init {
        // Configure sandbox with reasonable limits
        sandbox.setMaxCPUTime(5000) // 5 seconds max execution time
        sandbox.setMaxMemory(50 * 1024 * 1024) // 50MB max memory
        sandbox.setMaxPreparedStatements(100)
        sandbox.allowLoadFunctions(false)
        sandbox.allowExitFunctions(false)
        sandbox.allowGlobalsObjects(false)
        sandbox.allowPrintFunctions(false)
        sandbox.allowReadFunctions(false)

        // Add safe JavaScript utilities
        sandbox.eval(
            """
            var console = {
                log: function() { /* no-op */ },
                warn: function() { /* no-op */ },
                error: function() { /* no-op */ }
            };
        """.trimIndent()
        )
    }

    fun executeLifecycleScript(
        schema: SchemaModel,
        event: LifecycleEvent,
        dataModel: DataModel? = null,
        oldData: Map<String, Any>? = null
    ): Any? {
        val script = schema.lifecycleScripts[event] ?: return null

        return try {
            // Prepare context variables
            val context = mutableMapOf<String, Any>()

            dataModel?.let { dm ->
                context["data"] = dm.data
                context["entityName"] = dm.entityName
                context["versionName"] = dm.versionName
                context["uniqueIdentifier"] = dm.uniqueIdentifier
            }

            oldData?.let { 
                // For MIGRATE_VERSION event, oldData contains migration metadata
                if (event == LifecycleEvent.MIGRATE_VERSION) {
                    context["oldVersion"] = it["oldVersion"] ?: ""
                    context["newVersion"] = it["newVersion"] ?: ""
                    context["oldData"] = it["data"] ?: mapOf<String, Any>()
                } else {
                    context["oldData"] = it
                }
            }

            // Convert context to JSON for JavaScript consumption
            val contextJson = objectMapper.writeValueAsString(context)

            // Create a wrapper script that provides the context
            val wrappedScript = """
                var ctx = JSON.parse('${contextJson.replace("'", "\\'")}');
                var data = ctx.data || {};
                var oldData = ctx.oldData || {};
                var entityName = ctx.entityName || "";
                var versionName = ctx.versionName || "";
                var uniqueIdentifier = ctx.uniqueIdentifier || "";
                var oldVersion = ctx.oldVersion || "";
                var newVersion = ctx.newVersion || "";
                
                // User script
                (function() {
                    $script
                })();
            """.trimIndent()

            // Execute the script
            sandbox.eval(wrappedScript)
        } catch (e: Exception) {
            // Log error but don't fail the operation
            logger.error("JavaScript execution error for ${event.name}: ${e.message}", e)
            null
        }
    }

}