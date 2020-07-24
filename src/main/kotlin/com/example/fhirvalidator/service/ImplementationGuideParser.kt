package com.example.fhirvalidator.service

import ca.uhn.fhir.context.FhirContext
import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.CodeSystem
import org.hl7.fhir.r4.model.MessageDefinition
import org.hl7.fhir.r4.model.StructureDefinition
import org.hl7.fhir.r4.model.ValueSet
import org.hl7.fhir.utilities.cache.NpmPackage
import org.springframework.stereotype.Service

@Service
class ImplementationGuideParser(private val fhirContext: FhirContext) {
    fun createPrePopulatedValidationSupport(npmPackage: NpmPackage): PrePopulatedValidationSupport {
        val prePopulatedSupport = PrePopulatedValidationSupport(fhirContext)

        getResourcesOfType(npmPackage, StructureDefinition::class.java).forEach(prePopulatedSupport::addStructureDefinition)
        getResourcesOfType(npmPackage, CodeSystem::class.java).forEach(prePopulatedSupport::addCodeSystem)
        getResourcesOfType(npmPackage, ValueSet::class.java).forEach(prePopulatedSupport::addValueSet)

        return prePopulatedSupport
    }

    //TODO - can't use listResources or loadExampleResource as for some reason the message definitions are in an "examples" folder. Is this correct?
    // fun getMessageDefinitions(npmPackage: NpmPackage, id: String): List<MessageDefinition> {
    //     return getResourcesOfType(npmPackage, MessageDefinition::class.java)
    // }
    fun getMessageDefinitions(npmPackage: NpmPackage): List<MessageDefinition> {
        val jsonParser = fhirContext.newJsonParser()
        return npmPackage.list("examples")
                .map { npmPackage.load("examples", it) }
                .map(jsonParser::parseResource)
                .filterIsInstance(MessageDefinition::class.java)
    }

    private fun <T : IBaseResource> getResourcesOfType(npmPackage: NpmPackage, resourceType: Class<T>): List<T> {
        val jsonParser = fhirContext.newJsonParser()
        //TODO - what is the "correct" way to get the resource type? Class name doesn't feel right given the existence of a ResourceDef annotation
        return npmPackage.listResources(resourceType.simpleName)
                .map(npmPackage::loadResource)
                .map(jsonParser::parseResource)
                .filterIsInstance(resourceType)
    }
}