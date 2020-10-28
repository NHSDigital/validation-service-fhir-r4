package com.example.fhirvalidator.service

import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.utilities.cache.NpmPackage
import org.springframework.stereotype.Service
import java.util.*
import kotlin.streams.toList

@Service
class MessageDefinitionApplier(
        implementationGuideParser: ImplementationGuideParser,
        npmPackages: List<NpmPackage>
) {
    val messageDefinitions = npmPackages.map(implementationGuideParser::getMessageDefinitions).flatten()

    fun applyMessageDefinition(resource: IBaseResource): OperationOutcome? {
        if (resource !is Bundle || resource.type != Bundle.BundleType.MESSAGE) {
            return null
        }

        val messageHeader = findMessageHeader(resource)
                ?: return createOperationOutcome(
                        "No MessageHeader found.",
                        "Bundle.entry"
                )

        val messageType = messageHeader.eventCoding
        val messageDefinition = findMessageDefinition(messageType)
                ?: return createOperationOutcome(
                        "Unsupported message type ${messageType.system}#${messageType.code}.",
                        "MessageHeader.eventCoding"
                )

        return applyMessageDefinition(resource, messageDefinition)
    }

    private fun findMessageHeader(bundle: Bundle): MessageHeader? {
        return bundle.entry
                ?.map { it.resource }
                ?.filterIsInstance(MessageHeader::class.java)
                ?.singleOrNull()
    }

    private fun findMessageDefinition(messageType: Coding): MessageDefinition? {
        return messageDefinitions
                .filter { it.eventCoding.system == messageType.system }
                .firstOrNull { it.eventCoding.code == messageType.code }
    }

    private fun applyMessageDefinition(
            resource: Bundle,
            messageDefinition: MessageDefinition
    ): OperationOutcome? {
        val issues = messageDefinition.focus.mapNotNull { applyMessageDefinitionFocus(resource, it) }
        if (issues.isEmpty()) {
            return null
        }
        return createOperationOutcome(issues)
    }

    private fun applyMessageDefinitionFocus(
            bundle: Bundle,
            focus: MessageDefinition.MessageDefinitionFocusComponent
    ): OperationOutcome.OperationOutcomeIssueComponent? {
        val resourceType = focus.code
        val matchingResources = bundle.entry.stream()
                .map { it.resource }
                .filter { it.fhirType() == resourceType }
                .toList()
        applyMessageDefinitionFocusProfile(focus, matchingResources)
        return applyMessageDefinitionFocusMinMax(focus, matchingResources.size)
    }

    private fun applyMessageDefinitionFocusProfile(focus: MessageDefinition.MessageDefinitionFocusComponent, matchingResources: List<Resource>) {
        if (focus.hasProfile()) {
            val profile = focus.profileElement
            matchingResources.stream()
                    .map { it.meta.profile }
                    .filter { !it.contains(profile) }
                    .forEach { it.add(profile) }
        }
    }

    private fun applyMessageDefinitionFocusMinMax(
            focus: MessageDefinition.MessageDefinitionFocusComponent,
            resourceCount: Int
    ): OperationOutcome.OperationOutcomeIssueComponent? {
        val resourceType = focus.code

        if (focus.hasMin()) {
            val min = focus.min
            if (resourceCount < min) {
                return createOperationOutcomeIssue(
                        "Bundle contains too few resources of type $resourceType. Expected at least $min.",
                        "Bundle.entry"
                )
            }
        }

        if (focus.hasMax()) {
            val maxStr = focus.max
            if (maxStr != "*") {
                val max = Integer.parseInt(maxStr)
                if (resourceCount > max) {
                    return createOperationOutcomeIssue(
                            "Bundle contains too many resources of type $resourceType. Expected at most $max.",
                            "Bundle.entry"
                    )
                }
            }
        }

        return null
    }

    private fun createOperationOutcome(diagnostics: String, expression: String): OperationOutcome {
        val issue = createOperationOutcomeIssue(diagnostics, expression)
        val issues = Collections.singletonList(issue)
        return createOperationOutcome(issues)
    }

    private fun createOperationOutcome(issues: List<OperationOutcome.OperationOutcomeIssueComponent>): OperationOutcome {
        val operationOutcome = OperationOutcome()
        issues.forEach { operationOutcome.addIssue(it) }
        return operationOutcome
    }

    private fun createOperationOutcomeIssue(diagnostics: String, expression: String): OperationOutcome.OperationOutcomeIssueComponent {
        val issue = OperationOutcome.OperationOutcomeIssueComponent()
        issue.severity = OperationOutcome.IssueSeverity.ERROR
        issue.code = OperationOutcome.IssueType.PROCESSING
        issue.diagnostics = diagnostics
        issue.addExpression(expression)
        return issue
    }
}