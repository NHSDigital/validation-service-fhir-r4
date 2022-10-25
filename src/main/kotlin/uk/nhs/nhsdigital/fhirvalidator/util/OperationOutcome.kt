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
    return createSTU3OperationOutcomeR3(issues)
}

fun createOperationOutcome(issues: List<OperationOutcome.OperationOutcomeIssueComponent>): OperationOutcome {
    val operationOutcome = OperationOutcome()
    issues.forEach { operationOutcome.addIssue(it) }
    return operationOutcome
}

fun createSTU3OperationOutcomeR3(issues: List<org.hl7.fhir.dstu3.model.OperationOutcome.OperationOutcomeIssueComponent>): org.hl7.fhir.dstu3.model.OperationOutcome {
    val operationOutcome = org.hl7.fhir.dstu3.model.OperationOutcome()
    issues.forEach { operationOutcome.addIssue(
       it
    ) }
    return operationOutcome
}

fun createSTU3OperationOutcomeR4(issues: List<OperationOutcome.OperationOutcomeIssueComponent>): org.hl7.fhir.dstu3.model.OperationOutcome {
    val operationOutcome = org.hl7.fhir.dstu3.model.OperationOutcome()
    issues.forEach { operationOutcome.addIssue(
        org.hl7.fhir.dstu3.model.OperationOutcome.OperationOutcomeIssueComponent()
            .setDiagnostics(it.diagnostics)
            .setCode(getCode(it.code))
            .setSeverity(getSeverity(it.severity))
    ) }
    return operationOutcome
}

fun getSeverity(severity: OperationOutcome.IssueSeverity?): org.hl7.fhir.dstu3.model.OperationOutcome.IssueSeverity? {
    when (severity) {
        OperationOutcome.IssueSeverity.ERROR -> org.hl7.fhir.dstu3.model.OperationOutcome.IssueSeverity.ERROR
        OperationOutcome.IssueSeverity.FATAL -> org.hl7.fhir.dstu3.model.OperationOutcome.IssueSeverity.FATAL
        OperationOutcome.IssueSeverity.INFORMATION -> org.hl7.fhir.dstu3.model.OperationOutcome.IssueSeverity.INFORMATION
        OperationOutcome.IssueSeverity.WARNING -> org.hl7.fhir.dstu3.model.OperationOutcome.IssueSeverity.WARNING
        else -> {}
    }
    return null
}

fun getCode(issue: OperationOutcome.IssueType) : org.hl7.fhir.dstu3.model.OperationOutcome.IssueType? {
    when (issue) {
        OperationOutcome.IssueType.INFORMATIONAL -> return  org.hl7.fhir.dstu3.model.OperationOutcome.IssueType.INFORMATIONAL
        OperationOutcome.IssueType.BUSINESSRULE -> return  org.hl7.fhir.dstu3.model.OperationOutcome.IssueType.BUSINESSRULE
        OperationOutcome.IssueType.CODEINVALID -> return  org.hl7.fhir.dstu3.model.OperationOutcome.IssueType.CODEINVALID
    }
    return null
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
