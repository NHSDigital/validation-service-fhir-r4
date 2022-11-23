package uk.nhs.nhsdigital.fhirvalidator.service

import uk.nhs.nhsdigital.fhirvalidator.util.applyProfile
import uk.nhs.nhsdigital.fhirvalidator.util.createOperationOutcome
import uk.nhs.nhsdigital.fhirvalidator.util.createOperationOutcomeIssue
import uk.nhs.nhsdigital.fhirvalidator.util.getResourcesOfType
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Service

@Service
class MessageDefinitionApplier(
    implementationGuideParser: ImplementationGuideParser,
    supportChain: ValidationSupportChain
) {
    val messageDefinitions = supportChain.fetchAllConformanceResources()?.filterIsInstance(MessageDefinition::class.java)
    /*
    val messageDefinitions = npmPackages.flatMap {
        implementationGuideParser.getResourcesOfTypeFromPackage(it, MessageDefinition::class.java)
    }*/

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
        val messageDefinitionProfile = messageHeader.definition
        val messageDefinition = findMessageDefinition(messageType, messageDefinitionProfile)
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

    private fun findMessageDefinition(messageType: Coding, messageDefinitionProfile: String?): MessageDefinition? {

        if (messageDefinitionProfile != null) {
            return messageDefinitions
                ?.filter { it.eventCoding.system == messageType.system }
                ?.firstOrNull { it.eventCoding.code == messageType.code &&  it.url == messageDefinitionProfile }
        } else {
            return messageDefinitions
                ?.filter { it.eventCoding.system == messageType.system }
                ?.firstOrNull { it.eventCoding.code == messageType.code }
        }
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
        val matchingResources = getResourcesOfType(bundle, focus.code)
        applyMessageDefinitionFocusProfile(focus, matchingResources)
        return applyMessageDefinitionFocusMinMax(focus, matchingResources.size)
    }

    private fun applyMessageDefinitionFocusProfile(
        focus: MessageDefinition.MessageDefinitionFocusComponent,
        matchingResources: List<IBaseResource>
    ) {
        if (focus.hasProfile()) {
            applyProfile(matchingResources, focus.profileElement)
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
}
