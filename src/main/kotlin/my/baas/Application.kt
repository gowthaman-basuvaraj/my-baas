package my.baas

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.http.NotFoundResponse
import io.javalin.json.JavalinJackson
import my.baas.auth.AuthHandler
import my.baas.auth.CurrentUser
import my.baas.config.AppContext
import my.baas.controllers.DataModelController
import my.baas.controllers.SchemaController
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
                                dataModelService.validateEntityExists(it.pathParam("entityName"))
                            }
                            post("reindex", DataModelController::reindexDataModels)
                            get(DataModelController::getAll)
                            post("search", DataModelController::search)
                            path("{versionName}") {
                                before {
                                    dataModelService.validateEntityAndVersionExists(
                                        it.pathParam("entityName"),
                                        it.pathParam("versionName")
                                    )

                                }
                                get("schema", DataModelController::getSchema)
                                post("validate", DataModelController::validatePayload)
                                post(DataModelController::create)
                                path("{uniqueIdentifier}") {
                                    before {
                                        dataModelService.findByUniqueIdentifier(
                                            it.pathParam("entityName"),
                                            it.pathParam("uniqueIdentifier")
                                        )
                                            ?: throw NotFoundResponse("DataModel not found")
                                    }
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
