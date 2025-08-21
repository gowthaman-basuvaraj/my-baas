package my.baas.auth

import io.ebean.DB
import io.javalin.http.BadRequestResponse
import io.javalin.http.Handler
import io.javalin.http.UnauthorizedResponse
import my.baas.models.TenantModel
import org.slf4j.LoggerFactory

object AuthHandler {

    private val logger = LoggerFactory.getLogger(AuthHandler::class.java)
    fun setCurrentTenant(domain: String) {

        // Find a tenant by domain
        val tenant = DB.find(TenantModel::class.java)
            .where()
            .eq("domain", domain)
            .eq("isActive", true)
            .findOne()

        CurrentUser.set(User("", tenant))
    }

    val handle: Handler = Handler { ctx ->
        val token = ctx.header("Authorization")?.removePrefix("Bearer ")
            ?: throw UnauthorizedResponse("Authorization header is missing")
        try {
            val jwtClaims = JwtProvider.verify(token)
            val userId = jwtClaims.claimsMap["preferred_username"] as String

            // Extract tenant from the host header or domain
            val domain = ctx.header("Host") ?: ctx.host() ?: throw BadRequestResponse("unable to find Host")

            // Find a tenant by domain
            val tenant = DB.find(TenantModel::class.java)
                .where()
                .eq("domain", domain)
                .eq("isActive", true)
                .findOne()

            if (tenant == null) {
                logger.warn("No active tenant found for domain: $domain")
                throw UnauthorizedResponse("Invalid tenant domain")
            }

            // Set user with tenant
            CurrentUser.set(User(userId, tenant))
            logger.debug("Set tenant: ${tenant.name} for user: $userId")

        } catch (e: Exception) {
            logger.error("exception in parsing JWT or setting tenant", e)
            throw UnauthorizedResponse("Invalid JWT or tenant")
        }
    }
}
