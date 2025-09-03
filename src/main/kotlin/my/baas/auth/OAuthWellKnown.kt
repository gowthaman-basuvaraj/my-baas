package my.baas.auth


import com.fasterxml.jackson.annotation.JsonProperty

data class OAuthWellKnown(
    @JsonProperty("authorization_endpoint")
    val authorizationEndpoint: String,
    @JsonProperty("issuer")
    val issuer: String,
    @JsonProperty("jwks_uri")
    val jwksUri: String,
    @JsonProperty("revocation_endpoint")
    val revocationEndpoint: String,
    @JsonProperty("token_endpoint")
    val tokenEndpoint: String,
    @JsonProperty("userinfo_endpoint")
    val userinfoEndpoint: String
)