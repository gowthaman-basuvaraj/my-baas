package my.baas

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.json.JavalinJackson
import my.baas.auth.AuthHandler
import my.baas.auth.CurrentUser
import my.baas.config.AppContext
import my.baas.controllers.*
import my.baas.services.DataModelService
import my.baas.services.JobRunnerServiceHolder
import my.baas.services.RedisEventPublisher
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("my.baas.Application")
private val dataModelService = DataModelService()

fun main() {
    logger.info("Starting MyBaaS application...")

    AppContext.db
    logger.info("Database connection initialized")

    // Initialize Redis event publisher if enabled
    RedisEventPublisher.initialize()

    // Initialize Job Runner Service for report execution
    JobRunnerServiceHolder.instance
    logger.info("Report job runner initialized")

    Javalin
        .create { config ->
            config.jsonMapper(JavalinJackson(AppContext.objectMapper))
            config.router.apiBuilder {
                // Admin APIs - for managing tenants
                path("admin") {
                    after { CurrentUser.clear() }
                    path("tenants") {
                        get(TenantController::getAll)
                        post(TenantController::create)
                        path("{id}") {
                            get(TenantController::getOne)
                            put(TenantController::update)
                            delete(TenantController::delete)
                            post("activate", TenantController::activate)
                            post("deactivate", TenantController::deactivate)
                        }
                    }
                }

                // Regular APIs - tenant-scoped
                path("api") {
                    before(AuthHandler.handle)
                    path("schema") {
                        crud(SchemaController)
                    }
                    path("reports") {
                        get(ReportController::getAll)
                        post(ReportController::create)
                        get("scheduled", ReportController::getScheduledReports)
                        post("validate-sql", ReportController::validateSql)
                        path("{id}") {
                            get(ReportController::getOne)
                            put(ReportController::update)
                            delete(ReportController::delete)
                            post("activate", ReportController::activate)
                            post("deactivate", ReportController::deactivate)
                            get("history", ReportController::getExecutionHistory)
                        }
                        // Job management endpoints
                        post("jobs", ReportController::submitJob)
                        path("jobs/{jobId}") {
                            get(ReportController::getJobStatus)
                            post("cancel", ReportController::cancelJob)
                            get("download", ReportController::downloadResult)
                        }
                    }
                    path("data") {
                        get("subscriptions", DataModelController::getActiveSubscriptions)
                        path("{entityName}") {
                            before {
                                dataModelService.validateSchemaExistsForEntity(it.pathParam("entityName"))
                            }
                            post("reindex", DataModelController::reindexDataModels)
                            get(DataModelController::getAll)
                            post("search", DataModelController::search)
                            path("{versionName}") {
                                before {
                                    dataModelService.validateSchemaExistsForEntityAndVersion(
                                        it.pathParam("entityName"),
                                        it.pathParam("versionName")
                                    )
                                }
                                get(DataModelController::getAll)
                                get("schema", DataModelController::getSchema)
                                post("validate", DataModelController::validatePayload)
                                post(DataModelController::create)
                                path("{uniqueIdentifier}") {
                                    get(DataModelController::getOne)
                                    put(DataModelController::update)
                                    patch(DataModelController::patch)
                                    delete(DataModelController::delete)
                                    post("migrate", DataModelController::migrate)
                                }
                            }
                        }
                    }
                    after { CurrentUser.clear() }
                }
                // WebSocket endpoint for real-time events
                ws("/ws/events", WebSocketHandler::configure)
            }
        }
        .start(7070)

    logger.info("MyBaaS application started successfully on port 7070")
}
