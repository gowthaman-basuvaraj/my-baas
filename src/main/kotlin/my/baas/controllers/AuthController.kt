package my.baas.controllers


import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.HttpStatus
import io.javalin.http.UnauthorizedResponse
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import my.baas.auth.JwtProvider
import my.baas.config.AppContext
import org.apache.hc.core5.net.URIBuilder

object AuthController {
    private val logger = LoggerFactory.getLogger(AuthController::class.java)
    private val appConfig = AppContext.appConfig
    private val httpClient = HttpClient.newBuilder().build()
    private val objectMapper = jacksonObjectMapper()
    val wellKnown = JwtProvider.fetchAuthEndpointWellKnown(appConfig.wellKnownUrl())

    /**
     * Get Keycloak initialization config
     * GET /auth/init
     */
    fun getAuthInit(ctx: Context) {
        try {

            ctx.status(HttpStatus.OK)
                .json(
                    mapOf(
                        "enabled" to true,
                        "authUrl" to wellKnown.authorizationEndpoint,
                        "baseUrl" to URIBuilder(wellKnown.authorizationEndpoint).run {
                            "${this.scheme}://${this.host}"
                        },
                        "realm" to appConfig.adminAuthClientRealm(),
                        "clientId" to appConfig.adminAuthClientId()
                    )
                )
        } catch (e: Exception) {
            logger.error("Error getting auth init config", e)
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result("Internal server error")
        }
    }

    /**
     * Exchange authorization code for token
     * POST /auth/token
     * Body: { "code": "auth-code", "redirectUri": "redirect-uri" }
     */
    fun exchangeToken(ctx: Context) {
        try {

            val body = ctx.bodyAsClass(TokenExchangeRequest::class.java)

            if (body.code.isBlank()) {
                throw BadRequestResponse("Authorization code is required")
            }

            // Build token endpoint URL
            val tokenEndpoint = wellKnown.tokenEndpoint

            // Prepare form data
            val formData = buildString {
                append("grant_type=").append(URLEncoder.encode("authorization_code", StandardCharsets.UTF_8))
                append("&code=").append(URLEncoder.encode(body.code, StandardCharsets.UTF_8))
                append("&client_id=").append(URLEncoder.encode(appConfig.adminAuthClientId(), StandardCharsets.UTF_8))
                append("&client_secret=").append(
                    URLEncoder.encode(
                        appConfig.adminAuthClientSecret(),
                        StandardCharsets.UTF_8
                    )
                )
                append("&redirect_uri=").append(URLEncoder.encode(body.redirectUri, StandardCharsets.UTF_8))
            }

            // Make request to Keycloak
            val request = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 200) {
                logger.error("Failed to exchange token. Status: ${response.statusCode()}, Body: ${response.body()}")
                throw UnauthorizedResponse("Failed to exchange authorization code")
            }

            // Parse token response
            val tokenResponse = objectMapper.readValue<Map<String, Any>>(response.body())

            ctx.status(HttpStatus.OK).json(
                mapOf(
                    "accessToken" to tokenResponse["access_token"],
                    "refreshToken" to tokenResponse["refresh_token"],
                    "expiresIn" to tokenResponse["expires_in"],
                    "tokenType" to tokenResponse["token_type"]
                )
            )

            logger.info("Successfully exchanged authorization code for token")
        } catch (e: BadRequestResponse) {
            throw e
        } catch (e: UnauthorizedResponse) {
            throw e
        } catch (e: Exception) {
            logger.error("Error exchanging token", e)
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result("Internal server error: ${e.message}")
        }
    }

    /**
     * Refresh access token
     * POST /auth/refresh
     * Body: { "refreshToken": "refresh-token" }
     */
    fun refreshToken(ctx: Context) {
        try {

            val body = ctx.bodyAsClass(RefreshTokenRequest::class.java)

            if (body.refreshToken.isBlank()) {
                throw BadRequestResponse("Refresh token is required")
            }

            // Build token endpoint URL
            val tokenEndpoint = wellKnown.tokenEndpoint

            // Prepare form data
            val formData = buildString {
                append("grant_type=").append(URLEncoder.encode("refresh_token", StandardCharsets.UTF_8))
                append("&refresh_token=").append(URLEncoder.encode(body.refreshToken, StandardCharsets.UTF_8))
                append("&client_id=").append(URLEncoder.encode(appConfig.adminAuthClientId(), StandardCharsets.UTF_8))
                append("&client_secret=").append(
                    URLEncoder.encode(
                        appConfig.adminAuthClientSecret(),
                        StandardCharsets.UTF_8
                    )
                )
            }

            // Make request to Keycloak
            val request = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 200) {
                logger.error("Failed to refresh token. Status: ${response.statusCode()}, Body: ${response.body()}")
                throw UnauthorizedResponse("Failed to refresh token")
            }

            // Parse token response
            val tokenResponse = objectMapper.readValue<Map<String, Any>>(response.body())

            ctx.status(HttpStatus.OK).json(
                mapOf(
                    "accessToken" to tokenResponse["access_token"],
                    "refreshToken" to tokenResponse["refresh_token"],
                    "expiresIn" to tokenResponse["expires_in"],
                    "tokenType" to tokenResponse["token_type"]
                )
            )

            logger.info("Successfully refreshed access token")
        } catch (e: BadRequestResponse) {
            throw e
        } catch (e: UnauthorizedResponse) {
            throw e
        } catch (e: Exception) {
            logger.error("Error refreshing token", e)
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result("Internal server error: ${e.message}")
        }
    }

    /**
     * Logout
     * POST /auth/logout
     * Body: { "refreshToken": "refresh-token" }
     */
    fun logout(ctx: Context) {
        try {

            val body = ctx.bodyAsClass(LogoutRequest::class.java)

            if (body.refreshToken.isBlank()) {
                ctx.status(HttpStatus.OK).json(mapOf("message" to "Logged out"))
                return
            }

            // Build logout endpoint URL
            val logoutEndpoint = wellKnown.endSessionEndpoint

            // Prepare form data
            val formData = buildString {
                append("client_id=").append(URLEncoder.encode(appConfig.adminAuthClientId(), StandardCharsets.UTF_8))
                append("&client_secret=").append(
                    URLEncoder.encode(
                        appConfig.adminAuthClientSecret(),
                        StandardCharsets.UTF_8
                    )
                )
                append("&refresh_token=").append(URLEncoder.encode(body.refreshToken, StandardCharsets.UTF_8))
            }

            // Make request to Keycloak
            val request = HttpRequest.newBuilder()
                .uri(URI.create(logoutEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build()

            httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            ctx.status(HttpStatus.OK).json(mapOf("message" to "Logged out successfully"))

            logger.info("Successfully logged out")
        } catch (e: Exception) {
            logger.error("Error logging out", e)
            ctx.status(HttpStatus.OK).json(mapOf("message" to "Logged out"))
        }
    }
}

data class TokenExchangeRequest(
    val code: String,
    val redirectUri: String
)

data class RefreshTokenRequest(
    val refreshToken: String
)

data class LogoutRequest(
    val refreshToken: String = ""
)
