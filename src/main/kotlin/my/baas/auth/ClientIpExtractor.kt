package my.baas.auth

import io.javalin.http.Context

object ClientIpExtractor {
    
    fun extractClientIp(ctx: Context): String {
        // Check common proxy headers first
        val xForwardedFor = ctx.header("X-Forwarded-For")
        if (xForwardedFor != null && xForwardedFor.isNotBlank()) {
            // X-Forwarded-For can contain multiple IPs, the first one is the original client
            return xForwardedFor.split(",")[0].trim()
        }
        
        val xRealIp = ctx.header("X-Real-IP")
        if (xRealIp != null && xRealIp.isNotBlank()) {
            return xRealIp.trim()
        }
        
        val xClientIp = ctx.header("X-Client-IP")
        if (xClientIp != null && xClientIp.isNotBlank()) {
            return xClientIp.trim()
        }
        
        val xClusterClientIp = ctx.header("X-Cluster-Client-IP")
        if (xClusterClientIp != null && xClusterClientIp.isNotBlank()) {
            return xClusterClientIp.trim()
        }
        
        // Fall back
        return ctx.req().remoteAddr ?: ctx.ip()
    }
}