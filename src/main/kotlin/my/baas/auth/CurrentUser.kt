package my.baas.auth

import my.baas.models.TenantModel

data class UserContext(
    val userId: String,
    val tenant: TenantModel? = null,
    val clientIp: String? = null
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


    fun clear() = userContext.remove()
}
