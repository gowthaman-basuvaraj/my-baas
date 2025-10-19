package my.baas.auth


import com.fasterxml.jackson.annotation.JsonProperty

data class OAuthWellKnown(
    @param:JsonProperty("authorization_endpoint")
    val authorizationEndpoint: String,
    @param:JsonProperty("issuer")
    val issuer: String,
    @param:JsonProperty("jwks_uri")
    val jwksUri: String,
    @param:JsonProperty("revocation_endpoint")
    val revocationEndpoint: String,
    @param:JsonProperty("token_endpoint")
    val tokenEndpoint: String,
    @param:JsonProperty("userinfo_endpoint")
    val userinfoEndpoint: String
)