package my.baas.auth

import my.baas.config.AppContext
import net.jodah.expiringmap.ExpiringMap
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object JwtProvider {

    private fun <T> makeCache(name: String): MutableMap<String, T> {
        return ExpiringMap
            .builder()
            .expiration(1, TimeUnit.HOURS)
            .expirationListener { p0: String, _: Any ->
                logger.debug("Cache for $name, Key $p0 is expired")
            }
            .build()
    }

    private val logger = LoggerFactory.getLogger(JwtProvider::class.java)
    private val wellKnownCache: MutableMap<String, OAuthWellKnown> = makeCache("wellKnownCache")
    private val jwksCache: MutableMap<String, List<JsonWebKey>> = makeCache("jwksCache")

    fun verify(token: String, wellKnownUrl: String): JwtClaims {
        logger.debug("Verifying JWT token: $token, with wellKnownUrl: $wellKnownUrl")
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

    private fun fetchJwksKeys(jwksUri: String): List<JsonWebKey> {
        return jwksCache.computeIfAbsent(jwksUri) {
            JsonWebKeySet(
                HttpClients.createDefault().execute(
                    HttpGet(jwksUri),
                    BasicHttpClientResponseHandler()
                )
            ).jsonWebKeys
        }
    }

    fun fetchAuthEndpointWellKnown(wellKnownUrl: String): OAuthWellKnown {

        return wellKnownCache.computeIfAbsent(wellKnownUrl) {
            AppContext.objectMapper.readValue(
                HttpClients.createDefault().execute(
                    HttpGet(wellKnownUrl),
                    BasicHttpClientResponseHandler()
                ),
                OAuthWellKnown::class.java
            )
        }

    }

}
