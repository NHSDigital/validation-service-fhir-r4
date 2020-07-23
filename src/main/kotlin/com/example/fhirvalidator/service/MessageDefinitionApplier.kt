package com.example.fhirvalidator.service

import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.MessageDefinition
import org.hl7.fhir.r4.model.MessageHeader
import org.hl7.fhir.utilities.cache.NpmPackage
import org.springframework.stereotype.Service

@Service
class MessageDefinitionApplier(implementationGuideParser: ImplementationGuideParser, npmPackages: Array<NpmPackage>) {
    val messageDefinitions = npmPackages.map { implementationGuideParser.getMessageDefinitions(it) }.flatten()

    fun applyMessageDefinition(resource: IBaseResource) {
        if (resource is Bundle) {
            val messageHeader = resource.entry.map { it.resource }.filterIsInstance(MessageHeader::class.java).firstOrNull()
            val messageDefinition = messageHeader?.let { findMessageDefinition(it) }
            messageDefinition?.let { applyMessageDefinition(resource, it) }
        }
    }

    private fun findMessageDefinition(messageHeader: MessageHeader): MessageDefinition? {
        //TODO - return error if we can't find the message definition?
        return messageDefinitions.filter { it.eventCoding.system == messageHeader.eventCoding.system }
                .firstOrNull { it.eventCoding.code == messageHeader.eventCoding.code }
    }

    private fun applyMessageDefinition(resource: Bundle, messageDefinition: MessageDefinition) {
        messageDefinition.focus.forEach { applyMessageDefinitionFocus(resource, it) }
    }

    private fun applyMessageDefinitionFocus(bundle: Bundle, focus: MessageDefinition.MessageDefinitionFocusComponent) {
        if (focus.hasProfile()) {
            val resourceType = focus.code
            val profile = focus.profileElement
            bundle.entry
                    .map(Bundle.BundleEntryComponent::getResource)
                    //TODO - is there a better way to select resources of a given type?
                    .filter { it.resourceType.toString() == resourceType }
                    .map { it.meta.profile }
                    .filter { !it.contains(profile) }
                    .forEach { it.add(profile) }
        }
    }
}