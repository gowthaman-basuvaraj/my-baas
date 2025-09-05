package my.baas.auth

import my.baas.models.TenantModel
enum class UserType {
    ADMIN, TENANT, ANONYMOUS;
}
data class UserContext(
    val userId: String,
    val tenant: TenantModel? = null,
    val clientIp: String? = null,
    val userType: UserType = UserType.ANONYMOUS,
    val applicationId: Long? = null,
    val applicationName: String? = null,
    val entityName: String? = null,
    val versionName: String? = null
)

object CurrentUser {

    private val userContext = object : ThreadLocal<UserContext>() {
        override fun initialValue(): UserContext {
            return UserContext("anonymous")
        }
    }

    fun get(): UserContext = userContext.get()

    fun set(userContext: UserContext) = CurrentUser.userContext.set(userContext)

    fun getTenant(): TenantModel? = userContext.get().tenant
    
    fun getApplicationId(): Long? = userContext.get().applicationId
    
    fun getApplicationName(): String? = userContext.get().applicationName
    
    fun getEntityName(): String? = userContext.get().entityName
    
    fun getVersionName(): String? = userContext.get().versionName

    fun setApplicationContext(applicationId: Long?, applicationName: String?) {
        val current = get()
        set(current.copy(applicationId = applicationId, applicationName = applicationName))
    }
    
    fun setEntityContext(entityName: String?, versionName: String?) {
        val current = get()
        set(current.copy(entityName = entityName, versionName = versionName))
    }

    fun clear() = userContext.remove()
}
