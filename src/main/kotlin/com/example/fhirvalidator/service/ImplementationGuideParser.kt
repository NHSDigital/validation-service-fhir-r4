package com.example.fhirvalidator.service

import ca.uhn.fhir.context.FhirContext
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.stereotype.Service

@Service
class ImplementationGuideParser(private val fhirContext: FhirContext) {
    fun createPrePopulatedValidationSupport(npmPackage: NpmPackage): PrePopulatedValidationSupport {
        val prePopulatedSupport = PrePopulatedValidationSupport(fhirContext)

        getResourcesOfType(npmPackage, StructureDefinition()).forEach(prePopulatedSupport::addStructureDefinition)
        getResourcesOfType(npmPackage, CodeSystem()).forEach(prePopulatedSupport::addCodeSystem)
        getResourcesOfType(npmPackage, ValueSet()).forEach(prePopulatedSupport::addValueSet)
        getResourcesOfOtherType(npmPackage, Questionnaire()).forEach(prePopulatedSupport::addResource)
        getResourcesOfOtherType(npmPackage, MessageDefinition()).forEach(prePopulatedSupport::addResource)
        return prePopulatedSupport
    }

    //TODO - can't use listResources or loadExampleResource as for some reason the message definitions are in an "examples" folder. Is this correct?
    // fun getMessageDefinitions(npmPackage: NpmPackage): List<MessageDefinition> {
    //     return getResourcesOfType(npmPackage, MessageDefinition())
    // }
    fun getMessageDefinitions(npmPackage: NpmPackage): List<MessageDefinition> {
        val jsonParser = fhirContext.newJsonParser()
        return npmPackage.list("examples")
            .map { npmPackage.load("examples", it) }
            .map(jsonParser::parseResource)
            .filterIsInstance(MessageDefinition::class.java)
    }

    fun <T : Resource> getResourcesOfOtherType(npmPackage: NpmPackage, resourceType: T): List<T> {
        val jsonParser = fhirContext.newJsonParser()
        val examplesList = npmPackage.list("examples")
            .map { npmPackage.load("examples", it) }
            .map(jsonParser::parseResource)
            .filterIsInstance(resourceType.javaClass)
        val mainList = getResourcesOfType(npmPackage,resourceType);
        return mainList.plus(examplesList)
    }

    fun <T : Resource> getResourcesOfType(npmPackage: NpmPackage, resourceType: T): List<T> {
        val jsonParser = fhirContext.newJsonParser()
        return npmPackage.listResources(resourceType.fhirType())
            .map(npmPackage::loadResource)
            .map(jsonParser::parseResource)
            .filterIsInstance(resourceType.javaClass)
    }
}
