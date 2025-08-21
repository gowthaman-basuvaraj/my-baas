package my.baas.auth

import my.baas.models.TenantModel

data class User(
    val userId: String,
    val tenant: TenantModel? = null
)

object CurrentUser {

    private val user = object : ThreadLocal<User>() {
        override fun initialValue(): User {
            return User("anonymous", null)
        }
    }

    fun get(): User = user.get()

    fun set(user: User) = CurrentUser.user.set(user)

    fun getTenant(): TenantModel? = user.get().tenant


    fun clear() = user.remove()
}
