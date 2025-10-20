package my.baas.services

import my.baas.auth.CurrentUser
import my.baas.config.AppContext
import my.baas.models.AuditAction
import my.baas.models.AuditLog
import my.baas.models.DataModel
import org.slf4j.LoggerFactory

object AuditService {
    
    private val logger = LoggerFactory.getLogger(AuditService::class.java)
    
    fun logAction(
        action: AuditAction,
        entityName: String,
        uniqueIdentifier: String,
        oldData: Map<String, Any>? = null,
        newData: Map<String, Any>? = null,
        notes: String? = null
    ) {
        try {
            val currentUser = CurrentUser.get()
            val tenant = CurrentUser.getTenant()
            
            if (tenant == null) {
                logger.warn("Cannot log audit action - no tenant in context")
                return
            }
            
            val auditLog = AuditLog(
                entityName = entityName,
                uniqueIdentifier = uniqueIdentifier,
                action = action,
                userId = currentUser.userId,
                clientIp = currentUser.clientIp ?: "unknown",
                oldData = oldData,
                newData = newData,
                notes = notes
            )
            
            AppContext.db.save(auditLog)
            
            logger.debug("Audit log created: ${action.name} for $entityName/$uniqueIdentifier by ${currentUser.userId}")
            
        } catch (e: Exception) {
            logger.error("Failed to create audit log", e)
            // Don't throw - audit logging failures shouldn't break the main operation
        }
    }
    
    fun logDataModelAction(
        action: AuditAction,
        dataModel: DataModel,
        oldData: Map<String, Any>? = null,
        notes: String? = null
    ) {
        logAction(
            action = action,
            entityName = dataModel.entityName,
            uniqueIdentifier = dataModel.uniqueIdentifier,
            oldData = oldData,
            newData = dataModel.data,
            notes = notes
        )
    }
}