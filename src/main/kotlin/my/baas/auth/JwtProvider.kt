package my.baas.auth

import my.baas.config.AppContext
import org.jose4j.jwk.HttpsJwks
import org.jose4j.jwt.JwtClaims
import org.jose4j.jwt.consumer.JwtConsumer
import org.jose4j.jwt.consumer.JwtConsumerBuilder
import org.jose4j.keys.resolvers.HttpsJwksVerificationKeyResolver
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

object JwtProvider {

    private val httpClient = HttpClient.newHttpClient()

    fun verify(token: String): JwtClaims {
        val wellKnownUrl = AppContext.appConfig.wellKnownUrl()

        // Fetch JWKS URI from well-known configuration
        val jwksUri = fetchAuthEndpointWellKnown(wellKnownUrl)

        // Create JWKS resolver
        val jwksResolver = HttpsJwksVerificationKeyResolver(HttpsJwks(jwksUri.jwksUri))

        // Build JWT consumer
        val jwtConsumer: JwtConsumer = JwtConsumerBuilder()
            .setRequireExpirationTime()
            .setAllowedClockSkewInSeconds(30)
            .setRequireSubject()
            .setExpectedIssuer(jwksUri.issuer)
            .setVerificationKeyResolver(jwksResolver)
            .build()

        // Verify and return claims
        return jwtConsumer.processToClaims(token)
    }

    private fun fetchAuthEndpointWellKnown(wellKnownUrl: String): OAuthWellKnown {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(wellKnownUrl))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            throw RuntimeException("Failed to fetch well-known configuration: ${response.statusCode()}")
        }

        return AppContext.objectMapper.readValue(response.body(), OAuthWellKnown::class.java)
    }
}
