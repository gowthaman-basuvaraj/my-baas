package my.baas.auth

import io.ebean.config.CurrentUserProvider

class CurrentUserProvider : CurrentUserProvider {

    override fun currentUser(): Any {
        return CurrentUser.get().userId
    }
}
