package my.baas

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.http.staticfiles.Location
import io.javalin.json.JavalinJackson
import my.baas.auth.AuthHandler
import my.baas.auth.CurrentUser
import my.baas.config.AppContext
import my.baas.controllers.*
import my.baas.models.ApplicationModel
import my.baas.models.TenantModel
import my.baas.services.DataModelService
import my.baas.services.RedisEventPublisher
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("my.baas.Application")
private val dataModelService = DataModelService()

fun main() {
    logger.info("Starting MyBaaS application...")

    logger.info("Database connection initialized, Total Tenants = ${AppContext.db.find(TenantModel::class.java).findCount()}")

    // Initialize Redis event publisher if enabled
    RedisEventPublisher.initialize()


    Javalin
        .create { config ->
            config.jsonMapper(JavalinJackson(AppContext.objectMapper))
            config.bundledPlugins.enableCors { cors -> cors.addRule { it.anyHost() } }
            config.staticFiles.enableWebjars()
            if (AppContext.appConfig.isDev()) {
                val currentDirectory = System.getProperty("user.dir")
                config.staticFiles.add("${currentDirectory}/src/main/resources/public", Location.EXTERNAL)
                logger.info("Running in dev mode, static files will be served from: ${currentDirectory}/src/main/resources/public")
            } else {
                config.staticFiles.add("/public", Location.CLASSPATH)
                logger.info("Running in production mode, static files will be served from: /public")
            }
            config.router.apiBuilder {
                // Auth routes (public)
                path("auth"){
                    get("init", AuthController::getAuthInit)
                    post("token", AuthController::exchangeToken)
                    post("refresh", AuthController::refreshToken)
                    post("logout", AuthController::logout)
                }
                // Admin APIs - for managing tenants
                path("admin") {
                    before(AuthHandler.handleAdmin)
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
                    // Application management
                    crud("applications/{id}", ApplicationController)
                    // Application-scoped schemas
                    path("applications/{applicationName}") {
                        before { ctx ->
                            // Set application context from path
                            val applicationName = ctx.pathParam("applicationName")
                            val tenantId = CurrentUser.getTenant()?.id
                            if (tenantId != null) {
                                val application = AppContext.db.find(ApplicationModel::class.java)
                                    .where()
                                    .eq("applicationName", applicationName)
                                    .eq("tenantId", tenantId)
                                    .findOne()
                                if (application != null) {
                                    CurrentUser.setApplicationContext(application.id, application.applicationName)
                                }
                            }
                        }
                        crud("schemas/{id}", SchemaController)
                        // Application-scoped data endpoints
                        path("data/{entityName}") {
                            before { ctx ->
                                // Set entity context
                                val entityName = ctx.pathParam("entityName")
                                CurrentUser.setEntityContext(entityName, null)
                                dataModelService.validateSchemaExistsForEntity(entityName)
                            }
                            post("search", DataModelController::search)
                            get(DataModelController::getAll)
                            path("{versionName}") {
                                before { ctx ->
                                    val versionName = ctx.pathParam("versionName")
                                    val entityName = ctx.pathParam("entityName")
                                    CurrentUser.setEntityContext(entityName, versionName)
                                    if(!ctx.path().endsWith("/search")) {
                                        dataModelService.validateSchemaExistsForEntityAndVersion(
                                            entityName,
                                            versionName
                                        )
                                    }
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
                    after { CurrentUser.clear() }
                }

                // WebSocket endpoint for real-time events
                ws("/ws/events", WebSocketHandler::configure)
            }
            config.router.treatMultipleSlashesAsSingleSlash = true
            config.router.ignoreTrailingSlashes = false
            config.bundledPlugins.enableRouteOverview("/all-routes")
        }
        .start(7070)

    logger.info("MyBaaS application started successfully on port 7070")
}
