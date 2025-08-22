package my.baas.services.completion

import my.baas.models.ReportExecutionLog
import my.baas.models.ReportModel

/**
 * Interface for processing report completion actions
 */
interface CompletionActionProcessor<T : ReportModel.CompletionAction> {
    /**
     * Process the completion action
     * @param action The completion action to process
     * @param executionLog The report execution log
     */
    fun process(action: T, executionLog: ReportExecutionLog)
    
    /**
     * Get the action type this processor handles
     */
    fun getActionType(): ReportModel.ActionType
}