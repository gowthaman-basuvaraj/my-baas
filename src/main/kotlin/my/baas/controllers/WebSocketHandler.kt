package my.baas.controllers

import io.javalin.websocket.WsConfig
import io.javalin.websocket.WsContext
import my.baas.auth.AuthHandler
import my.baas.auth.CurrentUser
import my.baas.services.WebSocketEventManager
import org.slf4j.LoggerFactory

object WebSocketHandler {

    private val logger = LoggerFactory.getLogger(WebSocketHandler::class.java)
    private val eventManager = WebSocketEventManager

    private fun setupCurrentTenant(ctx: WsContext) {
        // Extract tenant from the host header or domain
        val domain = ctx.header("Host") ?: ctx.host()
        //todo: authentication
        AuthHandler.setCurrentTenant(domain)
    }

    fun configure(ws: WsConfig) {
        ws.onConnect { ctx ->
            setupCurrentTenant(ctx)
            // Extract tenant from the host header or query parameter
            val tenantId = CurrentUser.getTenant()?.id ?: return@onConnect
            eventManager.handleConnection(ctx, tenantId)
        }

        ws.onMessage { ctx ->
            setupCurrentTenant(ctx)

            val tenantId = CurrentUser.getTenant()?.id ?: return@onMessage
            eventManager.handleMessage(ctx, ctx.message(), tenantId)
        }

        ws.onClose { ctx ->
            setupCurrentTenant(ctx)

            val tenantId = CurrentUser.getTenant()?.id ?: return@onClose
            eventManager.handleDisconnection(ctx, tenantId)
        }

        ws.onError { ctx ->
            setupCurrentTenant(ctx)

            val tenantId = CurrentUser.getTenant()?.id ?: return@onError
            eventManager.handleDisconnection(ctx, tenantId)
        }
    }

}