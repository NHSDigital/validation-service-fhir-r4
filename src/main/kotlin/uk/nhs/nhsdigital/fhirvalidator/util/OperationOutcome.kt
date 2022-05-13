package uk.nhs.nhsdigital.fhirvalidator.util

import org.hl7.fhir.r4.model.OperationOutcome
import java.util.*

fun createOperationOutcome(diagnostics: String, expression: String?): OperationOutcome {
    val issue = createOperationOutcomeIssue(diagnostics, expression)
    val issues = Collections.singletonList(issue)
    return createOperationOutcome(issues)
}
fun createSTU3OperationOutcome(diagnostics: String, expression: String?): org.hl7.fhir.dstu3.model.OperationOutcome {
    val issue = createSTU3OperationOutcomeIssue(diagnostics, expression)
    val issues = Collections.singletonList(issue)
    return createSTU3OperationOutcome(issues)
}

fun createOperationOutcome(issues: List<OperationOutcome.OperationOutcomeIssueComponent>): OperationOutcome {
    val operationOutcome = OperationOutcome()
    issues.forEach { operationOutcome.addIssue(it) }
    return operationOutcome
}

fun createSTU3OperationOutcome(issues: List<org.hl7.fhir.dstu3.model.OperationOutcome.OperationOutcomeIssueComponent>): org.hl7.fhir.dstu3.model.OperationOutcome {
    val operationOutcome = org.hl7.fhir.dstu3.model.OperationOutcome()
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

fun createSTU3OperationOutcomeIssue(diagnostics: String, expression: String?): org.hl7.fhir.dstu3.model.OperationOutcome.OperationOutcomeIssueComponent {
    val issue = org.hl7.fhir.dstu3.model.OperationOutcome.OperationOutcomeIssueComponent()
    issue.severity = org.hl7.fhir.dstu3.model.OperationOutcome.IssueSeverity.ERROR
    issue.code = org.hl7.fhir.dstu3.model.OperationOutcome.IssueType.PROCESSING
    issue.diagnostics = diagnostics
    expression?.let { issue.addExpression(it) }
    return issue
}
