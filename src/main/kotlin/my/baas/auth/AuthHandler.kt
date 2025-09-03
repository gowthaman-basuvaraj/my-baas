package my.baas.auth

import inet.ipaddr.IPAddressString
import io.javalin.http.Handler
import io.javalin.http.UnauthorizedResponse
import my.baas.config.AppContext
import my.baas.models.TenantModel
import org.slf4j.LoggerFactory

object AuthHandler {

    private val logger = LoggerFactory.getLogger(AuthHandler::class.java)
    fun setCurrentTenant(domain: String) {

        // Find a tenant by domain
        val tenant = AppContext.db.find(TenantModel::class.java)
            .where()
            .eq("domain", domain)
            .eq("isActive", true)
            .findOne()

        CurrentUser.set(UserContext("", tenant))
    }

    val handle: Handler = Handler { ctx ->
        val token = ctx.header("Authorization")?.removePrefix("Bearer ")
            ?: throw UnauthorizedResponse("Authorization header is missing")
        try {

            // Extract client IP
            val clientIp = ClientIpExtractor.extractClientIp(ctx)

            // Extract tenant from the host header or domain
            val domain = ctx.header("Host") ?: ctx.host() ?: throw UnauthorizedResponse("unable to find Host")

            // Find a tenant by domain
            val tenant = AppContext.db.find(TenantModel::class.java)
                .where()
                .eq("domain", domain)
                .eq("isActive", true)
                .findOne()

            if (tenant == null) {
                logger.warn("No active tenant found for domain: $domain")
                throw UnauthorizedResponse("Invalid tenant domain")
            }

            // Check if client IP is in allowed IPs list (if configured)
            tenant.allowedIps?.let { allowedIps ->

                if (allowedIps.isNotEmpty() && !allowedIps.any { ip ->
                        IPAddressString(ip).contains(
                            IPAddressString(
                                clientIp
                            )
                        )
                    }) {
                    logger.warn("Client IP $clientIp not in allowed IPs for tenant: ${tenant.name}, Allowed [$allowedIps]")
                    throw UnauthorizedResponse("Client IP [$clientIp] not authorized")
                }
            }

            val jwtClaims =
                JwtProvider.verify(token, tenant.config.jwksUri ?: throw UnauthorizedResponse("No JWKS URI found"))
            val userId = jwtClaims.claimsMap["preferred_username"] as String

            // Set user with tenant and client IP
            CurrentUser.set(UserContext(userId, tenant, clientIp))
            logger.debug("Set tenant: ${tenant.name} for user: $userId from IP: $clientIp")

        } catch (e: UnauthorizedResponse) {
            logger.error("exception in parsing JWT or setting tenant", e)
            throw e
        } catch (e: Exception) {
            logger.error("exception in parsing JWT or setting tenant", e)
            throw UnauthorizedResponse("Invalid JWT or tenant")
        }
    }
    val handleAdmin: Handler = Handler { ctx ->
        val token = ctx.header("Authorization")?.removePrefix("Bearer ")
            ?: throw UnauthorizedResponse("Authorization header is missing")
        try {
            val jwtClaims =
                JwtProvider.verify(token, AppContext.appConfig.wellKnownUrl())
            val userId = jwtClaims.claimsMap["preferred_username"] as String

            val clientIp = ClientIpExtractor.extractClientIp(ctx)

            // Set user with tenant and client IP
            CurrentUser.set(UserContext(userId, null, clientIp, UserType.ADMIN))
            logger.debug("Set Admin for user: $userId from IP: $clientIp")

        } catch (e: Exception) {
            logger.error("exception in parsing JWT or setting tenant", e)
            throw UnauthorizedResponse("Invalid JWT or tenant")
        }
    }
}
