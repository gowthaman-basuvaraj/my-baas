package my.baas

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.json.JavalinJackson
import my.baas.auth.AdminAuthHandler
import my.baas.auth.AuthHandler
import my.baas.auth.CurrentUser
import my.baas.config.AppContext
import my.baas.controllers.DataModelController
import my.baas.controllers.SchemaController
import my.baas.controllers.TenantController
import my.baas.controllers.WebSocketHandler
import my.baas.services.DataModelService
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


    Javalin
        .create { config ->
            config.jsonMapper(JavalinJackson(AppContext.objectMapper))
            config.router.apiBuilder {
                // Admin APIs - for managing tenants
                path("admin") {
                    before(AdminAuthHandler.handle)
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
                    after { CurrentUser.clear() }
                    path("schema") {
                        crud(SchemaController)
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
                }
                // WebSocket endpoint for real-time events
                ws("/ws/events", WebSocketHandler::configure)
            }
        }
        .start(7070)

    logger.info("MyBaaS application started successfully on port 7070")
}
