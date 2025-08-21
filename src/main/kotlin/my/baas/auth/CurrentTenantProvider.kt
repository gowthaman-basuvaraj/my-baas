package my.baas.auth

import io.ebean.config.CurrentTenantProvider

class CurrentTenantProvider : CurrentTenantProvider {
    
    override fun currentId(): Any {
        return CurrentUser.getTenant()?.id ?: throw IllegalStateException("No tenant in context")
    }
}