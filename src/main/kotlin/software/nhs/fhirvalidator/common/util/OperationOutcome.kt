package software.nhs.fhirvalidator.common.util

import org.hl7.fhir.r4.model.OperationOutcome
import java.util.*

fun createOperationOutcome(diagnostics: String, expression: String?): OperationOutcome {
    val issue = createOperationOutcomeIssue(diagnostics, expression)
    val issues = Collections.singletonList(issue)
    return createOperationOutcome(issues)
}

fun createOperationOutcome(issues: List<OperationOutcome.OperationOutcomeIssueComponent>): OperationOutcome {
    val operationOutcome = OperationOutcome()
    issues.forEach { operationOutcome.addIssue(it) }
    return operationOutcome
}

fun createOperationOutcomeIssue(diagnostics: String, expression: String?): OperationOutcome.OperationOutcomeIssueComponent {
    val issue = OperationOutcome.OperationOutcomeIssueComponent()
    issue.severity = OperationOutcome.IssueSeverity.ERROR
    issue.code = OperationOutcome.IssueType.PROCESSING
    issue.diagnostics = diagnostics
    expression?.let { issue.addExpression(it) }
    return issue
}
