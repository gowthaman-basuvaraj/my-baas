package my.baas.auth

import my.baas.config.AppContext
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.jose4j.jwk.JsonWebKey
import org.jose4j.jwk.JsonWebKeySet
import org.jose4j.jwt.JwtClaims
import org.jose4j.jwt.consumer.JwtConsumer
import org.jose4j.jwt.consumer.JwtConsumerBuilder
import org.jose4j.keys.resolvers.JwksVerificationKeyResolver
import org.slf4j.LoggerFactory

object JwtProvider {

    private val logger = LoggerFactory.getLogger(JwtProvider::class.java)

    fun verify(token: String, wellKnownUrl: String): JwtClaims {
        logger.info("Verifying JWT token: $token, with wellKnownUrl: $wellKnownUrl")
        val jwksUri = fetchAuthEndpointWellKnown(wellKnownUrl)

        // Create a JWKS resolver
        val jwksResolver = JwksVerificationKeyResolver(
            fetchJwksKeys(jwksUri.jwksUri)
        )

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

    private fun fetchJwksKeys(jwksUri: String): MutableList<JsonWebKey> {
        return JsonWebKeySet(
            HttpClients.createDefault().execute(
                HttpGet(jwksUri),
                BasicHttpClientResponseHandler()
            )
        ).jsonWebKeys
    }

    private fun fetchAuthEndpointWellKnown(wellKnownUrl: String): OAuthWellKnown {

        return AppContext.objectMapper.readValue(
            HttpClients.createDefault().execute(
                HttpGet(wellKnownUrl),
                BasicHttpClientResponseHandler()
            ),
            OAuthWellKnown::class.java
        )

    }

}
