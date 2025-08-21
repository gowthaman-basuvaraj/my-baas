package my.baas.auth


import com.fasterxml.jackson.annotation.JsonProperty

data class OAuthWellKnown(
    @JsonProperty("authorization_endpoint")
    val authorizationEndpoint: String,
    @JsonProperty("claims_supported")
    val claimsSupported: List<String>,
    @JsonProperty("code_challenge_methods_supported")
    val codeChallengeMethodsSupported: List<String>,
    @JsonProperty("device_authorization_endpoint")
    val deviceAuthorizationEndpoint: String,
    @JsonProperty("grant_types_supported")
    val grantTypesSupported: List<String>,
    @JsonProperty("id_token_signing_alg_values_supported")
    val idTokenSigningAlgValuesSupported: List<String>,
    @JsonProperty("issuer")
    val issuer: String,
    @JsonProperty("jwks_uri")
    val jwksUri: String,
    @JsonProperty("response_types_supported")
    val responseTypesSupported: List<String>,
    @JsonProperty("revocation_endpoint")
    val revocationEndpoint: String,
    @JsonProperty("scopes_supported")
    val scopesSupported: List<String>,
    @JsonProperty("subject_types_supported")
    val subjectTypesSupported: List<String>,
    @JsonProperty("token_endpoint")
    val tokenEndpoint: String,
    @JsonProperty("token_endpoint_auth_methods_supported")
    val tokenEndpointAuthMethodsSupported: List<String>,
    @JsonProperty("userinfo_endpoint")
    val userinfoEndpoint: String
)