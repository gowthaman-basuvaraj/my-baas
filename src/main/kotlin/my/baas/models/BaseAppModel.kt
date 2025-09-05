package my.baas.models

import io.ebean.annotation.Index
import jakarta.persistence.ManyToOne
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PrePersist
import my.baas.auth.CurrentUser
import my.baas.config.AppContext

@MappedSuperclass
abstract class BaseAppModel : BaseTenantModel() {
    
    @ManyToOne
    @Index
    lateinit var application: ApplicationModel
    
    @PrePersist
    fun setApplicationFromContext() {
        application = CurrentUser.get().applicationId?.let { appId ->
            AppContext.db.find(ApplicationModel::class.java, appId)
        } ?: throw IllegalStateException("No application found in context")
    }
}