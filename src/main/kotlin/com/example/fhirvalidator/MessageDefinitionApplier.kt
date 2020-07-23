package com.example.fhirvalidator

import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.MessageDefinition
import org.hl7.fhir.utilities.cache.NpmPackage
import org.springframework.stereotype.Service

@Service
class MessageDefinitionApplier(implementationGuideParser: ImplementationGuideParser, npmPackages: Array<NpmPackage>) {
    val messageDefinitions = npmPackages.map { implementationGuideParser.getMessageDefinitions(it) }.flatten()

    fun applyMessageDefinition(resource: IBaseResource, messageDefinitionUrl: String) {
        //TODO - return an error if we can't find the specified message definition?
        val messageDefinition = messageDefinitions.firstOrNull { it.url == messageDefinitionUrl }
        //TODO - return an error if the input is not a bundle?
        if (resource is Bundle) {
            messageDefinition?.focus?.forEach { applyMessageDefinitionFocus(resource, it) }
        }
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